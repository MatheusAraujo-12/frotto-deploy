package com.localuz.service;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.MercadoPagoWebhookEvent;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.MercadoPagoWebhookEventRepository;
import com.localuz.repository.SubscriptionRepository;
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
    /** RETRY: a transient condition - the delivery stays RETRYABLE and must be answered so Mercado Pago sends it again. */
    public enum Result { PROCESSED, DUPLICATE, IGNORED, RETRY }
    /** What one processing attempt concluded: the persisted status plus a safe, enum-derived result code. */
    private record Handling(MercadoPagoWebhookProcessingStatus status, String result) {
        static Handling processed(String result) { return new Handling(MercadoPagoWebhookProcessingStatus.PROCESSED, result); }
        static Handling ignored(String result) { return new Handling(MercadoPagoWebhookProcessingStatus.IGNORED_FINAL, result); }
        static Handling retryable(String result) { return new Handling(MercadoPagoWebhookProcessingStatus.RETRYABLE, result); }
    }
    private static final String PREAPPROVAL="subscription_preapproval";
    private static final String AUTHORIZED_PAYMENT="subscription_authorized_payment";
    private final MercadoPagoClient client; private final BillingCheckoutRepository checkouts;
    private final SubscriptionRepository subscriptions; private final MercadoPagoWebhookEventRepository events;
    private final MercadoPagoFinancialIngestion financialIngestion;
    private final SubscriptionPlanUpgradeService planUpgrades;
    /** Pre-5G.12.1 wiring kept for existing callers/tests that exercise only recurring billing: no prorated-upgrade routing. */
    public MercadoPagoWebhookProcessor(MercadoPagoClient client,BillingCheckoutRepository checkouts,SubscriptionRepository subscriptions,MercadoPagoWebhookEventRepository events,MercadoPagoFinancialIngestion financialIngestion){this(client,checkouts,subscriptions,events,financialIngestion,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public MercadoPagoWebhookProcessor(MercadoPagoClient client,BillingCheckoutRepository checkouts,SubscriptionRepository subscriptions,MercadoPagoWebhookEventRepository events,MercadoPagoFinancialIngestion financialIngestion,SubscriptionPlanUpgradeService planUpgrades){this.client=client;this.checkouts=checkouts;this.subscriptions=subscriptions;this.events=events;this.financialIngestion=financialIngestion;this.planUpgrades=planUpgrades;}

    /**
     * E2: the delivery row is locked (PESSIMISTIC_WRITE) for the whole attempt, so concurrent deliveries
     * of the same event are serialized and the later one sees the earlier one's committed status.
     * PROCESSED / IGNORED_FINAL are acknowledged without any new effect. RECEIVED / RETRYABLE are
     * processed again on the SAME row. A provider/persistence exception rolls this transaction back;
     * MercadoPagoWebhookDeliveryService then records the attempt as RETRYABLE in its own transaction.
     */
    @Transactional
    public Result process(String requestId,String type,String resourceId){
        Instant now=Instant.now();
        MercadoPagoWebhookEvent event=events.findForProcessing(requestId,type,resourceId).orElse(null);
        if(event==null){
            event=MercadoPagoWebhookEvent.received(requestId,type,resourceId,now);
            events.saveAndFlush(event);
        } else if(event.getProcessingStatus()!=null && event.getProcessingStatus().isTerminal()){
            LOG.info("Mercado Pago webhook event duplicate eventId={} requestId={} eventType={} resourceId={} processingStatus={} processingResult={}",
                event.getId(),requestId,type,resourceId,event.getProcessingStatus(),event.getProcessingResult());
            return Result.DUPLICATE;
        }
        LOG.info("Mercado Pago webhook event started eventId={} requestId={} eventType={} resourceId={} attempt={}",
            event.getId(),requestId,type,resourceId,event.getProcessingAttempts()+1);
        Handling handling=handle(requestId,type,resourceId);
        event.recordAttempt(handling.status(),handling.result(),now);
        events.saveAndFlush(event);
        boolean willRetry=handling.status()==MercadoPagoWebhookProcessingStatus.RETRYABLE;
        LOG.info("Mercado Pago webhook event outcome eventId={} requestId={} eventType={} resourceId={} processingStatus={} processingResult={} attempt={} willRetry={}",
            event.getId(),requestId,type,resourceId,handling.status(),handling.result(),event.getProcessingAttempts(),willRetry);
        switch(handling.status()){
            case PROCESSED: return Result.PROCESSED;
            case IGNORED_FINAL: return Result.IGNORED;
            default: return Result.RETRY;
        }
    }

    private Handling handle(String requestId,String type,String resourceId){
        // 5G.12.1: a one-off prorated-upgrade payment is recognised by its own external_reference
        // (re-read with an authoritative GET) and must never become a recurring BillingInvoice.
        if ("payment".equals(type) && planUpgrades != null && planUpgrades.handlePaymentNotification(resourceId)) {
            return Handling.processed("plan_upgrade_payment");
        }
        if (AUTHORIZED_PAYMENT.equals(type) || "payment".equals(type)) {
            MercadoPagoFinancialIngestion.IngestionResult ingestion = financialIngestion.ingestForWebhook(type, resourceId);
            if (ingestion.ingested()) return Handling.processed("ingested");
            MercadoPagoFinancialIngestion.IgnoreReason reason = ingestion.ignoredReason();
            return reason.isRetryable() ? Handling.retryable(reason.code()) : Handling.ignored(reason.code());
        }
        MercadoPagoPreapproval preapproval;
        try {
            if(PREAPPROVAL.equals(type)){preapproval=client.getPreapproval(resourceId);}
            else {
                LOG.info("Mercado Pago webhook event ignored requestId={} eventType={} resourceId={} reason=unknown_event_type",requestId,type,resourceId);
                return Handling.ignored("unknown_event_type");
            }
        } catch (RuntimeException providerFailure) {
            LOG.warn("Mercado Pago webhook provider GET failed requestId={} eventType={} resourceId={}",requestId,type,resourceId);
            throw providerFailure;
        }
        if(preapproval.getId()==null){
            // An incomplete authoritative snapshot: the same preapproval can be read completely later.
            LOG.info("Mercado Pago webhook event ignored requestId={} eventType={} resourceId={} reason=invalid_provider_response",requestId,type,resourceId);
            return Handling.retryable("invalid_provider_response");
        }
        Optional<BillingCheckout> byProvider=checkouts.findByProviderSubscriptionId(preapproval.getId());
        Optional<BillingCheckout> byReference=checkouts.findByExternalReference(preapproval.getExternalReference());
        if(byProvider.isPresent() && byReference.isPresent() && !byProvider.get().getId().equals(byReference.get().getId())){
            LOG.warn("Mercado Pago webhook event ignored requestId={} eventType={} resourceId={} reason=ownership_mismatch",requestId,type,resourceId);
            return Handling.ignored("ownership_mismatch");
        }
        BillingCheckout checkout=byProvider.orElseGet(()->byReference.orElse(null));
        if(checkout==null || !checkout.getExternalReference().equals(preapproval.getExternalReference()) || (checkout.getProviderSubscriptionId()!=null && !checkout.getProviderSubscriptionId().equals(preapproval.getId()))){
            LOG.warn("Mercado Pago webhook event ignored requestId={} eventType={} resourceId={} reason=no_matching_checkout",requestId,type,resourceId);
            return Handling.ignored("no_matching_checkout");
        }
        checkout.setProviderSubscriptionId(preapproval.getId()); checkout.setProviderStatus(preapproval.getStatus());
        reconcile(checkout,preapproval,PREAPPROVAL.equals(type));
        LOG.info("Mercado Pago webhook event processed requestId={} eventType={} resourceId={} providerStatus={}",requestId,type,resourceId,preapproval.getStatus());
        return Handling.processed("preapproval_reconciled");
    }

    private void reconcile(BillingCheckout checkout,MercadoPagoPreapproval provider,boolean activateExisting){
        String status=provider.getStatus().toLowerCase(java.util.Locale.ROOT);
        if("authorized".equals(status)){
            Optional<Subscription> existing=subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId());
            if(existing.filter(subscription->subscription.getStatus()==SubscriptionStatus.CANCELED).isPresent()){
                checkout.setStatus(BillingCheckoutStatus.CANCELED);checkouts.save(checkout);return;
            }
            // Etapa 5F.1: cancelAtPeriodEnd=true means a Frotto-initiated cancellation is pending
            // or already confirmed by the provider (see SubscriptionCancellationSteps). The
            // subscription's status stays ACTIVE/PAST_DUE for the rest of the paid period by
            // design, so the CANCELED-status guard above does not catch this case - a stale or
            // out-of-order "authorized" observation (an old webhook retry, or a 5E.4 reconciliation
            // snapshot taken before the cancel) must not silently erase that intent/confirmation.
            // The checkout attempt record itself is harmless to update either way.
            if(existing.filter(subscription->Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())).isPresent()){
                checkout.setStatus(BillingCheckoutStatus.AUTHORIZED);checkouts.save(checkout);return;
            }
            checkout.setStatus(BillingCheckoutStatus.AUTHORIZED);
            Subscription subscription=existing.orElseGet(Subscription::new);
            subscription.setUser(checkout.getUser());subscription.setPlan(checkout.getPlan());subscription.setBillingCycle(checkout.getBillingCycle());
            if(existing.isEmpty()||activateExisting)subscription.setStatus(SubscriptionStatus.ACTIVE);subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
            subscription.setExternalProvider("MERCADO_PAGO");subscription.setExternalSubscriptionId(provider.getId());
            subscription.setContractedPrice(checkout.getQuotedPrice());subscription.setContractedVehicleCount(checkout.getVehicleCount());
            subscription.setCancelAtPeriodEnd(false);subscription.setCanceledAt(null);
            if(subscription.getStartDate()==null)subscription.setStartDate(provider.getDateCreated()!=null?provider.getDateCreated():Instant.now());
            subscription.setCurrentPeriodEnd(provider.getNextPaymentDate()); subscriptions.save(subscription);
        } else if(SubscriptionCancellationSteps.isTerminalCancelled(status)){
            checkout.setStatus(BillingCheckoutStatus.CANCELED);
            subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId()).ifPresent(subscription->{
                // A confirmed cancellation must preserve an existing paid period regardless
                // of where it was requested. Do not reactivate a paused/closed subscription.
                boolean deferToPeriodEnd = (subscription.getStatus() == SubscriptionStatus.ACTIVE
                    || subscription.getStatus() == SubscriptionStatus.PAST_DUE)
                    && subscription.getCurrentPeriodEnd() != null
                    && subscription.getCurrentPeriodEnd().isAfter(Instant.now());
                if (deferToPeriodEnd) {
                    subscription.setCancelAtPeriodEnd(true);
                }
                if(subscription.getCanceledAt()==null){
                    subscription.setCanceledAt(provider.getLastModified()!=null?provider.getLastModified():Instant.now());
                }
                if(!deferToPeriodEnd){
                    subscription.setStatus(SubscriptionStatus.CANCELED);
                }
                subscriptions.save(subscription);
            });
        } else if("paused".equals(status)) {
            checkout.setStatus(BillingCheckoutStatus.PROVIDER_PENDING);
            subscriptions.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO",provider.getId()).ifPresent(subscription->{subscription.setStatus(SubscriptionStatus.PAUSED);subscriptions.save(subscription);});
        } else if("pending".equals(status)) {
            if(checkout.getStatus()!=BillingCheckoutStatus.AUTHORIZED)checkout.setStatus(BillingCheckoutStatus.PROVIDER_PENDING);
        }
        checkouts.save(checkout);
    }

}
