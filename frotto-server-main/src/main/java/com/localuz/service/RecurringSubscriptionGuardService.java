package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.web.rest.errors.BillingRecurringSubscriptionExistsException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Enforces docs/billing-recurring-contract-5g1.md invariant 16 / section 20: no second remote
 * recurrence may be created for a user while an earlier PAYMENT_PROVIDER contract can still
 * charge, or is otherwise an unresolved live remote contract. This is deliberately a different
 * question from SubscriptionFinancialCoverageService's "does this grant access right now" - a
 * PAYMENT_PROVIDER Subscription can be ACTIVE with zero financial evidence (checkout just
 * authorized, no invoice/payment ingested yet) and still be a real remote contract capable of
 * charging the user, which must block a second checkout even though it grants no entitlement yet.
 *
 * Only ever reads PAYMENT_PROVIDER rows: ADMIN_GRANT and GRANDFATHERED are never a remote
 * recurrence able to charge Mercado Pago, so they can never be confused with one here.
 *
 * The caller (BillingCheckoutService#createCheckout) is responsible for invoking this AFTER its
 * own pessimistic user-row lock and BEFORE any POST to Mercado Pago - this class does no locking
 * of its own, it only evaluates already-committed state.
 */
@Service
public class RecurringSubscriptionGuardService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final Clock clock;

    @Autowired
    public RecurringSubscriptionGuardService(SubscriptionRepository subscriptions, SubscriptionFinancialCoverageService financialCoverage) {
        this(subscriptions, financialCoverage, Clock.systemUTC());
    }

    RecurringSubscriptionGuardService(SubscriptionRepository subscriptions, SubscriptionFinancialCoverageService financialCoverage, Clock clock) {
        this.subscriptions = subscriptions;
        this.financialCoverage = financialCoverage;
        this.clock = clock;
    }

    /** @throws BillingRecurringSubscriptionExistsException if a blocking remote contract exists. */
    public void assertNoBlockingRemoteRecurrence(Long userId) {
        boolean blocked = subscriptions
            .findByUserIdAndSource(userId, SubscriptionSource.PAYMENT_PROVIDER)
            .stream()
            .anyMatch(this::isStillChargeable);
        if (blocked) {
            throw new BillingRecurringSubscriptionExistsException();
        }
    }

    /**
     * True when this single PAYMENT_PROVIDER row can still charge or is an unresolved live remote
     * contract - the exact same predicate {@link #assertNoBlockingRemoteRecurrence} applies to
     * every row for a user. Exposed so BillingPaymentStateService can identify which of a user's
     * (possibly several historical) PAYMENT_PROVIDER rows is the real, currently-live contract
     * instead of re-deriving that question from "most recently started row" - a second, divergent
     * definition of "which contract is the real one" is exactly what would let a stale historical
     * row's cancel eligibility leak into the UI, or a live row be missed because a NEWER but
     * already-closed row happens to sort first.
     */
    public boolean isStillChargeable(Subscription subscription) {
        return blocks(subscription, clock.instant());
    }

    private boolean blocks(Subscription subscription, Instant now) {
        SubscriptionStatus status = subscription.getStatus();
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE || status == SubscriptionStatus.PAUSED) {
            // ACTIVE (with or without financial evidence yet), PAST_DUE, and PAUSED (assumed
            // resumable - the local model has no separate "cannot be resumed" signal, and the
            // fail-closed default is to treat every PAUSED preapproval as reactivatable) can all
            // still charge. This also covers both cancellation sub-states that keep this status:
            // requested-but-unconfirmed (cancelAtPeriodEnd=true, canceledAt=null) and confirmed-
            // but-deferred-to-period-end (canceledAt set, status kept ACTIVE/PAST_DUE until the
            // paid period ends - see MercadoPagoWebhookProcessor#reconcile).
            return true;
        }
        // CANCELED (and the currently-unused EXPIRED value): the contract itself is done, but a
        // confirmed cancellation can still leave residual paid coverage in effect (5G.1 section
        // 20's additional protection). Reuse the authoritative financial-coverage verdict rather
        // than re-deriving "residual coverage" from currentPeriodEnd here - a second, divergent
        // definition of coverage is exactly what this task must not introduce.
        return financialCoverage.evaluate(subscription, now).covered();
    }
}
