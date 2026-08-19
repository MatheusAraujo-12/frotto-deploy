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
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
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

    @Test
    void returnsSubscriptionPlanWhenAnActiveOrPastDueSubscriptionExists() {
        Plan bronze = new Plan();
        bronze.setId(2L);
        bronze.setCode(PlanCode.BRONZE);

        Subscription subscription = new Subscription();
        subscription.setPlan(bronze);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        when(
            subscriptionRepository.findFirstByUserIdAndStatusInOrderByStartDateDesc(
                eq(42L),
                eq(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
            )
        )
            .thenReturn(Optional.of(subscription));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void fallsBackToFreePlanWhenNoCurrentSubscriptionExists() {
        when(subscriptionRepository.findFirstByUserIdAndStatusInOrderByStartDateDesc(anyLong(), Mockito.anyList()))
            .thenReturn(Optional.empty());

        Plan free = new Plan();
        free.setId(1L);
        free.setCode(PlanCode.FREE);
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));

        Plan effectivePlan = subscriptionService.getEffectivePlan(user);

        assertThat(effectivePlan.getCode()).isEqualTo(PlanCode.FREE);
    }

    @Test
    void getCurrentSubscriptionIgnoresCanceledAndExpiredSubscriptions() {
        // Repository query itself filters by status; this test documents/locks in that the
        // service asks only for ACTIVE/PAST_DUE, never CANCELED/EXPIRED, as "current".
        when(
            subscriptionRepository.findFirstByUserIdAndStatusInOrderByStartDateDesc(
                eq(42L),
                eq(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
            )
        )
            .thenReturn(Optional.empty());

        assertThat(subscriptionService.getCurrentSubscription(user)).isEmpty();
    }

    @Test
    void throwsIfFreePlanIsMissingFromConfiguration() {
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getFreePlan()).isInstanceOf(ResponseStatusException.class);
    }
}
