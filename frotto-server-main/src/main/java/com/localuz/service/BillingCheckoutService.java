package com.localuz.service;

import com.localuz.config.MercadoPagoProperties;
import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Plan;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.service.dto.PricingResult;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a server-owned price snapshot; it never creates an active Subscription. */
@Service
public class BillingCheckoutService {
    private final MercadoPagoProperties properties; private final PricingService pricingService;
    private final PlanRepository planRepository; private final BillingCheckoutRepository repository; private final MercadoPagoClient client; private final CarRepository cars; private final Clock clock;
    @Autowired
    public BillingCheckoutService(MercadoPagoProperties p,PricingService pricing,PlanRepository plans,BillingCheckoutRepository repo,MercadoPagoClient client,CarRepository cars){this(p,pricing,plans,repo,client,cars,Clock.systemUTC());}
    BillingCheckoutService(MercadoPagoProperties p,PricingService pricing,PlanRepository plans,BillingCheckoutRepository repo,MercadoPagoClient client,CarRepository cars,Clock clock){this.properties=p;this.pricingService=pricing;this.planRepository=plans;this.repository=repo;this.client=client;this.cars=cars;this.clock=clock;}
    @Transactional
    public BillingCheckout createIntent(User authenticatedUser, PlanCode requestedPlan, int vehicleCount) {
        if (!properties.isEnabled()) throw new IllegalStateException("Mercado Pago gateway is disabled");
        if (!properties.hasAccessToken()) throw new IllegalStateException("Mercado Pago access token is not configured");
        if (authenticatedUser == null || authenticatedUser.getId() == null) throw new IllegalArgumentException("Authenticated user is required");
        PlanCode requiredPlan = pricingService.resolvePlanForVehicleCount(vehicleCount).getCode();
        Plan selected = planRepository.findByCode(requestedPlan).filter(p -> Boolean.TRUE.equals(p.getActive())).orElseThrow(() -> new IllegalArgumentException("Plan is not active"));
        Plan required = planRepository.findByCode(requiredPlan).orElseThrow(() -> new IllegalStateException("Required plan not found"));
        if (selected.getMinVehicles() < required.getMinVehicles()) throw new IllegalArgumentException("Requested plan is below required plan");
        PricingResult quote = pricingService.calculatePriceForPlan(requestedPlan, vehicleCount);
        Plan plan = selected;
        Instant now=clock.instant(); BillingCheckout intent=new BillingCheckout(); intent.setUser(authenticatedUser); intent.setPlan(plan);
        intent.setVehicleCount(vehicleCount); intent.setQuotedPrice(quote.getMonthlyPrice()); intent.setBillingCycle(BillingCycle.MONTHLY);
        intent.setExternalReference(UUID.randomUUID().toString()); intent.setIdempotencyKey(UUID.randomUUID().toString()); intent.setProvider("MERCADO_PAGO"); intent.setStatus(BillingCheckoutStatus.CREATED);
        intent.setCreatedAt(now); intent.setExpiresAt(now.plus(30, ChronoUnit.MINUTES)); return repository.save(intent);
    }

    @Transactional(noRollbackFor = MercadoPagoException.class)
    public BillingCheckout createCheckout(User user, PlanCode requestedPlan) {
        String email = user == null ? null : user.getEmail();
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Authenticated user must have a valid email");
        int vehicleCount = Math.toIntExact(cars.countByUserIdAndActiveTrue(user.getId()));
        BillingCheckout checkout = createIntent(user, requestedPlan, vehicleCount);
        MercadoPagoPreapprovalRequest request = new MercadoPagoPreapprovalRequest(
            checkout.getExternalReference(), email, "Frotto - plano " + requestedPlan,
            checkout.getQuotedPrice(), "BRL", properties.getBackUrl());
        try {
            MercadoPagoPreapproval preapproval = client.createPreapproval(request, checkout.getIdempotencyKey());
            if (!checkout.getExternalReference().equals(preapproval.getExternalReference())) throw new MercadoPagoException("Mercado Pago returned an inconsistent reference", true);
            checkout.setProviderSubscriptionId(preapproval.getId()); checkout.setProviderStatus(preapproval.getStatus());
            checkout.setInitPoint(preapproval.getInitPoint()); checkout.setStatus(BillingCheckoutStatus.PROVIDER_PENDING);
            return repository.save(checkout);
        } catch (MercadoPagoException exception) {
            checkout.setStatus(exception.isAmbiguous() ? BillingCheckoutStatus.PROVIDER_UNKNOWN : BillingCheckoutStatus.FAILED);
            repository.save(checkout); throw exception;
        }
    }
}
