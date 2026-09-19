package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.web.rest.errors.BillingRecurringSubscriptionExistsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises docs/billing-recurring-contract-5g1.md invariant 16 / section 20 directly against
 * RecurringSubscriptionGuardService, independent of SubscriptionFinancialCoverageService's own
 * internals (mocked here to a controlled covered()/not-covered() verdict - that service has its
 * own exhaustive test suite).
 */
class RecurringSubscriptionGuardServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final SubscriptionFinancialCoverageService financialCoverage = mock(SubscriptionFinancialCoverageService.class);
    private final RecurringSubscriptionGuardService guard = new RecurringSubscriptionGuardService(subscriptions, financialCoverage, CLOCK);

    private Subscription subscription(SubscriptionSource source, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setSource(source);
        subscription.setStatus(status);
        return subscription;
    }

    private void stubProviderSubscriptions(Subscription... rows) {
        when(subscriptions.findByUserIdAndSource(7L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(rows));
    }

    private void stubCoverage(Subscription subscription, boolean covered) {
        when(financialCoverage.evaluate(subscription, NOW)).thenReturn(
            new FinancialCoverageEvaluation(covered, FinancialCoverageEvaluation.CommercialState.CANCELED, null, null, null, null,
                FinancialCoverageEvaluation.Reason.RENEWAL_STOPPED));
    }

    @BeforeEach
    void defaultNoRows() {
        when(subscriptions.findByUserIdAndSource(7L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
    }

    @Test
    void noProviderRowsNeverBlocks() {
        guard.assertNoBlockingRemoteRecurrence(7L);
    }

    @Test
    void activePaidBlocks() {
        Subscription active = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        stubProviderSubscriptions(active);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void activeWithoutFinancialEvidenceStillBlocks() {
        // status=ACTIVE alone (checkout just authorized, no BillingInvoice/PaymentAttempt yet) is
        // exactly the case financial coverage would say "not covered" for - the guard must not
        // consult financial coverage at all for this status.
        Subscription activeUnproven = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        stubProviderSubscriptions(activeUnproven);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void pastDueBlocks() {
        Subscription pastDue = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.PAST_DUE);
        stubProviderSubscriptions(pastDue);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void pausedBlocks() {
        Subscription paused = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.PAUSED);
        stubProviderSubscriptions(paused);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void cancellationRequestedButNotConfirmedBlocks() {
        // markIntent() sets cancelAtPeriodEnd=true and leaves status ACTIVE/PAST_DUE until the
        // provider confirms - see SubscriptionCancellationSteps.
        Subscription pendingCancellation = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        pendingCancellation.setCancelAtPeriodEnd(true);
        stubProviderSubscriptions(pendingCancellation);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void confirmedCancellationWithResidualPaidCoverageBlocks_deferredStatus() {
        // MercadoPagoWebhookProcessor#reconcile keeps status ACTIVE/PAST_DUE (deferToPeriodEnd)
        // when a paid period is still running at confirmation time.
        Subscription deferredCancellation = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        deferredCancellation.setCancelAtPeriodEnd(true);
        deferredCancellation.setCanceledAt(NOW.minusSeconds(60));
        stubProviderSubscriptions(deferredCancellation);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void confirmedCancellationWithResidualPaidCoverageBlocks_terminalStatus() {
        Subscription canceledWithResidualCoverage = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.CANCELED);
        canceledWithResidualCoverage.setCanceledAt(NOW.minusSeconds(60));
        stubProviderSubscriptions(canceledWithResidualCoverage);
        stubCoverage(canceledWithResidualCoverage, true);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void canceledWithoutResidualCoverageAllowsNewContract() {
        Subscription closed = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.CANCELED);
        closed.setCanceledAt(NOW.minusSeconds(60));
        stubProviderSubscriptions(closed);
        stubCoverage(closed, false);
        guard.assertNoBlockingRemoteRecurrence(7L);
    }

    @Test
    void expiredWithoutResidualCoverageAllowsNewContract() {
        Subscription expired = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.EXPIRED);
        stubProviderSubscriptions(expired);
        stubCoverage(expired, false);
        guard.assertNoBlockingRemoteRecurrence(7L);
    }

    @Test
    void expiredWithResidualCoverageStillBlocks() {
        // EXPIRED is not emitted by any current flow, but the guard must not special-case it as an
        // automatic pass - the same "still covered right now" question applies as for CANCELED.
        Subscription expired = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.EXPIRED);
        stubProviderSubscriptions(expired);
        stubCoverage(expired, true);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    /**
     * A user can legitimately accumulate more than one PAYMENT_PROVIDER row over time (a closed-out
     * contract plus a later one). The guard must inspect EVERY row, never just the most recently
     * started one - otherwise a naive "latest subscription" read could miss an older row that can
     * still charge. Both orderings are exercised so an implementation that only checked list()
     * ordering (e.g. first/last element) cannot pass by accident.
     */
    @Test
    void multipleHistoricalRowsBlockIfAnyOneIsStillChargeable_blockingRowFirst() {
        Subscription stillChargeable = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        Subscription longClosed = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.CANCELED);
        longClosed.setId(2L);
        longClosed.setCanceledAt(NOW.minusSeconds(600));
        stubCoverage(longClosed, false);
        stubProviderSubscriptions(stillChargeable, longClosed);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void multipleHistoricalRowsBlockIfAnyOneIsStillChargeable_blockingRowLast() {
        Subscription longClosed = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.CANCELED);
        longClosed.setCanceledAt(NOW.minusSeconds(600));
        stubCoverage(longClosed, false);
        Subscription stillChargeable = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.ACTIVE);
        stillChargeable.setId(2L);
        stubProviderSubscriptions(longClosed, stillChargeable);
        assertThatThrownBy(() -> guard.assertNoBlockingRemoteRecurrence(7L)).isInstanceOf(BillingRecurringSubscriptionExistsException.class);
    }

    @Test
    void multipleHistoricalRowsAllClosedWithoutCoverageAllowsNewContract() {
        Subscription oldest = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.CANCELED);
        oldest.setCanceledAt(NOW.minusSeconds(600));
        stubCoverage(oldest, false);
        Subscription newer = subscription(SubscriptionSource.PAYMENT_PROVIDER, SubscriptionStatus.EXPIRED);
        newer.setId(2L);
        stubCoverage(newer, false);
        stubProviderSubscriptions(oldest, newer);
        guard.assertNoBlockingRemoteRecurrence(7L);
    }

    @Test
    void adminGrantNeverBlocks() {
        // Only PAYMENT_PROVIDER rows are ever fetched/considered - ADMIN_GRANT never appears here.
        stubProviderSubscriptions();
        guard.assertNoBlockingRemoteRecurrence(7L);
        org.mockito.Mockito.verify(subscriptions).findByUserIdAndSource(7L, SubscriptionSource.PAYMENT_PROVIDER);
        org.mockito.Mockito.verifyNoInteractions(financialCoverage);
    }

    @Test
    void grandfatheredNeverBlocks() {
        stubProviderSubscriptions();
        guard.assertNoBlockingRemoteRecurrence(7L);
    }
}
