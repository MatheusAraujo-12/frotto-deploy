package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingPlanChangeAlreadyPendingException;
import com.localuz.web.rest.errors.BillingPlanChangeAmbiguousSubscriptionException;
import com.localuz.web.rest.errors.BillingPlanChangeNoOpException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * matters the same way a cancellation's does: markPendingDowngrade commits BEFORE the provider PUT,
 * so a racing reconciliation/webhook read always sees either "nothing pending yet" or a fully
 * durable pending change, never a half-written one.
 */
@Service
public class SubscriptionPlanChangeSteps {

    private static final String ENTITY_NAME = "subscriptionPlanChange";
    /** 5G.12 section 10: stricter than cancellation's CANCELLABLE_STATUSES - PAST_DUE/PAUSED never allow a plan change, only ACTIVE. */
    static final List<SubscriptionStatus> ELIGIBLE_STATUSES = List.of(SubscriptionStatus.ACTIVE);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final Clock clock;

    @Autowired
    public SubscriptionPlanChangeSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
        RecurringSubscriptionGuardService recurringSubscriptionGuard, SubscriptionFinancialCoverageService financialCoverage) {
        this(userRepository, subscriptionRepository, recurringSubscriptionGuard, financialCoverage, Clock.systemUTC());
    }

    SubscriptionPlanChangeSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
        RecurringSubscriptionGuardService recurringSubscriptionGuard, SubscriptionFinancialCoverageService financialCoverage, Clock clock) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.recurringSubscriptionGuard = recurringSubscriptionGuard;
        this.financialCoverage = financialCoverage;
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
     * Read-only validation ends here; markPlanChangeIntent (below) does the actual write and is
     * what a caller must use before ever touching the provider - see its javadoc for why an
     * upgrade cannot simply validate-then-release-the-lock-then-write-later.
     */
    @Transactional
    Subscription lockAndValidateForChange(Long userId) {
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
        if (subscription.getPendingPlan() != null) {
            throw new BillingPlanChangeAlreadyPendingException();
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

    /**
     * Validates AND commits the pending-change intent in the SAME short transaction, still holding
     * the user's PESSIMISTIC_WRITE lock throughout (both lockAndValidateForChange and this write
     * run inside this one @Transactional method, so the lock is never released in between). This
     * closes a real race an earlier version of this class had: if validation and the pendingPlan
     * write were two separate transactions, two concurrent UPGRADE requests could both pass
     * validation (an upgrade has no state to check "already requested" against, unlike
     * cancelAtPeriodEnd) before either one wrote anything - both would then call Mercado Pago,
     * producing two PUTs for one logical request. Writing pendingPlan here - for BOTH an upgrade
     * and a downgrade - gives every plan change the exact same "commit intent before the provider
     * call" guarantee SubscriptionCancellationSteps already has via cancelAtPeriodEnd=true: the
     * loser of the lock, once unblocked, immediately sees pendingPlan already set and refuses
     * (BillingPlanChangeAlreadyPendingException) instead of racing a second PUT.
     *
     * effectiveAt is the ONLY thing that differs by direction: "now" for an upgrade (finalizeUpgrade
     * promotes it unconditionally, no proration), or currentPeriodEnd for a downgrade (effectuateIfDue
     * promotes it only once a renewal on/after that date is authoritatively confirmed).
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
        boolean upgrade = targetPlan.getMinVehicles() > currentPlan.getMinVehicles();
        Instant effectiveAt;
        if (upgrade) {
            effectiveAt = clock.instant();
        } else {
            effectiveAt = subscription.getCurrentPeriodEnd();
            if (effectiveAt == null) {
                throw new BadRequestAlertException(
                    "Subscription is missing its current period end; refusing to schedule a downgrade without a guaranteed boundary",
                    ENTITY_NAME, "missingperiodend");
            }
        }
        subscription.setPendingPlan(targetPlan);
        subscription.setPendingContractedPrice(price);
        subscription.setPendingContractedVehicleCount(vehicleCount);
        subscription.setPlanChangeEffectiveAt(effectiveAt);
        subscription.setPlanChangeRequestedAt(clock.instant());
        return subscriptionRepository.save(subscription);
    }

    /**
     * Promotes the pending intent to the live plan immediately (no proration, no waiting for a
     * renewal) - only ever called for an UPGRADE, right after the provider confirms the new
     * amount. A downgrade's pending intent is instead promoted later by effectuateIfDue, once a
     * real renewal on/after planChangeEffectiveAt is confirmed.
     */
    @Transactional
    public Subscription finalizeUpgrade(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalStateException("Subscription disappeared during plan change: " + subscriptionId));
        Plan pendingPlan = subscription.getPendingPlan();
        if (pendingPlan == null) {
            return subscription; // Already finalized by a previous call - idempotent no-op.
        }
        subscription.setPlan(pendingPlan);
        subscription.setContractedPrice(subscription.getPendingContractedPrice());
        subscription.setContractedVehicleCount(subscription.getPendingContractedVehicleCount());
        subscription.setPendingPlan(null);
        subscription.setPendingContractedPrice(null);
        subscription.setPendingContractedVehicleCount(null);
        subscription.setPlanChangeEffectiveAt(null);
        subscription.setPlanChangeRequestedAt(null);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Only called after a definite provider rejection of the amount-change PUT, or after the
     * confirming GET proves the OLD value is still in effect - in both cases we know for a fact
     * the new amount never took effect, so undoing the local pending intent is safe. An
     * INDETERMINATE confirming GET (network/5xx) is NEVER rolled back here - see
     * SubscriptionPlanChangeService, same fail-closed-by-leaving-state-as-is philosophy as
     * SubscriptionCancellationSteps#resolveAfterUnconfirmedResponse.
     */
    @Transactional
    public void rollbackPendingChange(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(subscription -> {
            subscription.setPendingPlan(null);
            subscription.setPendingContractedPrice(null);
            subscription.setPendingContractedVehicleCount(null);
            subscription.setPlanChangeEffectiveAt(null);
            subscription.setPlanChangeRequestedAt(null);
            subscriptionRepository.save(subscription);
        });
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
        subscription.setPendingPlan(null);
        subscription.setPendingContractedPrice(null);
        subscription.setPendingContractedVehicleCount(null);
        subscription.setPlanChangeEffectiveAt(null);
        subscription.setPlanChangeRequestedAt(null);
        return subscriptionRepository.save(subscription);
    }
}
