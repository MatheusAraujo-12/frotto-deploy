package com.localuz.service;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.BillingPaymentStateDTO;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BillingPaymentStateService {
    private final SubscriptionRepository subscriptions; private final BillingCheckoutRepository checkouts;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final RecurringSubscriptionGuardService recurringSubscriptionGuard;
    public BillingPaymentStateService(SubscriptionRepository subscriptions, BillingCheckoutRepository checkouts,
        SubscriptionFinancialCoverageService financialCoverage, RecurringSubscriptionGuardService recurringSubscriptionGuard) {
        this.subscriptions=subscriptions;this.checkouts=checkouts;this.financialCoverage=financialCoverage;
        this.recurringSubscriptionGuard=recurringSubscriptionGuard;
    }
    /**
     * The raw PAYMENT_PROVIDER Subscription.status is exposed here for every terminal/pending state
     * (5G.5 reconciliation and UI messaging both need it), but per the 5G rule status=ACTIVE alone is
     * never financial proof - financiallyCovered carries the actual SubscriptionFinancialCoverageService
     * verdict so the frontend can tell "checkout/preapproval confirmed" apart from "payment financially
     * confirmed" instead of inferring it from status.
     *
     * 5G.9 section 3: a user can accumulate more than one PAYMENT_PROVIDER row over time. Which one
     * is "the" contract to report/offer for cancellation must NOT be decided by "most recently
     * started" alone - that would let a stale historical row's cancel eligibility leak into the UI
     * if it happened to sort last, or hide a still-live older row behind a newer but already-closed
     * one. Reuses RecurringSubscriptionGuardService#isStillChargeable (the exact same predicate that
     * blocks a second checkout) to pick the real live contract first; only when NO row is currently
     * chargeable does this fall back to the most recently started row, purely for historical display
     * (status/financiallyCovered of a closed contract) - canCancel is false in that case regardless.
     */
    public BillingPaymentStateDTO getState(User user){
        List<Subscription> providerRows = subscriptions.findByUserIdAndSource(user.getId(), SubscriptionSource.PAYMENT_PROVIDER);
        Subscription paid = providerRows.stream()
            .filter(recurringSubscriptionGuard::isStillChargeable)
            .findFirst()
            .orElseGet(() -> mostRecentlyStarted(providerRows));
        boolean covered = paid != null && financialCoverage.evaluate(paid).covered();
        // Deliberately NOT gated on `covered`: a remote contract the user can cancel is a
        // different question from one that currently grants paid access (5G.9 section B).
        boolean canCancel = paid != null && SubscriptionCancellationSteps.CANCELLABLE_STATUSES.contains(paid.getStatus());
        BillingCheckout checkout=checkouts.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        return new BillingPaymentStateDTO(paid,covered,canCancel,checkout);
    }

    private static Subscription mostRecentlyStarted(List<Subscription> rows) {
        return rows.stream().max(Comparator.comparing(Subscription::getStartDate, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
    }
}
