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
import com.localuz.service.dto.PlanChangePreviewDTO;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanChangeStatus;
import com.localuz.service.dto.PlanChangeType;
import com.localuz.service.dto.PlanUpgradeStatusDTO;
import com.localuz.service.dto.PricingResult;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingDowngradeUndoRejectedException;
import com.localuz.web.rest.errors.BillingPlanChangeNoOpException;
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
 * 5G.12.1:
 * - UPGRADE: prorated payment first (SubscriptionPlanUpgradeService); the plan is only granted
 *   after the payment AND the new recurring amount are authoritatively confirmed. Allowed even
 *   while a downgrade is scheduled - that downgrade survives any failed/unpaid attempt.
 * - DOWNGRADE: unchanged from 5G.12 (scheduled, effective only after a paid renewal).
 * - Undo downgrade: restores the recurring amount first, clears the schedule only after an
 *   authoritative GET confirms it.
 *
 * FREE -> paid and paid -> FREE are deliberately NOT handled as a "plan change" here: FREE -> paid
 * reuses the existing checkout flow, and paid -> FREE reuses the existing, already-homologated
 * cancellation flow (SubscriptionCancellationService) - see docs/billing-plan-change-5g12.md "FREE".
 */
@Service
public class SubscriptionPlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanChangeService.class);
    private static final String ENTITY_NAME = "subscriptionPlanChange";
    private static final String CURRENCY = "BRL";

    private enum Confirmation { CONFIRMED_NEW, CONFIRMED_OLD, INDETERMINATE }

    private record Target(Plan plan, int vehicleCount, BigDecimal price) {}

    private final SubscriptionPlanChangeSteps steps;
    private final SubscriptionPlanUpgradeService upgradeService;
    private final SubscriptionCancellationService cancellationService;
    private final MercadoPagoClient client;
    private final PricingService pricingService;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CarRepository carRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionPlanChangeService(SubscriptionPlanChangeSteps steps, SubscriptionPlanUpgradeService upgradeService,
        SubscriptionCancellationService cancellationService, MercadoPagoClient client, PricingService pricingService,
        PlanRepository planRepository, SubscriptionRepository subscriptionRepository, CarRepository carRepository) {
        this(steps, upgradeService, cancellationService, client, pricingService, planRepository, subscriptionRepository, carRepository,
            Clock.systemUTC());
    }

    SubscriptionPlanChangeService(SubscriptionPlanChangeSteps steps, SubscriptionPlanUpgradeService upgradeService,
        SubscriptionCancellationService cancellationService, MercadoPagoClient client, PricingService pricingService,
        PlanRepository planRepository, SubscriptionRepository subscriptionRepository, CarRepository carRepository, Clock clock) {
        this.steps = steps;
        this.upgradeService = upgradeService;
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
        Plan targetPlan = activePlan(targetPlanCode);
        if (targetPlanCode == PlanCode.FREE) {
            return changeToFree(user, targetPlan);
        }
        Target target = price(userId, targetPlan);

        // Settle any attempt the user already started (a return from Mercado Pago, a stale expired
        // checkout) against the provider BEFORE validating, so validation sees fresh state.
        reconcileOpenUpgradesQuietly(userId);
        Subscription current = steps.lockAndValidateForChange(userId);
        if (current.getPlan().getCode() == targetPlanCode) {
            throw new BillingPlanChangeNoOpException();
        }
        if (directionOf(current.getPlan(), targetPlan) == PlanChangeType.UPGRADE) {
            return upgradeService.requestUpgrade(userId, current, targetPlan, target.price(), target.vehicleCount());
        }

        // Downgrade: locks, validates, and commits the pending intent all in ONE short transaction
        // (see SubscriptionPlanChangeSteps#markPlanChangeIntent) - the lock is never released
        // between "is this allowed" and "record that it's happening".
        Subscription subscription = steps.markPlanChangeIntent(userId, targetPlan, target.price(), target.vehicleCount());
        PlanCode currentPlanCode = subscription.getPlan().getCode();
        String idempotencyKey = "change-plan-" + subscription.getExternalSubscriptionId() + "-" + targetPlanCode;
        applyDowngradeAmount(subscription, target.price(), idempotencyKey);
        return new PlanChangeResultDTO(currentPlanCode, targetPlanCode, PlanChangeType.DOWNGRADE, PlanChangeStatus.DOWNGRADE_SCHEDULED,
            subscription.getPlanChangeEffectiveAt(), target.price(), null, target.price(), null);
    }

    /**
     * 5G.12.1: everything the confirmation modal needs, computed server-side (never on the
     * frontend). Informational only - changePlan recomputes everything itself.
     */
    public PlanChangePreviewDTO previewChange(User user, PlanCode targetPlanCode) {
        Long userId = requireUserId(user);
        if (targetPlanCode == PlanCode.FREE) {
            throw new BadRequestAlertException("Changing to FREE uses the cancellation flow", ENTITY_NAME, "freeusescancellation");
        }
        Plan targetPlan = activePlan(targetPlanCode);
        Target target = price(userId, targetPlan);
        Subscription current = steps.lockAndValidateForChange(userId);
        if (current.getPlan().getCode() == targetPlanCode) {
            throw new BillingPlanChangeNoOpException();
        }
        if (directionOf(current.getPlan(), targetPlan) == PlanChangeType.UPGRADE) {
            SubscriptionPlanUpgradeSteps.Quote quote = upgradeService.quote(current, targetPlan, target.price(), target.vehicleCount());
            return new PlanChangePreviewDTO(current.getPlan().getCode(), targetPlanCode, PlanChangeType.UPGRADE, current.getContractedPrice(),
                target.price(), quote.charge(), quote.cycleEnd());
        }
        return new PlanChangePreviewDTO(current.getPlan().getCode(), targetPlanCode, PlanChangeType.DOWNGRADE, current.getContractedPrice(),
            target.price(), null, current.getCurrentPeriodEnd());
    }

    /**
     * 5G.12.1 "Desfazer downgrade": PUT the CURRENT plan's contracted price back on the same
     * preapproval, then clear the schedule only after an authoritative GET shows it. A definite
     * rejection, a GET still showing the downgrade price, or an indeterminate GET all keep the
     * downgrade scheduled (a later retry is safe: the same PUT is idempotent in effect). No charge.
     */
    public PlanChangeResultDTO undoDowngrade(User user) {
        Long userId = requireUserId(user);
        reconcileOpenUpgradesQuietly(userId);
        Subscription subscription = steps.lockForUndoDowngrade(userId);
        String externalId = subscription.getExternalSubscriptionId();
        BigDecimal restoredPrice = subscription.getContractedPrice();
        BigDecimal scheduledPrice = subscription.getPendingContractedPrice();
        Plan scheduledPlan = subscription.getPendingPlan();
        PlanCode currentPlanCode = subscription.getPlan().getCode();
        Instant requestedAt = subscription.getPlanChangeRequestedAt();
        String idempotencyKey = "undo-downgrade-" + subscription.getId() + "-" + (requestedAt == null ? "0" : requestedAt.toEpochMilli());
        boolean ambiguous = false;
        try {
            client.updatePreapprovalAmount(externalId, restoredPrice, CURRENCY, idempotencyKey);
        } catch (MercadoPagoException failure) {
            if (!failure.isAmbiguous()) {
                log.warn("Downgrade undo rejected by provider subscriptionId={} category={} httpStatus={}",
                    subscription.getId(), failure.getCategory(), failure.getHttpStatus());
                throw new BillingDowngradeUndoRejectedException();
            }
            ambiguous = true;
        }
        if (confirm(externalId, restoredPrice, scheduledPrice) != Confirmation.CONFIRMED_NEW) {
            log.warn("Downgrade undo not confirmed subscriptionId={} ambiguousPut={}", subscription.getId(), ambiguous);
            throw new BillingDowngradeUndoRejectedException();
        }
        steps.clearUndoneDowngrade(userId, subscription.getId(), scheduledPlan.getId(), requestedAt);
        return new PlanChangeResultDTO(currentPlanCode, scheduledPlan.getCode(), PlanChangeType.DOWNGRADE,
            PlanChangeStatus.DOWNGRADE_UNDONE, null, restoredPrice, null, restoredPrice, null);
    }

    /** 5G.12.1: the caller's latest prorated upgrade, reconciled server-side with the provider first. */
    public PlanUpgradeStatusDTO upgradeStatus(User user) {
        return upgradeService.statusForUser(requireUserId(user));
    }

    private PlanChangeResultDTO changeToFree(User user, Plan freePlan) {
        Subscription result = cancellationService.cancel(user);
        return new PlanChangeResultDTO(result.getPlan().getCode(), PlanCode.FREE, PlanChangeType.DOWNGRADE,
            PlanChangeStatus.CANCELLATION_SCHEDULED, result.getCurrentPeriodEnd(), freePlan.getMonthlyBasePrice(), null, null, null);
    }

    private Plan activePlan(PlanCode code) {
        return planRepository.findByCode(code)
            .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
            .orElseThrow(() -> new BadRequestAlertException("Target plan is not active", ENTITY_NAME, "invalidtargetplan"));
    }

    /** Price always from PricingService for the vehicle count the backend itself counted - never from the client. */
    private Target price(Long userId, Plan targetPlan) {
        int vehicleCount = Math.toIntExact(carRepository.countByUserIdAndActiveTrue(userId));
        if (targetPlan.getMaxVehicles() != null && vehicleCount > targetPlan.getMaxVehicles()) {
            throw new BadRequestAlertException("Vehicle count exceeds the target plan limit", ENTITY_NAME, "fleetincompatible");
        }
        PricingResult quote = pricingService.calculatePriceForPlan(targetPlan.getCode(), vehicleCount);
        return new Target(targetPlan, vehicleCount, quote.getMonthlyPrice());
    }

    private void reconcileOpenUpgradesQuietly(Long userId) {
        try {
            upgradeService.reconcileForUser(userId);
        } catch (MercadoPagoException failure) {
            // The open attempt (if any) stays open and keeps blocking conflicting changes - safe.
            log.warn("Plan upgrade pre-change reconciliation failed category={} httpStatus={}", failure.getCategory(), failure.getHttpStatus());
        }
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
     * By the time this runs, the subscription ALWAYS already has the downgrade committed as
     * pendingPlan (see markPlanChangeIntent) - so a definite rejection or a GET confirming the OLD
     * value rolls that pending intent back here.
     */
    private void applyDowngradeAmount(Subscription subscription, BigDecimal newPrice, String idempotencyKey) {
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
        return preapproval != null && preapproval.getTransactionAmount() != null && preapproval.getCurrencyId() != null
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
