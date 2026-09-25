package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.SubscriptionPlanUpgrade;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionPlanUpgradeRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.web.rest.errors.BillingPlanUpgradeInProgressException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5G.12.1: the individually-committed transitions of the prorated-upgrade state machine
 * (SubscriptionPlanUpgradeStatus). Never performs HTTP - SubscriptionPlanUpgradeService calls the
 * provider between these short transactions, exactly like SubscriptionPlanChangeSteps /
 * SubscriptionCancellationSteps. Every transition re-reads the attempt under a PESSIMISTIC_WRITE
 * lock, so a webhook, the user's polling, the scheduler and a double click can race freely: only
 * the first one moves the state, the others observe the result.
 */
@Service
public class SubscriptionPlanUpgradeSteps {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanUpgradeSteps.class);

    public static final String REFERENCE_PREFIX = "frotto-upgrade-";
    public static final Set<String> PENDING_PAYMENT_STATUSES = Set.of("pending", "in_process", "authorized", "in_mediation");
    public static final Set<String> REJECTED_PAYMENT_STATUSES = Set.of("rejected", "cancelled");
    /** How long the Checkout Pro preference accepts payments (expiration_date_to). */
    static final Duration CHECKOUT_WINDOW = Duration.ofMinutes(30);
    /** Extra wait after the checkout window before declaring "no payment" - absorbs provider search indexing delay. */
    static final Duration SETTLEMENT_GRACE = Duration.ofMinutes(15);
    /** A paid upgrade whose recurrence update keeps failing is escalated instead of being retried forever. */
    static final int MAX_APPLY_ATTEMPTS = 5;
    static final String CHECKOUT_UNCONFIRMED = "checkout_unconfirmed";

    /** Null-safe (immutable Set.contains(null) throws) - lastPaymentStatus is null until a payment is observed. */
    public static boolean isPendingPaymentStatus(String status) {
        return status != null && PENDING_PAYMENT_STATUSES.contains(status);
    }

    public static boolean isRejectedPaymentStatus(String status) {
        return status != null && REJECTED_PAYMENT_STATUSES.contains(status);
    }

    /** Frozen server-side quote for one attempt. */
    public record Quote(Long subscriptionId, Long fromPlanId, BigDecimal fromPrice, Plan targetPlan, BigDecimal targetPrice,
        int vehicleCount, BigDecimal charge, String currency, Instant cycleStart, Instant cycleEnd) {}

    public record Opened(SubscriptionPlanUpgrade upgrade, boolean reused) {}

    /** A payment as returned by an authoritative GET /v1/payments/{id} - never a webhook body. */
    public record ObservedPayment(String id, String status, BigDecimal amount, String currency, String externalReference, Instant approvedAt) {}

    private final UserRepository userRepository;
    private final SubscriptionPlanChangeSteps planChangeSteps;
    private final SubscriptionPlanUpgradeRepository upgrades;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    @Autowired
    public SubscriptionPlanUpgradeSteps(UserRepository userRepository, SubscriptionPlanChangeSteps planChangeSteps,
        SubscriptionPlanUpgradeRepository upgrades, SubscriptionRepository subscriptions) {
        this(userRepository, planChangeSteps, upgrades, subscriptions, Clock.systemUTC());
    }

    SubscriptionPlanUpgradeSteps(UserRepository userRepository, SubscriptionPlanChangeSteps planChangeSteps,
        SubscriptionPlanUpgradeRepository upgrades, SubscriptionRepository subscriptions, Clock clock) {
        this.userRepository = userRepository;
        this.planChangeSteps = planChangeSteps;
        this.upgrades = upgrades;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    /**
     * Under the user's PESSIMISTIC_WRITE lock (shared with checkout/cancellation/plan change):
     * re-validates the subscription, then either reuses the caller's still-open attempt for the
     * SAME target (double click, retry, reload - no second charge link) or opens a new one. Any
     * other open attempt blocks the request. A scheduled downgrade is NOT touched here: it is only
     * cleared by finalizeApplied, after the provider confirmed the upgrade.
     *
     * The quote was computed outside the lock (it needs a provider GET); if the contract changed
     * since (plan/price/subscription), the request is refused as a concurrency conflict rather than
     * charging a stale amount.
     */
    @Transactional
    public Opened openOrReuse(Long userId, Quote quote) {
        Subscription subscription = planChangeSteps.lockAndValidateForChange(userId);
        if (!Objects.equals(subscription.getId(), quote.subscriptionId())
            || !Objects.equals(subscription.getPlan().getId(), quote.fromPlanId())
            || subscription.getContractedPrice() == null || subscription.getContractedPrice().compareTo(quote.fromPrice()) != 0) {
            throw new ConcurrencyFailureException("Subscription changed while the upgrade was being quoted");
        }
        List<SubscriptionPlanUpgrade> open = upgrades.findBySubscriptionIdAndStatusIn(subscription.getId(),
            SubscriptionPlanChangeSteps.OPEN_UPGRADE_STATUSES);
        if (!open.isEmpty()) {
            SubscriptionPlanUpgrade existing = open.get(0);
            boolean sameTarget = Objects.equals(existing.getTargetPlan().getId(), quote.targetPlan().getId());
            if (open.size() == 1 && sameTarget && existing.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT
                && clock.instant().isBefore(existing.getCheckoutExpiresAt())) {
                if (existing.getCheckoutUrl() == null && !CHECKOUT_UNCONFIRMED.equals(existing.getFailureReason())) {
                    // Another request for this same attempt is creating its checkout right now:
                    // never create a second payment link for the same attempt.
                    throw new BillingPlanUpgradeInProgressException();
                }
                return new Opened(existing, true);
            }
            boolean neverShownToUser = open.size() == 1 && existing.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT
                && existing.getCheckoutUrl() == null && existing.getExternalPaymentId() == null;
            if (!neverShownToUser) {
                throw new BillingPlanUpgradeInProgressException();
            }
            // Its checkout link was never handed to the user (ambiguous creation), so nothing can be
            // paid through it: safe to supersede with the newly requested target.
            existing.setStatus(SubscriptionPlanUpgradeStatus.FAILED);
            existing.setFailureReason("superseded_before_checkout");
            upgrades.save(existing);
        }
        Instant now = clock.instant();
        SubscriptionPlanUpgrade upgrade = new SubscriptionPlanUpgrade();
        upgrade.setSubscription(subscription);
        upgrade.setFromPlan(subscription.getPlan());
        upgrade.setTargetPlan(quote.targetPlan());
        upgrade.setFromPrice(quote.fromPrice());
        upgrade.setTargetPrice(quote.targetPrice());
        upgrade.setTargetVehicleCount(quote.vehicleCount());
        upgrade.setChargeAmount(quote.charge());
        upgrade.setCurrency(quote.currency());
        upgrade.setCycleStart(quote.cycleStart());
        upgrade.setCycleEnd(quote.cycleEnd());
        upgrade.setExternalReference(REFERENCE_PREFIX + UUID.randomUUID());
        upgrade.setCheckoutExpiresAt(now.plus(CHECKOUT_WINDOW));
        upgrade.setApplyAttempts(0);
        // Nothing is due (e.g. same recurring price): skip the checkout, but still go through the
        // exact same "recurrence confirmed first, plan second" application path.
        upgrade.setStatus(quote.charge().signum() == 0 ? SubscriptionPlanUpgradeStatus.APPLYING : SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT);
        upgrade.setCreatedAt(now);
        upgrade.setUpdatedAt(now);
        return new Opened(upgrades.save(upgrade), false);
    }

    @Transactional
    public SubscriptionPlanUpgrade recordCheckout(Long upgradeId, String preferenceId, String checkoutUrl) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT) {
            upgrade.setExternalPreferenceId(preferenceId);
            upgrade.setCheckoutUrl(checkoutUrl);
            upgrade.setFailureReason(null);
            upgrade = upgrades.save(upgrade);
        }
        return upgrade;
    }

    /** The preference creation ended ambiguously (timeout/5xx): the same attempt may retry it with the same external_reference. */
    @Transactional
    public void markCheckoutUnconfirmed(Long upgradeId) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT && upgrade.getCheckoutUrl() == null) {
            upgrade.setFailureReason(CHECKOUT_UNCONFIRMED);
            upgrades.save(upgrade);
        }
    }

    /** The preference was definitively rejected: nothing can have been paid through it. */
    @Transactional
    public void markCheckoutFailed(Long upgradeId) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT && upgrade.getCheckoutUrl() == null) {
            upgrade.setStatus(SubscriptionPlanUpgradeStatus.FAILED);
            upgrade.setFailureReason("checkout_rejected");
            upgrades.save(upgrade);
        }
    }

    /**
     * Folds authoritative payment observations into the state machine.
     *
     * @param discoveryComplete true only when {@code payments} is the provider's full list for the
     *     reference (search + individual GETs). Only then may the attempt expire for lack of payment;
     *     a single webhook observation never proves the absence of other payments.
     */
    @Transactional
    public SubscriptionPlanUpgrade recordPayments(Long upgradeId, List<ObservedPayment> payments, boolean discoveryComplete) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        Instant now = clock.instant();
        List<ObservedPayment> ours = payments.stream()
            .filter(payment -> upgrade.getExternalReference().equals(payment.externalReference()))
            .toList();
        List<ObservedPayment> approved = ours.stream().filter(payment -> "approved".equals(normalized(payment.status()))).toList();
        List<ObservedPayment> exact = approved.stream()
            .filter(payment -> payment.amount() != null && payment.amount().compareTo(upgrade.getChargeAmount()) == 0
                && upgrade.getCurrency().equalsIgnoreCase(StringUtils.defaultString(payment.currency()))
                && payment.approvedAt() != null)
            .sorted(Comparator.comparing(ObservedPayment::approvedAt))
            .toList();

        switch (upgrade.getStatus()) {
            case AWAITING_PAYMENT -> {
                if (!exact.isEmpty()) {
                    ObservedPayment paid = exact.get(0);
                    upgrade.setExternalPaymentId(paid.id());
                    upgrade.setPaidAt(paid.approvedAt());
                    upgrade.setLastPaymentStatus("approved");
                    if (!paid.approvedAt().isBefore(upgrade.getCycleEnd())) {
                        // Paid for a cycle that had already ended - applying it now would grant the
                        // new plan for a cycle charged at the old price. Escalate, never guess.
                        escalate(upgrade, "paid_after_cycle_end");
                    } else {
                        upgrade.setStatus(SubscriptionPlanUpgradeStatus.APPLYING);
                    }
                    warnIfDuplicate(upgrade, exact);
                } else if (!approved.isEmpty()) {
                    escalate(upgrade, "payment_amount_mismatch");
                } else {
                    // Discovery lists newest first (sort=date_created&criteria=desc).
                    if (!ours.isEmpty()) upgrade.setLastPaymentStatus(StringUtils.left(normalized(ours.get(0).status()), 32));
                    boolean stillPending = ours.stream().anyMatch(payment -> isPendingPaymentStatus(normalized(payment.status())));
                    boolean windowClosed = !now.isBefore(upgrade.getCheckoutExpiresAt().plus(SETTLEMENT_GRACE));
                    if (discoveryComplete && windowClosed && !stillPending) {
                        upgrade.setStatus(SubscriptionPlanUpgradeStatus.EXPIRED);
                        upgrade.setFailureReason(ours.isEmpty() ? "no_payment" : "payment_not_approved");
                    }
                }
            }
            case EXPIRED, FAILED -> {
                if (!approved.isEmpty()) {
                    // Money arrived after we stopped waiting for it: never drop it silently.
                    upgrade.setExternalPaymentId(approved.get(0).id());
                    upgrade.setPaidAt(approved.get(0).approvedAt());
                    escalate(upgrade, "late_payment");
                }
            }
            default -> warnIfDuplicate(upgrade, approved);
        }
        return upgrades.save(upgrade);
    }

    /**
     * Returns the attempt only if it is APPLYING and the subscription can still receive it; otherwise
     * escalates (paid but no longer applicable) or returns empty. Checked BEFORE touching the
     * recurrence, so an ineligible subscription never gets its recurring amount raised.
     */
    @Transactional
    public Optional<SubscriptionPlanUpgrade> prepareApply(Long upgradeId) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() != SubscriptionPlanUpgradeStatus.APPLYING) {
            return Optional.empty();
        }
        String blocker = applyBlocker(upgrade, subscriptions.findById(upgrade.getSubscription().getId()).orElse(null));
        if (blocker != null) {
            escalate(upgrade, blocker);
            upgrades.save(upgrade);
            return Optional.empty();
        }
        return Optional.of(upgrade);
    }

    @Transactional
    public SubscriptionPlanUpgrade recordApplyFailure(Long upgradeId, String reason) {
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() != SubscriptionPlanUpgradeStatus.APPLYING) {
            return upgrade;
        }
        upgrade.setApplyAttempts(upgrade.getApplyAttempts() + 1);
        upgrade.setFailureReason(reason);
        if (upgrade.getApplyAttempts() >= MAX_APPLY_ATTEMPTS) {
            escalate(upgrade, "recurrence_update_failed");
        }
        return upgrades.save(upgrade);
    }

    /**
     * Only called after an authoritative GET showed the recurrence at targetPrice. Under the user
     * lock (same as every other plan operation), switches the live plan/price/vehicle count and
     * clears a scheduled downgrade - the upgrade supersedes it, and this is the first moment it is
     * safe to drop it (a failed/unpaid upgrade never reaches here, so the downgrade survives).
     */
    @Transactional
    public SubscriptionPlanUpgrade finalizeApplied(Long upgradeId) {
        SubscriptionPlanUpgrade snapshot = upgrades.findById(upgradeId)
            .orElseThrow(() -> new IllegalStateException("Plan upgrade disappeared: " + upgradeId));
        userRepository.findByIdForBillingCheckoutLock(snapshot.getSubscription().getUser().getId())
            .orElseThrow(() -> new IllegalStateException("Plan upgrade owner disappeared: " + upgradeId));
        SubscriptionPlanUpgrade upgrade = lock(upgradeId);
        if (upgrade.getStatus() != SubscriptionPlanUpgradeStatus.APPLYING) {
            return upgrade;
        }
        Subscription subscription = subscriptions.findById(upgrade.getSubscription().getId()).orElse(null);
        String blocker = applyBlocker(upgrade, subscription);
        if (blocker != null) {
            // The recurrence was already raised but the contract changed in between: an operator
            // must reconcile (refund / restore). Never grant the plan on a stale contract.
            escalate(upgrade, "state_changed_after_recurrence_update");
            return upgrades.save(upgrade);
        }
        subscription.setPlan(upgrade.getTargetPlan());
        subscription.setContractedPrice(upgrade.getTargetPrice());
        subscription.setContractedVehicleCount(upgrade.getTargetVehicleCount());
        SubscriptionPlanChangeSteps.clearPending(subscription);
        subscriptions.save(subscription);
        upgrade.setStatus(SubscriptionPlanUpgradeStatus.APPLIED);
        upgrade.setAppliedAt(clock.instant());
        upgrade.setFailureReason(null);
        return upgrades.save(upgrade);
    }

    private String applyBlocker(SubscriptionPlanUpgrade upgrade, Subscription subscription) {
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE
            || Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getCanceledAt() != null
            || StringUtils.isBlank(subscription.getExternalSubscriptionId())) {
            return "subscription_not_eligible";
        }
        if (!Objects.equals(subscription.getPlan().getId(), upgrade.getFromPlan().getId())) {
            return "plan_changed";
        }
        if (!clock.instant().isBefore(upgrade.getCycleEnd())) {
            return "cycle_ended";
        }
        return null;
    }

    private void escalate(SubscriptionPlanUpgrade upgrade, String reason) {
        upgrade.setStatus(SubscriptionPlanUpgradeStatus.REQUIRES_REVIEW);
        upgrade.setFailureReason(reason);
        // Ids only - never amounts tied to identities, tokens or provider payloads.
        log.warn("Plan upgrade requires review upgradeId={} subscriptionId={} reason={}",
            upgrade.getId(), upgrade.getSubscription().getId(), reason);
    }

    private void warnIfDuplicate(SubscriptionPlanUpgrade upgrade, List<ObservedPayment> approved) {
        if (approved.stream().anyMatch(payment -> !payment.id().equals(upgrade.getExternalPaymentId()))) {
            log.warn("Plan upgrade received more than one approved payment upgradeId={} subscriptionId={} reason=duplicate_payment_needs_refund",
                upgrade.getId(), upgrade.getSubscription().getId());
        }
    }

    private SubscriptionPlanUpgrade lock(Long upgradeId) {
        return upgrades.findByIdForUpdate(upgradeId)
            .orElseThrow(() -> new IllegalStateException("Plan upgrade disappeared: " + upgradeId));
    }

    private static String normalized(String status) {
        return status == null ? "" : status.toLowerCase(Locale.ROOT);
    }
}
