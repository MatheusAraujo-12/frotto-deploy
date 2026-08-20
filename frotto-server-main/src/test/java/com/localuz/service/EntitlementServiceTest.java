package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.service.dto.EntitlementSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntitlementServiceTest {

    private SubscriptionService subscriptionService;
    private CarRepository carRepository;
    private PricingService pricingService;
    private EntitlementService entitlementService;
    private User user;

    @BeforeEach
    void setUp() {
        subscriptionService = Mockito.mock(SubscriptionService.class);
        carRepository = Mockito.mock(CarRepository.class);
        pricingService = Mockito.mock(PricingService.class);
        entitlementService = new EntitlementService(subscriptionService, carRepository, pricingService);

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

    @Test
    void requiredPlanComesFromPricingServiceBasedOnActualVehicleCount() {
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(6L);
        when(pricingService.resolvePlanForVehicleCount(6)).thenReturn(planWithLimit(PlanCode.SILVER, 15));

        assertThat(entitlementService.getRequiredPlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void requiredPlanCanDifferFromContractedPlanForAGrandfatheredUser() {
        // Contracted BRONZE (max 5) but already has 6 cars from before Billing existed.
        Subscription bronzeSubscription = subscriptionWith(PlanCode.BRONZE, 5, SubscriptionStatus.ACTIVE);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.of(bronzeSubscription));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(6L);
        when(pricingService.resolvePlanForVehicleCount(6)).thenReturn(planWithLimit(PlanCode.SILVER, 15));

        EntitlementSnapshot snapshot = entitlementService.getSnapshot(user);

        assertThat(snapshot.getCurrentPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(snapshot.getRequiredPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(snapshot.isCanAddVehicle()).isFalse();
        assertThat(snapshot.isNeedsUpgrade()).isTrue();
    }

    @Test
    void snapshotFallsBackToFreeWhenNoCurrentSubscriptionExists() {
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(subscriptionService.getFreePlan()).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(0L);
        when(pricingService.resolvePlanForVehicleCount(0)).thenReturn(planWithLimit(PlanCode.FREE, 2));

        EntitlementSnapshot snapshot = entitlementService.getSnapshot(user);

        assertThat(snapshot.getSubscription()).isNull();
        assertThat(snapshot.getCurrentPlan().getCode()).isEqualTo(PlanCode.FREE);
        assertThat(snapshot.getActiveVehicleCount()).isEqualTo(0L);
        assertThat(snapshot.isCanAddVehicle()).isTrue();
    }

    @Test
    void snapshotDoesNotQuerySubscriptionMoreThanOnce() {
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(subscriptionService.getFreePlan()).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(2L);
        when(pricingService.resolvePlanForVehicleCount(2)).thenReturn(planWithLimit(PlanCode.FREE, 2));

        entitlementService.getSnapshot(user);

        Mockito.verify(subscriptionService, Mockito.times(1)).getCurrentSubscription(user);
        Mockito.verify(carRepository, Mockito.times(1)).countByUserIdAndActiveTrue(7L);
    }

    @Test
    void snapshotIncludesActivePlatinumSubscription() {
        Subscription platinumSubscription = subscriptionWith(PlanCode.PLATINUM, 100, SubscriptionStatus.ACTIVE);
        platinumSubscription.setBillingCycle(BillingCycle.MONTHLY);
        platinumSubscription.setContractedPrice(new BigDecimal("129.90"));
        platinumSubscription.setCurrentPeriodStart(Instant.parse("2026-08-01T00:00:00Z"));
        platinumSubscription.setCurrentPeriodEnd(Instant.parse("2026-09-01T00:00:00Z"));
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.of(platinumSubscription));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(50L);
        when(pricingService.resolvePlanForVehicleCount(50)).thenReturn(planWithLimit(PlanCode.PLATINUM, 100));

        EntitlementSnapshot snapshot = entitlementService.getSnapshot(user);

        assertThat(snapshot.getSubscription()).isSameAs(platinumSubscription);
        assertThat(snapshot.getCurrentPlan().getCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(snapshot.isCanAddVehicle()).isTrue();
    }

    @Test
    void snapshotTreatsPastDueSubscriptionAsCurrent() {
        Subscription pastDueSubscription = subscriptionWith(PlanCode.BRONZE, 5, SubscriptionStatus.PAST_DUE);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.of(pastDueSubscription));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(4L);
        when(pricingService.resolvePlanForVehicleCount(4)).thenReturn(planWithLimit(PlanCode.BRONZE, 5));

        EntitlementSnapshot snapshot = entitlementService.getSnapshot(user);

        assertThat(snapshot.getSubscription().getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(snapshot.getCurrentPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void snapshotFallsBackToFreeWhenOnlyASubscriptionCanceledIsOnRecord() {
        // SubscriptionService#getCurrentSubscription only ever returns ACTIVE/PAST_DUE rows, so a
        // CANCELED-only history looks identical to "no subscription" from here - documents that.
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(subscriptionService.getFreePlan()).thenReturn(planWithLimit(PlanCode.FREE, 2));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(1L);
        when(pricingService.resolvePlanForVehicleCount(1)).thenReturn(planWithLimit(PlanCode.FREE, 2));

        EntitlementSnapshot snapshot = entitlementService.getSnapshot(user);

        assertThat(snapshot.getCurrentPlan().getCode()).isEqualTo(PlanCode.FREE);
        assertThat(snapshot.getSubscription()).isNull();
    }

    @Test
    void frottaGrantIsUnlimited() {
        // Etapa 3 scenario: "FROTTA ilimitado" - an ADMIN_GRANT of FROTTA has maxVehicles=null,
        // same unbounded contract as any other source of FROTTA (see nullLimitMeansUnbounded).
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.FROTTA, null));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(10_000L);

        assertThat(entitlementService.canAddVehicle(user)).isTrue();
    }

    @Test
    void goldGrantLimitsUserToThirtyVehicles() {
        // Etapa 3 scenario: "grant GOLD limitando 30".
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.GOLD, 30));

        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(29L);
        assertThat(entitlementService.canAddVehicle(user)).isTrue();

        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(30L);
        assertThat(entitlementService.canAddVehicle(user)).isFalse();
    }

    @Test
    void grandfatheredSilverCanGrowUpToFifteen() {
        // Etapa 3 scenario: "grandfathered SILVER crescendo até 15" - a legacy user with 10
        // cars, grandfathered into SILVER (max 15), can keep adding up to the 15th.
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.SILVER, 15));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(14L);

        assertThat(entitlementService.canAddVehicle(user)).isTrue();
    }

    @Test
    void grandfatheredSilverBlocksTheSixteenthVehicle() {
        // Etapa 3 scenario: "bloqueando 16º" - at 15/15, the user is still within SILVER's own
        // range (6-15), so requiredPlan for their *current* count is still SILVER (not GOLD -
        // getRequiredPlan answers "what fits today", not "what would fit after one more");
        // canAddVehicle is what actually blocks the 16th, and is what the frontend should use
        // to know an upgrade is needed before the user can grow further.
        when(subscriptionService.getEffectivePlan(user)).thenReturn(planWithLimit(PlanCode.SILVER, 15));
        when(carRepository.countByUserIdAndActiveTrue(7L)).thenReturn(15L);
        when(pricingService.resolvePlanForVehicleCount(15)).thenReturn(planWithLimit(PlanCode.SILVER, 15));

        assertThat(entitlementService.canAddVehicle(user)).isFalse();
        assertThat(entitlementService.needsUpgrade(user)).isTrue();
        assertThat(entitlementService.getRequiredPlan(user).getCode()).isEqualTo(PlanCode.SILVER);
    }

    private static Subscription subscriptionWith(PlanCode planCode, Integer maxVehicles, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setPlan(planWithLimit(planCode, maxVehicles));
        subscription.setStatus(status);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }
}
