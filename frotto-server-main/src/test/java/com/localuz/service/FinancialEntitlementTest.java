package com.localuz.service;

import static com.localuz.service.FinancialCoverageFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real coverage -> SubscriptionService -> all EntitlementService read paths; only persistence is mocked. */
class FinancialEntitlementTest {
    private final FinancialCoverageFixture f = new FinancialCoverageFixture();
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final CarRepository cars = mock(CarRepository.class);
    private final PricingService pricing = mock(PricingService.class);
    private final SubscriptionService service = new SubscriptionService(subscriptions, plans, f.service, f.clock);
    private final EntitlementService entitlement = new EntitlementService(service, cars, pricing);
    private final User user = new User();
    private final Plan free = plan(PlanCode.FREE, 2);
    private final List<Subscription> candidates = new ArrayList<>();

    FinancialEntitlementTest() {
        user.setId(42L);
        f.subscription.setUser(user);
        f.subscription.setPlan(plan(PlanCode.BRONZE, 10));
        candidates.add(f.subscription);
        when(subscriptions.findByUserIdOrderByStartDateDesc(42L)).thenReturn(candidates);
        when(plans.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));
        when(cars.countByUserIdAndActiveTrue(42L)).thenReturn(2L);
        when(pricing.resolvePlanForVehicleCount(2)).thenReturn(free);
    }

    private static Plan plan(PlanCode code, int limit) {
        Plan plan = new Plan(); plan.setCode(code); plan.setMaxVehicles(limit); return plan;
    }

    private Subscription add(SubscriptionSource source, PlanCode code) {
        Subscription subscription = new Subscription();
        subscription.setSource(source);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(OCT);
        subscription.setPlan(plan(code, 20));
        candidates.add(subscription);
        return subscription;
    }

    @ParameterizedTest @ValueSource(strings = {"REFUNDED", "CHARGEDBACK"})
    void reversalLeavesAdminAndGrandfatheredSourcesUntouched(String status) {
        var invoice = f.invoice(NOV, DEC, true);
        f.payments.get(0).setStatus(PaymentAttemptStatus.valueOf(status));
        invoice.setStatus(BillingInvoiceStatus.valueOf(status));
        Subscription grandfathered = add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.SILVER);
        Subscription admin = add(SubscriptionSource.ADMIN_GRANT, PlanCode.GOLD);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(admin.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(grandfathered.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptions, never()).save(any());
    }

    @Test void validAdminGrantWinsWithoutFinancialQueries() {
        f.invoice(NOV, DEC, true);
        add(SubscriptionSource.ADMIN_GRANT, PlanCode.GOLD).setGrantExpiresAt(DEC);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.GOLD);
        verifyNoInteractions(f.invoices, f.attempts);
    }

    @Test void expiredAdminGrantFallsBackToValidProvider() {
        f.invoice(NOV, DEC, true);
        add(SubscriptionSource.ADMIN_GRANT, PlanCode.GOLD).setGrantExpiresAt(f.clock.instant());
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test void providerWithoutCoverageFallsBackToGrandfathered() {
        // PAST_DUE, not ACTIVE: 5G.10's authoritative-status bypass only applies to ACTIVE/CANCELED/
        // PAUSED, so a zero-invoice PAST_DUE row genuinely has no coverage here, same as before.
        f.subscription.setStatus(SubscriptionStatus.PAST_DUE);
        add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test void graceProviderWinsOverGrandfathered() {
        f.invoice(OCT, NOV, true);
        f.invoice(NOV, DEC, false);
        add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test void noFirstPaymentMeansFreeInEveryEntitlementReadPath() {
        f.invoice(NOV, DEC, false);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
        assertThat(entitlement.getSnapshot(user).getCurrentPlan()).isSameAs(free);
        assertThat(entitlement.getVehicleLimit(user)).isEqualTo(2);
        assertThat(entitlement.canAddVehicle(user)).isFalse();
        assertThat(entitlement.needsUpgrade(user)).isTrue();
    }

    @Test void currentPaidCompetencyIgnoresStaleAggregatedPeriodEnd() {
        f.invoice(NOV, DEC, true);
        f.subscription.setCurrentPeriodEnd(OCT);
        f.subscription.setStatus(SubscriptionStatus.PAST_DUE);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(entitlement.getSnapshot(user).getCurrentPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(entitlement.canAddVehicle(user)).isTrue();
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(f.subscription.getCurrentPeriodEnd()).isEqualTo(OCT);
        verify(subscriptions, never()).save(any());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void canceledProviderIsStillConsideredForRemainingPaidCoverage(boolean withinPeriod) {
        f.invoice(NOV, DEC, true);
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(NOV.plusSeconds(1));
        add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER);
        Clock clock = Clock.fixed(withinPeriod ? DEC.minusMillis(1) : DEC, ZoneOffset.UTC);
        SubscriptionService atBoundary = new SubscriptionService(subscriptions, plans, f.service, clock);
        assertThat(atBoundary.getEffectivePlan(user).getCode()).isEqualTo(withinPeriod ? PlanCode.BRONZE : PlanCode.SILVER);
    }

    @Test void expiredGraceFallsBackAtBoundaryWithoutWriting() {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        SubscriptionService atBoundary = new SubscriptionService(subscriptions, plans, f.service,
            Clock.fixed(renewal.getGracePeriodEnd(), ZoneOffset.UTC));
        assertThat(atBoundary.getEffectivePlan(user)).isSameAs(free);
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptions, never()).save(any());
    }

    @Test void revokedAndExpiredNonProviderRowsCannotGrantAccess() {
        // The default provider row must not itself grant access here, or this stops isolating the
        // non-provider rows this test is actually about - see 5G.10's authoritative-ACTIVE rule.
        f.subscription.setStatus(SubscriptionStatus.PAST_DUE);
        add(SubscriptionSource.ADMIN_GRANT, PlanCode.GOLD).setStatus(SubscriptionStatus.CANCELED);
        add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER).setStatus(SubscriptionStatus.EXPIRED);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    // --- 5G.10: authoritative ACTIVE grants the contracted plan without requiring a BillingInvoice
    // to exist yet (regression: staging subscriptions authorized by Mercado Pago, zero invoices
    // ingested, were being reported as FREE). Distinguish carefully from
    // noFirstPaymentMeansFreeInEveryEntitlementReadPath above, which has an actual (unpaid) invoice
    // in history - that keeps failing closed, because an invoice existing at all means
    // Reason.NO_INVOICE never applies and the pre-existing "no approved payment yet" rule still
    // governs unchanged. --------------------------------------------------------------------

    /** Caso A. */
    @Test void activeAuthorizedProviderWithZeroInvoicesGrantsContractedPlanImmediately() {
        f.subscription.setCurrentPeriodEnd(DEC);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(entitlement.getSnapshot(user).getCurrentPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        verify(subscriptions, never()).save(any());
    }

    /** Caso B - already worked before; kept explicit so a future change can't silently regress it. */
    @Test void activeProviderWithAnApprovedInvoiceRemainsGrantedAsBefore() {
        f.invoice(NOV, DEC, true);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
    }

    /** Caso C. */
    @Test void canceledProviderWithZeroInvoicesKeepsAccessUntilCurrentPeriodEnd() {
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(f.clock.instant());
        f.subscription.setCurrentPeriodEnd(DEC);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
    }

    /** Caso D. */
    @Test void canceledProviderWithZeroInvoicesAndPastPeriodEndFallsBackToFree() {
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(OCT);
        f.subscription.setCurrentPeriodEnd(OCT);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /** Item 4: missing currentPeriodEnd on a cancelled, evidence-less contract stays fail-closed - never infinite access. */
    @Test void canceledProviderWithZeroInvoicesAndNoCurrentPeriodEndFailsClosed() {
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(f.clock.instant());
        f.subscription.setCurrentPeriodEnd(null);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /** Caso E. */
    @Test void expiredProviderWithZeroInvoicesNeverGrantsAccess() {
        f.subscription.setStatus(SubscriptionStatus.EXPIRED);
        f.subscription.setCurrentPeriodEnd(DEC);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /** Caso F: PAST_DUE with zero invoices never invents grace - grace still requires a contiguous prior paid competency, unchanged by 5G.10. */
    @Test void pastDueProviderWithZeroInvoicesNeverGetsGrace() {
        f.subscription.setStatus(SubscriptionStatus.PAST_DUE);
        f.subscription.setCurrentPeriodEnd(DEC);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /**
     * Item 6 (PAUSED): never auto-reactivated (status stays PAUSED, never rewritten to ACTIVE) -
     * but if a period the user already contracted for is still running (Mercado Pago's own
     * next_payment_date, captured as currentPeriodEnd while the preapproval was last authorized),
     * pausing future charges mid-period does not retroactively revoke access already paid for.
     */
    @Test void pausedProviderWithZeroInvoicesKeepsAccessUntilCurrentPeriodEndWithoutReactivating() {
        f.subscription.setStatus(SubscriptionStatus.PAUSED);
        f.subscription.setCurrentPeriodEnd(DEC);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        verify(subscriptions, never()).save(any());
    }

    @Test void pausedProviderWithZeroInvoicesAndPastPeriodEndFallsBackToFree() {
        f.subscription.setStatus(SubscriptionStatus.PAUSED);
        f.subscription.setCurrentPeriodEnd(OCT);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    @Test void pausedProviderWithZeroInvoicesAndNoCurrentPeriodEndFailsClosed() {
        f.subscription.setStatus(SubscriptionStatus.PAUSED);
        f.subscription.setCurrentPeriodEnd(null);
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /**
     * "Não enfraquecer a origem autoritativa" (item 2): the ACTIVE/NO_INVOICE bypass must never
     * paper over an actual reversal. Once ANY invoice exists for the current competency, its
     * outcome is authoritative again - a refund/chargeback on file still revokes entitlement
     * exactly as before 5G.10, even though the contract's own status field remains ACTIVE.
     */
    @ParameterizedTest @ValueSource(strings = {"REFUNDED", "CHARGEDBACK"})
    void activeProviderWithAReversedCurrentInvoiceStillDeniesEntitlement(String status) {
        var invoice = f.invoice(NOV, DEC, true);
        f.payments.get(0).setStatus(PaymentAttemptStatus.valueOf(status));
        invoice.setStatus(BillingInvoiceStatus.valueOf(status));
        assertThat(entitlement.getCurrentPlan(user)).isSameAs(free);
    }

    /** Caso G (re-confirmed against the exact zero-invoice regression scenario, not just the already-covered generic case). */
    @Test void adminGrantOutranksAZeroInvoiceActiveProviderContract() {
        f.subscription.setCurrentPeriodEnd(DEC);
        add(SubscriptionSource.ADMIN_GRANT, PlanCode.GOLD).setGrantExpiresAt(DEC);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.GOLD);
    }

    /** Caso H (re-confirmed): GRANDFATHERED only becomes effective once the zero-invoice ACTIVE provider contract itself is absent/ineligible. */
    @Test void grandfatheredDoesNotOutrankAZeroInvoiceActiveProviderContract() {
        f.subscription.setCurrentPeriodEnd(DEC);
        add(SubscriptionSource.GRANDFATHERED, PlanCode.SILVER);
        assertThat(entitlement.getCurrentPlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
    }
}
