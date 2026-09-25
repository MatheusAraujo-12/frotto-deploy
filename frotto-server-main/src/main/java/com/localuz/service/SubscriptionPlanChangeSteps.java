package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionPlanUpgradeRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingPlanChangeAlreadyPendingException;
import com.localuz.web.rest.errors.BillingPlanChangeAmbiguousSubscriptionException;
import com.localuz.web.rest.errors.BillingPlanChangeNoOpException;
import com.localuz.web.rest.errors.BillingPlanUpgradeInProgressException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The individually-committed persistence steps behind SubscriptionPlanChangeService, split into
 * their own bean for the exact same reason as SubscriptionCancellationSteps: SubscriptionPlanChange
 * Service#changePlan is deliberately not @Transactional, so the HTTP call to Mercado Pago (updating
 * the recurring amount) never happens inside an open transaction - see SubscriptionCancellationSteps'
 * javadoc for the full self-invocation/AOP-proxy reasoning, which applies identically here.
 *
 * A downgrade's "prepare short local state -> external call -> confirm provider -> finalize" order
 * matters the same way a cancellation's does: markPlanChangeIntent commits BEFORE the provider PUT,
 * so a racing reconciliation/webhook read always sees either "nothing pending yet" or a fully
 * durable pending change, never a half-written one.
 *
 * 5G.12.1: subscription.pending_* now only ever holds a scheduled DOWNGRADE. A prorated upgrade has
 * its own state machine (SubscriptionPlanUpgrade / SubscriptionPlanUpgradeSteps), so a scheduled
 * downgrade no longer blocks an upgrade - only an OPEN upgrade (payment awaiting/being applied)
 * blocks further plan changes, because that is the only real concurrent financial operation.
 */
@Service
public class SubscriptionPlanChangeSteps {

    private static final String ENTITY_NAME = "subscriptionPlanChange";
    /** 5G.12 section 10: stricter than cancellation's CANCELLABLE_STATUSES - PAST_DUE/PAUSED never allow a plan change, only ACTIVE. */
    static final List<SubscriptionStatus> ELIGIBLE_STATUSES = List.of(SubscriptionStatus.ACTIVE);
    static final Set<SubscriptionPlanUpgradeStatus> OPEN_UPGRADE_STATUSES =
        EnumSet.of(SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT, SubscriptionPlanUpgradeStatus.APPLYING);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final SubscriptionPlanUpgradeRepository upgradeRepository;
    private final Clock clock;

    @Autowired
    public SubscriptionPlanChangeSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
        RecurringSubscriptionGuardService recurringSubscriptionGuard, SubscriptionFinancialCoverageService financialCoverage,
        SubscriptionPlanUpgradeRepository upgradeRepository) {
        this(userRepository, subscriptionRepository, recurringSubscriptionGuard, financialCoverage, upgradeRepository, Clock.systemUTC());
    }

    SubscriptionPlanChangeSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
        RecurringSubscriptionGuardService recurringSubscriptionGuard, SubscriptionFinancialCoverageService financialCoverage,
        SubscriptionPlanUpgradeRepository upgradeRepository, Clock clock) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.recurringSubscriptionGuard = recurringSubscriptionGuard;
        this.financialCoverage = financialCoverage;
        this.upgradeRepository = upgradeRepository;
        this.clock = clock;
    }

    /**
     * Locks the user row (same PESSIMISTIC_WRITE primitive checkout/cancellation already use) so a
     * concurrent checkout, cancellation, or another plan change for the SAME user can never both
     * proceed against stale state (section 9). Effectuates any already-due pending change first
     * (effectuateIfDue) so validation always reasons about fresh state.
     *
     * Exactly one still-chargeable PAYMENT_PROVIDER row is required (section 9): zero means "use
     * checkout instead" (a FREE user has nothing to change), more than one is a pre-5G.9 duplicate
     * that must be resolved through the existing cancellation flow rather than picked arbitrarily.
     *
     * 5G.12.1: a scheduled downgrade (pendingPlan) is no longer a blocker here - each operation
     * decides what a pending downgrade means for it (see markPlanChangeIntent / lockForUndoDowngrade
     * / SubscriptionPlanUpgradeSteps#openOrReuse).
     */
    @Transactional
    public Subscription lockAndValidateForChange(Long userId) {
        userRepository.findByIdForBillingCheckoutLock(userId).orElseThrow(() -> new IllegalArgumentException("Authenticated user is required"));

        List<Subscription> candidates = subscriptionRepository.findByUserIdAndSource(userId, SubscriptionSource.PAYMENT_PROVIDER)
            .stream()
            .filter(recurringSubscriptionGuard::isStillChargeable)
            .map(subscription -> effectuateIfDue(subscription, clock.instant()))
            .toList();
        if (candidates.isEmpty()) {
            throw new BadRequestAlertException("No active payment-provider subscription; use checkout instead", ENTITY_NAME, "nosubscriptionforchange");
        }
        if (candidates.size() > 1) {
            throw new BillingPlanChangeAmbiguousSubscriptionException();
        }
        Subscription subscription = candidates.get(0);
        if (!ELIGIBLE_STATUSES.contains(subscription.getStatus())) {
            throw new BadRequestAlertException("Subscription status does not allow a plan change", ENTITY_NAME, "statusnoteligible");
        }
        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getCanceledAt() != null) {
            throw new BadRequestAlertException("Subscription already has a cancellation scheduled", ENTITY_NAME, "cancellationscheduled");
        }
        if (StringUtils.isBlank(subscription.getExternalSubscriptionId())) {
            throw new BadRequestAlertException("Subscription is missing its provider reference", ENTITY_NAME, "missingproviderreference");
        }
        FinancialCoverageEvaluation evaluation = financialCoverage.evaluate(subscription, clock.instant());
        if (evaluation.reason() == FinancialCoverageEvaluation.Reason.FINANCIAL_CONFLICT
            || evaluation.reason() == FinancialCoverageEvaluation.Reason.REVERSED_OR_CANCELED) {
            throw new BadRequestAlertException("Subscription has a conflicting financial state", ENTITY_NAME, "financialconflict");
        }
        return subscription;
    }

    /** True while a prorated upgrade for this subscription is awaiting payment or being applied. */
    boolean hasOpenUpgrade(Long subscriptionId) {
        return !upgradeRepository.findBySubscriptionIdAndStatusIn(subscriptionId, OPEN_UPGRADE_STATUSES).isEmpty();
    }

    /**
     * Validates AND commits a scheduled DOWNGRADE in the SAME short transaction, still holding the
     * user's PESSIMISTIC_WRITE lock throughout, so two concurrent requests can never both pass
     * validation and both PUT the provider: the loser of the lock sees pendingPlan already set and
     * refuses (BillingPlanChangeAlreadyPendingException) instead of racing a second PUT.
     *
     * 5G.12.1: only downgrades come through here - an upgrade is a prorated payment flow
     * (SubscriptionPlanUpgradeSteps). A second downgrade while one is scheduled is still refused
     * (undo it first), and so is any downgrade while an upgrade payment is open.
     */
    @Transactional
    public Subscription markPlanChangeIntent(Long userId, Plan targetPlan, BigDecimal price, int vehicleCount) {
        Subscription subscription = lockAndValidateForChange(userId);
        Plan currentPlan = subscription.getPlan();
        if (currentPlan.getCode() == targetPlan.getCode()) {
            throw new BillingPlanChangeNoOpException();
        }
        // Never determined by price (a progressive plan's base price is not a reliable ranking) -
        // only by the plans' structural minVehicles ordering, per docs/billing-plan-change-5g12.md
        // section 20. Mirrors SubscriptionPlanChangeService#directionOf.
        if (targetPlan.getMinVehicles() > currentPlan.getMinVehicles()) {
            throw new IllegalStateException("Upgrades are handled by SubscriptionPlanUpgradeSteps");
        }
        if (hasOpenUpgrade(subscription.getId())) {
            throw new BillingPlanUpgradeInProgressException();
        }
        if (subscription.getPendingPlan() != null) {
            throw new BillingPlanChangeAlreadyPendingException();
        }
        Instant effectiveAt = subscription.getCurrentPeriodEnd();
        if (effectiveAt == null) {
            throw new BadRequestAlertException(
                "Subscription is missing its current period end; refusing to schedule a downgrade without a guaranteed boundary",
                ENTITY_NAME, "missingperiodend");
        }
        subscription.setPendingPlan(targetPlan);
        subscription.setPendingContractedPrice(price);
        subscription.setPendingContractedVehicleCount(vehicleCount);
        subscription.setPlanChangeEffectiveAt(effectiveAt);
        subscription.setPlanChangeRequestedAt(clock.instant());
        return subscriptionRepository.save(subscription);
    }

    /**
     * Only called after a definite provider rejection of the downgrade PUT, or after the confirming
     * GET proves the OLD value is still in effect - in both cases we know for a fact the new amount
     * never took effect, so undoing the local pending intent is safe. An INDETERMINATE confirming GET
     * (network/5xx) is NEVER rolled back here - see SubscriptionPlanChangeService, same fail-closed-
     * by-leaving-state-as-is philosophy as SubscriptionCancellationSteps#resolveAfterUnconfirmedResponse.
     */
    @Transactional
    public void rollbackPendingChange(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(subscription -> {
            clearPending(subscription);
            subscriptionRepository.save(subscription);
        });
    }

    /**
     * 5G.12.1 "Desfazer downgrade", step 1: locks and validates that there IS a scheduled downgrade
     * to undo and no upgrade payment is open. Nothing is written - the pending fields are only
     * cleared by clearUndoneDowngrade, after the provider confirmed the restored amount.
     */
    @Transactional
    public Subscription lockForUndoDowngrade(Long userId) {
        Subscription subscription = lockAndValidateForChange(userId);
        if (subscription.getPendingPlan() == null) {
            throw new BadRequestAlertException("There is no scheduled downgrade to undo", ENTITY_NAME, "nopendingdowngrade");
        }
        if (hasOpenUpgrade(subscription.getId())) {
            throw new BillingPlanUpgradeInProgressException();
        }
        return subscription;
    }

    /**
     * 5G.12.1 "Desfazer downgrade", step 2: clears the scheduled downgrade only if it is still the
     * SAME one the provider restoration was done for (same pending plan and request instant) - a
     * concurrent effectuation or another request in between leaves the row untouched.
     */
    @Transactional
    public Subscription clearUndoneDowngrade(Long userId, Long subscriptionId, Long pendingPlanId, Instant requestedAt) {
        userRepository.findByIdForBillingCheckoutLock(userId).orElseThrow(() -> new IllegalArgumentException("Authenticated user is required"));
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalStateException("Subscription disappeared during downgrade undo: " + subscriptionId));
        if (subscription.getPendingPlan() == null || !Objects.equals(subscription.getPendingPlan().getId(), pendingPlanId)
            || !Objects.equals(subscription.getPlanChangeRequestedAt(), requestedAt)) {
            return subscription;
        }
        clearPending(subscription);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Promotes a pending downgrade to the live plan once BOTH: (a) the scheduled effective date
     * has passed, and (b) the EXISTING canonical financial-coverage signal (SubscriptionFinancial
     * CoverageService - the same evaluator entitlement itself relies on, never a new payment
     * concept) shows a PAID competency starting on/after that date. This is deliberately not "the
     * clock reached the date" alone (section 5: "Não faça downgrade local apenas porque o relógio
     * atingiu a data") - a renewal that failed, is still pending, or reflects a chargeback/refund
     * (any Reason other than PAID) leaves the pending change exactly as scheduled, never applied
     * and never dropped.
     */
    @Transactional
    public Subscription effectuateIfDue(Long subscriptionId, Instant now) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalStateException("Subscription disappeared during plan change effectuation: " + subscriptionId));
        return effectuateIfDue(subscription, now);
    }

    private Subscription effectuateIfDue(Subscription subscription, Instant now) {
        if (subscription.getPendingPlan() == null || subscription.getPlanChangeEffectiveAt() == null) {
            return subscription;
        }
        if (now.isBefore(subscription.getPlanChangeEffectiveAt())) {
            return subscription;
        }
        FinancialCoverageEvaluation evaluation = financialCoverage.evaluate(subscription, now);
        boolean renewedOnOrAfterEffectiveDate = evaluation.reason() == FinancialCoverageEvaluation.Reason.PAID
            && evaluation.coverageStart() != null
            && !evaluation.coverageStart().isBefore(subscription.getPlanChangeEffectiveAt());
        if (!renewedOnOrAfterEffectiveDate) {
            return subscription;
        }
        subscription.setPlan(subscription.getPendingPlan());
        subscription.setContractedPrice(subscription.getPendingContractedPrice());
        subscription.setContractedVehicleCount(subscription.getPendingContractedVehicleCount());
        clearPending(subscription);
        return subscriptionRepository.save(subscription);
    }

    static void clearPending(Subscription subscription) {
        subscription.setPendingPlan(null);
        subscription.setPendingContractedPrice(null);
        subscription.setPendingContractedVehicleCount(null);
        subscription.setPlanChangeEffectiveAt(null);
        subscription.setPlanChangeRequestedAt(null);
    }
}
