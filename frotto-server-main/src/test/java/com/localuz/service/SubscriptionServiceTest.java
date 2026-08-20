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

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private SubscriptionService subscriptionService;
    private User user;

    @BeforeEach
    void setUp() {
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        planRepository = Mockito.mock(PlanRepository.class);
        subscriptionService = new SubscriptionService(subscriptionRepository, planRepository);

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
    void returnsSubscriptionPlanWhenAnActiveOrPastDueSubscriptionExists() {
        Plan bronze = plan(2L, PlanCode.BRONZE);
        Subscription subscription = subscription(bronze, SubscriptionSource.PAYMENT_PROVIDER, null);

        when(
            subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(
                eq(42L),
                eq(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
            )
        )
            .thenReturn(List.of(subscription));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void fallsBackToFreePlanWhenNoCurrentSubscriptionExists() {
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(anyLong(), Mockito.anyList())).thenReturn(List.of());

        Plan free = plan(1L, PlanCode.FREE);
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void getCurrentSubscriptionIgnoresCanceledAndExpiredSubscriptions() {
        // Repository query itself filters by status; this test documents/locks in that the
        // service asks only for ACTIVE/PAST_DUE, never CANCELED/EXPIRED, as "current".
        when(
            subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(
                eq(42L),
                eq(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
            )
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
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList())).thenReturn(List.of(grant));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(grant);
    }

    @Test
    void temporaryAdminGrantIsCurrentBeforeItExpires() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList())).thenReturn(List.of(grant));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(grant);
    }

    @Test
    void expiredAdminGrantIsSkippedWithoutBeingMutated() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(3600));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
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
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(expiredGrant, olderPaymentProvider));

        Optional<Subscription> current = subscriptionService.getCurrentSubscription(user);

        assertThat(current).contains(olderPaymentProvider);
        assertThat(current.get().getSource()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
    }

    @Test
    void expiredAdminGrantWithNothingElseFallsBackToFree() {
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(expiredGrant));
        Plan free = plan(1L, PlanCode.FREE);
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void grandfatheredSourceIsPreservedAsCurrent() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
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
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(renewed, original));

        assertThat(subscriptionService.getCurrentSubscription(user)).contains(renewed);
    }

    @Test
    void scenarioA_activeAdminGrantOutranksActivePaymentProvider() {
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Plan frotta = plan(6L, PlanCode.FROTTA);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        Subscription adminGrant = subscription(frotta, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(2_592_000));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(paymentProvider, adminGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.FROTTA);
    }

    @Test
    void scenarioB_expiredAdminGrantFallsBackToTheUnderlyingPaymentProviderSubscription() {
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Plan frotta = plan(6L, PlanCode.FROTTA);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        Subscription expiredGrant = subscription(frotta, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(paymentProvider, expiredGrant));

        Plan effective = subscriptionService.getEffectivePlan(user);

        assertThat(effective.getCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(paymentProvider.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); // never touched
    }

    @Test
    void scenarioC_revokedAdminGrantFallsBackToTheUnderlyingPaymentProviderSubscription() {
        // A revoked grant has status=CANCELED, so it's excluded by the repository query itself
        // (findByUserIdAndStatusInOrderByStartDateDesc only ever returns ACTIVE/PAST_DUE) -
        // this documents that the revoked grant never even reaches the priority selection, and
        // the PAYMENT_PROVIDER row underneath was never touched by the revoke.
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Subscription paymentProvider = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(paymentProvider));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.PLATINUM);
    }

    @Test
    void scenarioD_activeAdminGrantOutranksActiveGrandfathered() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        Subscription adminGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().plusSeconds(2_592_000));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(grandfathered, adminGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.GOLD);
    }

    @Test
    void scenarioE_expiredAdminGrantFallsBackToTheUnderlyingGrandfatheredSubscription() {
        Plan silver = plan(3L, PlanCode.SILVER);
        Plan gold = plan(4L, PlanCode.GOLD);
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED, null);
        Subscription expiredGrant = subscription(gold, SubscriptionSource.ADMIN_GRANT, Instant.now().minusSeconds(60));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList()))
            .thenReturn(List.of(grandfathered, expiredGrant));

        assertThat(subscriptionService.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void aSubscriptionPastItsBillingPeriodEndIsNotCurrentEvenIfStillMarkedActive() {
        // Forward-looking guard for the future gateway flow: if currentPeriodEnd is set and
        // already passed, the row is excluded dynamically, the same way an expired grant is -
        // no scheduler needs to flip its status first.
        Plan platinum = plan(5L, PlanCode.PLATINUM);
        Subscription lapsed = subscription(platinum, SubscriptionSource.PAYMENT_PROVIDER, null);
        lapsed.setCurrentPeriodEnd(Instant.now().minusSeconds(3600));
        when(subscriptionRepository.findByUserIdAndStatusInOrderByStartDateDesc(eq(42L), Mockito.anyList())).thenReturn(List.of(lapsed));

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }
}
