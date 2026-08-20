package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.GrandfatherPreviewDTO;
import com.localuz.service.dto.GrandfatherResultDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills a GRANDFATHERED Subscription for a pre-Billing user, so that turning on
 * billing.enforcement.enabled later doesn't suddenly cap their existing fleet at the FREE
 * limit. Explicitly triggered per user via AdminBillingResource (ROLE_ADMIN) - never run at
 * startup, never run in bulk automatically, never run against production by this stage's
 * work. See the Billing Etapa 3 report for the staging rollout plan.
 *
 * apply() is idempotent: a user who already has any current subscription (regardless of
 * source) is left untouched, so calling it twice - or calling it after an admin grant already
 * exists - never creates a conflicting second row.
 *
 * contractedPrice is always 0.00, for the same reason as SubscriptionAdminService's admin
 * grants: grandfathering is not a charge (see Subscription's javadoc).
 */
@Service
@Transactional
public class GrandfatheringService {

    private final CarRepository carRepository;
    private final PlanRepository planRepository;
    private final PricingService pricingService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;

    public GrandfatheringService(
        CarRepository carRepository,
        PlanRepository planRepository,
        PricingService pricingService,
        SubscriptionService subscriptionService,
        SubscriptionRepository subscriptionRepository
    ) {
        this.carRepository = carRepository;
        this.planRepository = planRepository;
        this.pricingService = pricingService;
        this.subscriptionService = subscriptionService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Dry run: computes what apply() would do, without writing anything. */
    @Transactional(readOnly = true)
    public GrandfatherPreviewDTO preview(User user) {
        return computePreview(user);
    }

    public GrandfatherResultDTO apply(User user) {
        GrandfatherPreviewDTO previewResult = computePreview(user);
        if (!previewResult.isWouldCreateSubscription()) {
            return new GrandfatherResultDTO(previewResult, false, null);
        }

        Plan requiredPlan = planRepository
            .findByCode(previewResult.getRequiredPlanCode())
            .orElseThrow(() -> new IllegalStateException("Plan disappeared between preview and apply: " + previewResult.getRequiredPlanCode()));

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(requiredPlan);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setSource(SubscriptionSource.GRANDFATHERED);
        subscription.setContractedPrice(BigDecimal.ZERO.setScale(2));
        subscription.setContractedVehicleCount(toIntVehicleCount(previewResult.getActiveVehicleCount()));
        subscriptionRepository.save(subscription);

        return new GrandfatherResultDTO(previewResult, true, subscription.getId());
    }

    private GrandfatherPreviewDTO computePreview(User user) {
        long activeVehicleCount = carRepository.countByUserIdAndActiveTrue(user.getId());
        boolean hasCurrentSubscription = subscriptionService.getCurrentSubscription(user).isPresent();
        Plan requiredPlan = pricingService.resolvePlanForVehicleCount(toIntVehicleCount(activeVehicleCount));
        boolean wouldCreate = !hasCurrentSubscription && requiredPlan.getCode() != PlanCode.FREE;

        return new GrandfatherPreviewDTO(
            user.getId(),
            activeVehicleCount,
            requiredPlan.getCode(),
            requiredPlan.getName(),
            hasCurrentSubscription,
            wouldCreate
        );
    }

    private static int toIntVehicleCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }
}
