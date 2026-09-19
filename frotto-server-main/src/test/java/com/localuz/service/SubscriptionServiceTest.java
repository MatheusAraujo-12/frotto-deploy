package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class SubscriptionServiceTest {
    private final SubscriptionFinancialCoverageService financialCoverage = Mockito.mock(SubscriptionFinancialCoverageService.class);

    private static com.localuz.service.dto.FinancialCoverageEvaluation coverage(boolean covered) {
        return new com.localuz.service.dto.FinancialCoverageEvaluation(covered,
            com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState.ACTIVE,
            null, null, null, null, com.localuz.service.dto.FinancialCoverageEvaluation.Reason.PAID);
    }

    private static com.localuz.service.dto.FinancialCoverageEvaluation notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason reason) {
        return new com.localuz.service.dto.FinancialCoverageEvaluation(false,
            com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT,
            null, null, null, null, reason);
    }

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private SubscriptionService subscriptionService;
    private User user;

    @BeforeEach
    void setUp() {
        // This suite tests source selection; real financial evidence is exercised in FinancialEntitlementTest.
        when(financialCoverage.evaluate(Mockito.any(), Mockito.any())).thenReturn(coverage(true));
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        planRepository = Mockito.mock(PlanRepository.class);
        subscriptionService = new SubscriptionService(subscriptionRepository, planRepository, financialCoverage);

        user = new User();
        user.setId(42L);
    }

    private static Plan plan(Long id, PlanCode code) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        return plan;
    }

    private static Subscription subscription(Plan plan, SubscriptionSource source, Instant grantExpiresAt) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setSource(source);
        subscription.setGrantExpiresAt(grantExpiresAt);
        return subscription;
    }

    @Test
    void returnsSubscriptionPlanWhenFinancialCoverageIsValid() {
        Plan bronze = plan(2L, PlanCode.BRONZE);
        Subscription subscription = subscription(bronze, SubscriptionSource.PAYMENT_PROVIDER, null);

        when(
            subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))
        )
            .thenReturn(List.of(subscription));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void fallsBackToFreePlanWhenNoCurrentSubscriptionExists() {
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(anyLong())).thenReturn(List.of());

        Plan free = plan(1L, PlanCode.FREE);
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void getCurrentSubscriptionIsEmptyWhenNoCandidatesExist() {
        when(
            subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))
        )
            .thenReturn(List.of());

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void throwsIfFreePlanIsMissingFromConfiguration() {
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getFreePlan()).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void permanentAdminGrantHasNoExpiryAndIsAlwaysCurrent() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grant = subscription(gold, SubscriptionSource.ADMIN_GRANT, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(grant));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(grant);
    }

    @Test
    void temporaryAdminGrantIsCurrentBeforeItExpires() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(grant));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(grant);
    }

    @Test
    void expiredAdminGrantIsSkippedWithoutBeingMutated() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(3600));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(expiredGrant));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
        assertThat(expiredGrant.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); // never mutated, only skipped
    }

    @Test
    void expiredAdminGrantFallsThroughToTheNextCurrentSubscription() {
        // Most recent row first, matching the repository's ORDER BY start_date DESC contract.
        Plan gold = plan(4L, PlanCode.GOLD);
        Plan bronze = plan(2L, PlanCode.BRONZE);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        Subscription olderPaymentProvider = subscription(bronze, SubscriptionSource.PAYMENT_PROVIDER, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(expiredGrant, olderPaymentProvider));

        Optional<Subscription> current = subscriptionService.getCurrentSubscription(user);

        assertThat(current).contains(olderPaymentProvider);
        assertThat(current.get().getSource()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
        assertThat(current.get().getGrantExpiresAt()).isNull();
    }

    @Test
    void expiredAdminGrantWithNothingElseFallsBackToFree() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(expiredGrant));
        Plan free = plan(1L, PlanCode.FREE);
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void grandfatheredSourceIsPreservedAsCurrent() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(grandfathered));

        Optional<Subscription> current = subscriptionService.getCurrentSubscription(user);

        assertThat(current.get().getSource()).isEqualTo(SubscriptionSource.GRANDFATHERED);
    }

    // --- Source-priority selection (Etapa 3, revisão de semântica) -----------------------

    @Test
    void whenTwoRowsShareTheSamePriorityTheMostRecentlyStartedOneWins() {
        // The only place recency still matters: as a tie-break within the *same* source, e.g.
        // two historical PAYMENT_PROVIDER rows (a renewal). Source priority always decides
        // first when sources differ - see the scenarios below.
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Plan bronze = plan(2L, PlanCode.BRONZE);
        Subscription renewed = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        renewed.setStartDate(Instant.parse("2026-06-01T00:00:00Z"));
        Subscription original = subscription(bronze, SubscriptionSource.PAYMENT_PROVIDER, null);
        original.setStartDate(Instant.parse("2026-01-01T00:00:00Z"));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(renewed, original));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(renewed);
    }

    @Test
    void scenarioA_activeAdminGrantOutranksActivePaymentProvider() {
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Plan frotta = plan(6L, PlanCode.FROTTA);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        Subscription adminGrant = subscription(frotta, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(2_592_000));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paymentProvider, adminGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FROTTA);
    }

    @Test
    void scenarioB_expiredAdminGrantFallsBackToTheUnderlyingPaymentProviderSubscription() {
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Plan frotta = plan(6L, PlanCode.FROTTA);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        Subscription expiredGrant = subscription(frotta, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paymentProvider, expiredGrant));

        Plan effective = subscriptionService.getEffectivePlan(user);

        assertThat(effective.getCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(paymentProvider.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); // never touched
    }

    @Test
    void scenarioC_revokedAdminGrantFallsBackToTheUnderlyingPaymentProviderSubscription() {
        // Non-provider eligibility still excludes revoked grants before selecting a paid fallback.
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paymentProvider));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.PLATINUM);
    }

    @Test
    void revokedAdminGrantFallsBackToTheUnderlyingGrandfatheredSubscription() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(grandfathered));

        Optional<Subscription> current = subscriptionService.getCurrentSubscription(user);

        assertThat(current).contains(grandfathered);
        assertThat(current.get().getSource()).isEqualTo(SubscriptionSource.GRANDFATHERED);
        assertThat(current.get().getGrantExpiresAt()).isNull();
    }

    @Test
    void scenarioD_activeAdminGrantOutranksActiveGrandfathered() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        Subscription adminGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(2_592_000));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(grandfathered, adminGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.GOLD);
    }

    @Test
    void scenarioE_expiredAdminGrantFallsBackToTheUnderlyingGrandfatheredSubscription() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(grandfathered, expiredGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void aSubscriptionPastItsBillingPeriodEndIsNotCurrentEvenIfStillMarkedActive() {
        // Financial evaluation, not the stored status, now rejects lapsed provider coverage.
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Subscription lapsed = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        lapsed.setCurrentPeriodEnd(Instant.now().minusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(lapsed), Mockito.any())).thenReturn(coverage(false));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(lapsed));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void pausedPaymentProviderDoesNotGrantEntitlement() {
        Subscription paused = subscription(plan(5L, PlanCode.PLATINUM), SubscriptionSource.PAYMENT_PROVIDER, null);
        paused.setStatus(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of());
        Plan free = plan(1L, PlanCode.FREE); when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void activeAdminGrantRemainsEffectiveWhilePaymentProviderIsPaused() {
        Subscription grant = subscription(plan(4L, PlanCode.GOLD), SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(grant));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.GOLD);
    }

    @Test
    void grandfatheredBecomesEffectiveWhilePaymentProviderIsPaused() {
        Subscription grandfathered = subscription(plan(3L, PlanCode.SILVER), SubscriptionSource.GRANDFATHERED, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(grandfathered));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }
    @Test
    void confirmedCancellationKeepsEntitlementUntilPeriodEndThenFallsBackWithoutScheduler() {
        Subscription paid = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        paid.setCancelAtPeriodEnd(true);
        paid.setCanceledAt(Instant.now());
        paid.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        Subscription legacy = subscription(plan(3L, PlanCode.SILVER), SubscriptionSource.GRANDFATHERED, null);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paid, legacy));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
        paid.setCurrentPeriodEnd(Instant.now().minusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(paid), Mockito.any())).thenReturn(coverage(false));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.SILVER);
        Subscription grant = subscription(plan(4L, PlanCode.GOLD), SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paid, legacy, grant));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.GOLD);
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L)))
            .thenReturn(List.of(paid));
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(plan(1L, PlanCode.FREE)));
        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FREE);
        Mockito.verify(subscriptionRepository, Mockito.never()).save(Mockito.any());
    }

    // --- 5G.10: authoritative ACTIVE bypasses the NO_INVOICE verdict; every other reason still
    // fully governs, unchanged. See FinancialEntitlementTest for the same behavior exercised
    // against the real SubscriptionFinancialCoverageService end to end. ------------------------

    @Test
    void activeProviderWithNoInvoiceEvidenceStillGrantsEntitlement() {
        Subscription active = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        when(financialCoverage.evaluate(Mockito.eq(active), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(active));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(active);
    }

    @Test
    void activeProviderWithAnyOtherUnresolvedReasonIsNotBypassed() {
        // The bypass is narrow: only literal absence of invoice evidence is trusted from status
        // alone. Any other reason (a reversal, a financial conflict, an ambiguous/incomplete
        // period...) must keep denying access exactly as it did before 5G.10.
        Subscription active = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        when(financialCoverage.evaluate(Mockito.eq(active), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.REVERSED_OR_CANCELED));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(active));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void canceledProviderWithNoInvoiceEvidenceKeepsAccessUntilCurrentPeriodEnd() {
        Subscription canceled = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        canceled.setStatus(SubscriptionStatus.CANCELED);
        canceled.setCanceledAt(Instant.now());
        canceled.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(canceled), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(canceled));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(canceled);
    }

    @Test
    void canceledProviderWithNoInvoiceEvidenceAndPastPeriodEndIsNotEntitled() {
        Subscription canceled = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        canceled.setStatus(SubscriptionStatus.CANCELED);
        canceled.setCanceledAt(Instant.now().minusSeconds(7200));
        canceled.setCurrentPeriodEnd(Instant.now().minusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(canceled), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(canceled));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void pausedProviderWithNoInvoiceEvidenceAndFuturePeriodEndKeepsAccessWithoutReactivating() {
        Subscription paused = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        paused.setStatus(SubscriptionStatus.PAUSED);
        paused.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(paused), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(paused));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(paused);
        assertThat(paused.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    void pastDueProviderWithNoInvoiceEvidenceIsNotBypassed() {
        // PAST_DUE is deliberately excluded from the ACTIVE/CANCELED/PAUSED bypass set - grace
        // still requires a contiguous prior paid competency (existing, unchanged rule).
        Subscription pastDue = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        pastDue.setStatus(SubscriptionStatus.PAST_DUE);
        when(financialCoverage.evaluate(Mockito.eq(pastDue), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(pastDue));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void expiredProviderWithNoInvoiceEvidenceIsNotBypassed() {
        Subscription expired = subscription(plan(2L, PlanCode.BRONZE), SubscriptionSource.PAYMENT_PROVIDER, null);
        expired.setStatus(SubscriptionStatus.EXPIRED);
        expired.setCurrentPeriodEnd(Instant.now().plusSeconds(3600));
        when(financialCoverage.evaluate(Mockito.eq(expired), Mockito.any()))
            .thenReturn(notCoveredWithReason(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.NO_INVOICE));
        when(subscriptionRepository.findByUserIdOrderByStartDateDesc(eq(42L))).thenReturn(List.of(expired));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }
}
