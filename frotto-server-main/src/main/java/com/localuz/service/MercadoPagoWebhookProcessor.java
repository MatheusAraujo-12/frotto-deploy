package com.localuz.service;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.MercadoPagoWebhookEvent;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.MercadoPagoWebhookEventRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MercadoPagoWebhookProcessor {
    private static final Logger LOG=LoggerFactory.getLogger(MercadoPagoWebhookProcessor.class);
    public enum Result { PROCESSED, DUPLICATE, IGNORED }
    private static final String PREAPPROVAL="subscription_preapproval";
    private static final String AUTHORIZED_PAYMENT="subscription_authorized_payment";
    private final MercadoPagoClient client; private final BillingCheckoutRepository checkouts;
    private final SubscriptionRepository subscriptions; private final MercadoPagoWebhookEventRepository events;
    public MercadoPagoWebhookProcessor(MercadoPagoClient client,BillingCheckoutRepository checkouts,SubscriptionRepository subscriptions,MercadoPagoWebhookEventRepository events){this.client=client;this.checkouts=checkouts;this.subscriptions=subscriptions;this.events=events;}

    @Transactional
    public Result process(String requestId,String type,String resourceId){
        if(events.existsByRequestIdAndEventTypeAndResourceId(requestId,type,resourceId))return Result.DUPLICATE;
        MercadoPagoPreapproval preapproval; MercadoPagoAuthorizedPayment payment=null;
        if(PREAPPROVAL.equals(type)){preapproval=client.getPreapproval(resourceId);}
        else if(AUTHORIZED_PAYMENT.equals(type)){
            payment=client.getAuthorizedPayment(resourceId);
            preapproval=client.getPreapproval(payment.getPreapprovalId());
        } else {return Result.IGNORED;}
        if(preapproval.getId()==null){saveEvent(requestId,type,resourceId);return Result.IGNORED;}
        Optional<BillingCheckout> byProvider=checkouts.findByProviderSubscriptionId(preapproval.getId());
        Optional<BillingCheckout> byReference=checkouts.findByExternalReference(preapproval.getExternalReference());
        if(byProvider.isPresent() && byReference.isPresent() && !byProvider.get().getId().equals(byReference.get().getId())){LOG.warn("Mercado Pago ownership mismatch for resource {}",resourceId);saveEvent(requestId,type,resourceId);return Result.IGNORED;}
        BillingCheckout checkout=byProvider.orElseGet(()->byReference.orElse(null));
        if(checkout==null || !checkout.getExternalReference().equals(preapproval.getExternalReference()) || (checkout.getProviderSubscriptionId()!=null && !checkout.getProviderSubscriptionId().equals(preapproval.getId()))){LOG.warn("Mercado Pago resource {} has no matching checkout",resourceId);saveEvent(requestId,type,resourceId);return Result.IGNORED;}
        checkout.setProviderSubscriptionId(preapproval.getId()); checkout.setProviderStatus(preapproval.getStatus());
        reconcile(checkout,preapproval,PREAPPROVAL.equals(type));
        if(payment!=null && "authorized".equalsIgnoreCase(preapproval.getStatus()))reconcilePayment(preapproval.getId(),payment);
        saveEvent(requestId,type,resourceId); return Result.PROCESSED;
    }

    private void saveEvent(String requestId,String type,String resourceId){Instant now=Instant.now();MercadoPagoWebhookEvent event=new MercadoPagoWebhookEvent();event.setRequestId(requestId);event.setEventType(type);event.setResourceId(resourceId);event.setReceivedAt(now);event.setProcessedAt(now);events.saveAndFlush(event);}

    private void reconcile(BillingCheckout checkout,MercadoPagoPreapproval provider,boolean activateExisting){
        String status=provider.getStatus().toLowerCase(java.util.Locale.ROOT);
        if("authorized".equals(status)){
            checkout.setStatus(BillingCheckoutStatus.AUTHORIZED);
            Optional<Subscription> existing=subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId());
            Subscription subscription=existing.orElseGet(Subscription::new);
            subscription.setUser(checkout.getUser());subscription.setPlan(checkout.getPlan());subscription.setBillingCycle(checkout.getBillingCycle());
            if(existing.isEmpty()||activateExisting)subscription.setStatus(SubscriptionStatus.ACTIVE);subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
            subscription.setExternalProvider("MERCADO_PAGO");subscription.setExternalSubscriptionId(provider.getId());
            subscription.setContractedPrice(checkout.getQuotedPrice());subscription.setContractedVehicleCount(checkout.getVehicleCount());
            subscription.setCancelAtPeriodEnd(false);subscription.setCanceledAt(null);
            if(subscription.getStartDate()==null)subscription.setStartDate(provider.getDateCreated()!=null?provider.getDateCreated():Instant.now());
            subscription.setCurrentPeriodEnd(provider.getNextPaymentDate()); subscriptions.save(subscription);
        } else if("cancelled".equals(status)||"canceled".equals(status)){
            checkout.setStatus(BillingCheckoutStatus.CANCELED);
            subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId()).ifPresent(subscription->{subscription.setStatus(SubscriptionStatus.CANCELED);subscription.setCanceledAt(provider.getLastModified()!=null?provider.getLastModified():Instant.now());subscriptions.save(subscription);});
        } else if("paused".equals(status)) {
            checkout.setStatus(BillingCheckoutStatus.PROVIDER_PENDING);
            subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId()).ifPresent(subscription->{subscription.setStatus(SubscriptionStatus.PAUSED);subscriptions.save(subscription);});
        } else if("pending".equals(status)) {
            if(checkout.getStatus()!=BillingCheckoutStatus.AUTHORIZED)checkout.setStatus(BillingCheckoutStatus.PROVIDER_PENDING);
        }
        checkouts.save(checkout);
    }

    private void reconcilePayment(String providerSubscriptionId,MercadoPagoAuthorizedPayment payment){
        String invoice=normalize(payment.getStatus()); String result=normalize(payment.getPaymentStatus());
        if(!"processed".equals(invoice))return;
        subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",providerSubscriptionId).ifPresent(subscription->{
            if("approved".equals(result)){subscription.setStatus(SubscriptionStatus.ACTIVE);subscriptions.save(subscription);}
            else if("rejected".equals(result)){subscription.setStatus(SubscriptionStatus.PAST_DUE);subscriptions.save(subscription);}
        });
    }

    private String normalize(String value){return value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT).replace(' ','_');}
}
