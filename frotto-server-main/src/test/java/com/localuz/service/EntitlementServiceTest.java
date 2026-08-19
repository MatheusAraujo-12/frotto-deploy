package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.CarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntitlementServiceTest {

    private SubscriptionService subscriptionService;
    private CarRepository carRepository;
    private EntitlementService entitlementService;
    private User user;

    @BeforeEach
    void setUp() {
        subscriptionService = Mockito.mock(SubscriptionService.class);
        carRepository = Mockito.mock(CarRepository.class);
        entitlementService = new EntitlementService(subscriptionService, carRepository);

        user = new User();
        user.setId(7L);
    }

    private static Plan planWithLimit(PlanCode code, Integer maxVehicles) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setMaxVehicles(maxVehicles);
        return plan;
    }

    @Test
    void vehicleLimitComesFromTheCurrentPlan() {
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.BRONZE, 5));

        assertThat(entitlementService.getVehicleLimit(user)).isEqualTo(5);
    }

    @Test
    void nullLimitMeansUnbounded() {
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.FROTTA, null));

        assertThat(entitlementService.getVehicleLimit(user)).isNull();
        assertThat(entitlementService.canAddVehicle(user)).isTrue();
    }

    @Test
    void canAddVehicleIsTrueWhenBelowLimit() {
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(1L);

        assertThat(entitlementService.canAddVehicle(user)).isTrue();
        assertThat(entitlementService.needsUpgrade(user)).isFalse();
    }

    @Test
    void canAddVehicleIsFalseWhenAtLimit() {
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(2L);

        assertThat(entitlementService.canAddVehicle(user)).isFalse();
        assertThat(entitlementService.needsUpgrade(user)).isTrue();
    }

    @Test
    void canAddVehicleIsFalseWhenAboveLimit() {
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(3L);

        assertThat(entitlementService.canAddVehicle(user)).isFalse();
    }

    @Test
    void activeVehicleCountOnlyCountsActiveTrueCars() {
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(4L);

        assertThat(entitlementService.getActiveVehicleCount(user)).isEqualTo(4L);
        Mockito.verify(carRepository).countByUserIdAndActiveTrue(7L);
    }
}
