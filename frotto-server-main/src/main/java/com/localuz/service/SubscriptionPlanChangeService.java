package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanChangeType;
import com.localuz.service.dto.PricingResult;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingPlanChangeProviderRejectedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a plan change for an existing PAYMENT_PROVIDER subscription without ever creating a
 * second preapproval: the SAME Mercado Pago recurrence has only its auto_recurring.transaction_amount
 * updated (see MercadoPagoClient#updatePreapprovalAmount). Never @Transactional itself - same
 * reasoning as SubscriptionCancellationService: the HTTP call to Mercado Pago must never happen
 * inside an open transaction (see SubscriptionPlanChangeSteps' javadoc).
 *
 * FREE -> paid and paid -> FREE are deliberately NOT handled as a "plan change" here: FREE -> paid
 * reuses the existing checkout flow (there is nothing to change - no PAYMENT_PROVIDER contract
 * exists yet), and paid -> FREE reuses the existing, already-homologated cancellation flow
 * (SubscriptionCancellationService) - see docs/billing-plan-change-5g12.md section "FREE".
 */
@Service
public class SubscriptionPlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanChangeService.class);
    private static final String ENTITY_NAME = "subscriptionPlanChange";
    private static final String CURRENCY = "BRL";

    private enum Confirmation { CONFIRMED_NEW, CONFIRMED_OLD, INDETERMINATE }

    private final SubscriptionPlanChangeSteps steps;
    private final SubscriptionCancellationService cancellationService;
    private final MercadoPagoClient client;
    private final PricingService pricingService;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CarRepository carRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionPlanChangeService(SubscriptionPlanChangeSteps steps, SubscriptionCancellationService cancellationService,
        MercadoPagoClient client, PricingService pricingService, PlanRepository planRepository,
        SubscriptionRepository subscriptionRepository, CarRepository carRepository) {
        this(steps, cancellationService, client, pricingService, planRepository, subscriptionRepository, carRepository, Clock.systemUTC());
    }

    SubscriptionPlanChangeService(SubscriptionPlanChangeSteps steps, SubscriptionCancellationService cancellationService,
        MercadoPagoClient client, PricingService pricingService, PlanRepository planRepository,
        SubscriptionRepository subscriptionRepository, CarRepository carRepository, Clock clock) {
        this.steps = steps;
        this.cancellationService = cancellationService;
        this.client = client;
        this.pricingService = pricingService;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.carRepository = carRepository;
        this.clock = clock;
    }

    public PlanChangeResultDTO changePlan(User user, PlanCode targetPlanCode) {
        Long userId = requireUserId(user);
        Plan targetPlan = planRepository.findByCode(targetPlanCode)
            .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
            .orElseThrow(() -> new BadRequestAlertException("Target plan is not active", ENTITY_NAME, "invalidtargetplan"));

        if (targetPlanCode == PlanCode.FREE) {
            return changeToFree(user, targetPlan);
        }

        int vehicleCount = Math.toIntExact(carRepository.countByUserIdAndActiveTrue(userId));
        if (targetPlan.getMaxVehicles() != null && vehicleCount > targetPlan.getMaxVehicles()) {
            throw new BadRequestAlertException("Vehicle count exceeds the target plan limit", ENTITY_NAME, "fleetincompatible");
        }
        PricingResult quote = pricingService.calculatePriceForPlan(targetPlanCode, vehicleCount);
        BigDecimal newPrice = quote.getMonthlyPrice();

        // Locks, validates, determines direction/effectiveAt and commits the pending intent all in
        // ONE short transaction (see SubscriptionPlanChangeSteps#markPlanChangeIntent) - the lock is
        // never released between "is this allowed" and "record that it's happening", which is what
        // makes two concurrent requests for the same user safe.
        Subscription subscription = steps.markPlanChangeIntent(userId, targetPlan, newPrice, vehicleCount);
        PlanCode currentPlanCode = subscription.getPlan().getCode();
        PlanChangeType changeType = directionOf(subscription.getPlan(), targetPlan);
        String idempotencyKey = "change-plan-" + subscription.getExternalSubscriptionId() + "-" + targetPlanCode;

        if (changeType == PlanChangeType.UPGRADE) {
            applyAmountChange(subscription, newPrice, idempotencyKey);
            Subscription updated = steps.finalizeUpgrade(subscription.getId());
            return new PlanChangeResultDTO(currentPlanCode, targetPlanCode, PlanChangeType.UPGRADE, clock.instant(), updated.getContractedPrice(), false);
        }

        applyAmountChange(subscription, newPrice, idempotencyKey);
        return new PlanChangeResultDTO(currentPlanCode, targetPlanCode, PlanChangeType.DOWNGRADE, subscription.getPlanChangeEffectiveAt(), newPrice, true);
    }

    private PlanChangeResultDTO changeToFree(User user, Plan freePlan) {
        Subscription result = cancellationService.cancel(user);
        return new PlanChangeResultDTO(result.getPlan().getCode(), PlanCode.FREE, PlanChangeType.DOWNGRADE,
            result.getCurrentPeriodEnd(), freePlan.getMonthlyBasePrice(), true);
    }

    /**
     * Never determined by price (progressive plans make that unreliable) - only by the plans'
     * structural minVehicles ordering, per docs/billing-plan-change-5g12.md section 20.
     */
    private static PlanChangeType directionOf(Plan currentPlan, Plan targetPlan) {
        return targetPlan.getMinVehicles() > currentPlan.getMinVehicles() ? PlanChangeType.UPGRADE : PlanChangeType.DOWNGRADE;
    }

    /**
     * "Não confie apenas na resposta do PUT": an authoritative confirming GET always follows,
     * whether the PUT looked successful or only ambiguously failed - never on a DEFINITE (non-
     * ambiguous) PUT rejection, which is handled without ever calling the provider again.
     *
     * By the time this runs, the subscription ALWAYS already has a pendingPlan committed (see
     * markPlanChangeIntent) for both an upgrade and a downgrade - so a definite rejection or a
     * GET confirming the OLD value always rolls that pending intent back here, uniformly.
     */
    private void applyAmountChange(Subscription subscription, BigDecimal newPrice, String idempotencyKey) {
        String externalId = subscription.getExternalSubscriptionId();
        BigDecimal oldPrice = subscription.getContractedPrice();
        boolean ambiguous = false;
        try {
            client.updatePreapprovalAmount(externalId, newPrice, CURRENCY, idempotencyKey);
        } catch (MercadoPagoException failure) {
            if (!failure.isAmbiguous()) {
                steps.rollbackPendingChange(subscription.getId());
                throw new BillingPlanChangeProviderRejectedException();
            }
            ambiguous = true;
        }
        Confirmation confirmation = confirm(externalId, newPrice, oldPrice);
        if (confirmation == Confirmation.CONFIRMED_NEW) {
            return;
        }
        if (confirmation == Confirmation.CONFIRMED_OLD) {
            steps.rollbackPendingChange(subscription.getId());
            throw new BillingPlanChangeProviderRejectedException();
        }
        // INDETERMINATE: fail closed - never claim success, but never guess a rollback either
        // (same philosophy as SubscriptionCancellationSteps#resolveAfterUnconfirmedResponse).
        log.warn("Plan change amount could not be authoritatively confirmed subscriptionId={} ambiguousPut={}", subscription.getId(), ambiguous);
        throw new BillingPlanChangeProviderRejectedException();
    }

    private Confirmation confirm(String externalId, BigDecimal expectedNew, BigDecimal expectedOld) {
        MercadoPagoPreapproval current;
        try {
            current = client.getPreapproval(externalId);
        } catch (MercadoPagoException confirmFailure) {
            log.warn("Plan change confirming GET failed externalId category={} httpStatus={}",
                confirmFailure.getCategory(), confirmFailure.getHttpStatus());
            return Confirmation.INDETERMINATE;
        }
        if (matches(current, expectedNew)) return Confirmation.CONFIRMED_NEW;
        if (expectedOld != null && matches(current, expectedOld)) return Confirmation.CONFIRMED_OLD;
        return Confirmation.INDETERMINATE;
    }

    private boolean matches(MercadoPagoPreapproval preapproval, BigDecimal amount) {
        return preapproval.getTransactionAmount() != null && preapproval.getCurrencyId() != null
            && amount.compareTo(preapproval.getTransactionAmount()) == 0
            && CURRENCY.equalsIgnoreCase(preapproval.getCurrencyId());
    }

    /** Promotes any pending downgrade whose effective date has passed and now has authoritative renewal evidence - see SubscriptionPlanChangeSteps#effectuateIfDue. */
    public void effectuateDueChangesForUser(Long userId) {
        Instant now = clock.instant();
        subscriptionRepository.findByUserIdAndSource(userId, SubscriptionSource.PAYMENT_PROVIDER)
            .stream()
            .filter(subscription -> subscription.getPendingPlan() != null)
            .forEach(subscription -> steps.effectuateIfDue(subscription.getId(), now));
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user.getId();
    }
}
