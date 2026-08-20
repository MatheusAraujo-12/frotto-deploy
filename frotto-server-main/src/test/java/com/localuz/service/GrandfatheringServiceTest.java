package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.GrandfatherPreviewDTO;
import com.localuz.service.dto.GrandfatherResultDTO;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * GrandfatheringService is never invoked automatically anywhere in this codebase (no startup
 * hook, no scheduler, no other service calls it) - these tests only exercise it directly, the
 * same way an admin would via AdminBillingResource.
 */
class GrandfatheringServiceTest {

    private CarRepository carRepository;
    private PlanRepository planRepository;
    private PricingService pricingService;
    private SubscriptionService subscriptionService;
    private SubscriptionRepository subscriptionRepository;
    private GrandfatheringService grandfatheringService;
    private User user;

    @BeforeEach
    void setUp() {
        carRepository = Mockito.mock(CarRepository.class);
        planRepository = Mockito.mock(PlanRepository.class);
        pricingService = Mockito.mock(PricingService.class);
        subscriptionService = Mockito.mock(SubscriptionService.class);
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        grandfatheringService = new GrandfatheringService(
            carRepository,
            planRepository,
            pricingService,
            subscriptionService,
            subscriptionRepository
        );

        user = new User();
        user.setId(77L);

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Plan plan(PlanCode code) {
        Plan plan = new Plan();
        plan.setId(9L);
        plan.setCode(code);
        plan.setName(code.name());
        return plan;
    }

    @Test
    void previewNeverWritesAnything() {
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(10L);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(pricingService.resolvePlanForVehicleCount(10)).thenReturn(plan(PlanCode.SILVER));

        GrandfatherPreviewDTO preview = grandfatheringService.preview(user);

        assertThat(preview.getRequiredPlanCode()).isEqualTo(PlanCode.SILVER);
        assertThat(preview.isWouldCreateSubscription()).isTrue();
        Mockito.verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void applyCreatesAGrandfatheredSubscriptionMatchingActualVehicleCount() {
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(10L);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(pricingService.resolvePlanForVehicleCount(10)).thenReturn(plan(PlanCode.SILVER));
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(plan(PlanCode.SILVER)));

        GrandfatherResultDTO result = grandfatheringService.apply(user);

        assertThat(result.isSubscriptionCreated()).isTrue();
        org.mockito.ArgumentCaptor<Subscription> captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        Mockito.verify(subscriptionRepository).save(captor.capture());
        Subscription created = captor.getValue();
        assertThat(created.getUser()).isEqualTo(user);
        assertThat(created.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(created.getSource()).isEqualTo(SubscriptionSource.GRANDFATHERED);
        assertThat(created.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(created.getContractedPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getContractedVehicleCount()).isEqualTo(10);
    }

    @Test
    void applyIsIdempotentWhenAUserAlreadyHasACurrentSubscription() {
        Subscription existing = new Subscription();
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(10L);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.of(existing));
        when(pricingService.resolvePlanForVehicleCount(10)).thenReturn(plan(PlanCode.SILVER));

        GrandfatherResultDTO result = grandfatheringService.apply(user);

        assertThat(result.isSubscriptionCreated()).isFalse();
        assertThat(result.getSubscriptionId()).isNull();
        Mockito.verify(subscriptionRepository, Mockito.never()).save(any(Subscription.class));
    }

    @Test
    void applySkipsUsersWhoseActualFleetAlreadyFitsFree() {
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(1L);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(pricingService.resolvePlanForVehicleCount(1)).thenReturn(plan(PlanCode.FREE));

        GrandfatherResultDTO result = grandfatheringService.apply(user);

        assertThat(result.isSubscriptionCreated()).isFalse();
        Mockito.verify(subscriptionRepository, Mockito.never()).save(any(Subscription.class));
    }

    @Test
    void applyingTwiceInARowOnlyCreatesOneSubscription() {
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(20L);
        when(pricingService.resolvePlanForVehicleCount(20)).thenReturn(plan(PlanCode.GOLD));
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(plan(PlanCode.GOLD)));

        // First call: no current subscription yet.
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        GrandfatherResultDTO first = grandfatheringService.apply(user);
        assertThat(first.isSubscriptionCreated()).isTrue();

        // Second call: now the (just-created) subscription is current, so apply is a no-op.
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.of(new Subscription()));
        GrandfatherResultDTO second = grandfatheringService.apply(user);
        assertThat(second.isSubscriptionCreated()).isFalse();

        Mockito.verify(subscriptionRepository, Mockito.times(1)).save(any(Subscription.class));
    }

    @Test
    void tenCarLegacyUserIsGrandfatheredIntoSilverWithContractedPriceZero() {
        // Etapa 3 worked example: legacy user with 10 cars -> SILVER, not billed.
        when(carRepository.countByUserIdAndActiveTrue(77L)).thenReturn(10L);
        when(subscriptionService.getCurrentSubscription(user)).thenReturn(Optional.empty());
        when(pricingService.resolvePlanForVehicleCount(10)).thenReturn(plan(PlanCode.SILVER));
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(plan(PlanCode.SILVER)));

        GrandfatherResultDTO result = grandfatheringService.apply(user);

        assertThat(result.isSubscriptionCreated()).isTrue();
        assertThat(result.getPreview().getRequiredPlanCode()).isEqualTo(PlanCode.SILVER);
        assertThat(result.getPreview().getActiveVehicleCount()).isEqualTo(10L);
    }
}
