package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.repository.CarRepository;
import com.localuz.service.dto.EntitlementSnapshot;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side source of truth for "how many vehicles can this user have". Not wired into
 * any car-creation endpoint yet - see the Etapa 1 report for what is still needed before
 * enforcement can be turned on safely.
 *
 * Vehicle counting uses Car.active = true (the existing soft-delete flag), matching
 * CarRepository#findActiveByCurrentUser; CarAdminStatus sub-states (A_VENDA, MANUTENCAO,
 * BLOQUEADO, ...) are not additionally excluded, since there is no existing precedent for
 * treating them differently for fleet-size purposes.
 *
 * currentPlan (what the user is contracted for) and requiredPlan (what their actual vehicle
 * count would map to) are deliberately kept separate - see getSnapshot/getRequiredPlan. A
 * user can be over their contracted limit (e.g. grandfathered legacy fleets); this class never
 * silently substitutes one for the other.
 */
@Service
@Transactional(readOnly = true)
public class EntitlementService {

    private final SubscriptionService subscriptionService;
    private final CarRepository carRepository;
    private final PricingService pricingService;

    public EntitlementService(SubscriptionService subscriptionService, CarRepository carRepository, PricingService pricingService) {
        this.subscriptionService = subscriptionService;
        this.carRepository = carRepository;
        this.pricingService = pricingService;
    }

    public Plan getCurrentPlan(User user) {
        return subscriptionService.getEffectivePlan(user);
    }

    public long getActiveVehicleCount(User user) {
        return carRepository.countByUserIdAndActiveTrue(user.getId());
    }

    /** Null means unbounded (e.g. FROTTA has no max_vehicles). */
    public Integer getVehicleLimit(User user) {
        return getCurrentPlan(user).getMaxVehicles();
    }

    public boolean canAddVehicle(User user) {
        Integer limit = getVehicleLimit(user);
        if (limit == null) {
            return true;
        }
        return getActiveVehicleCount(user) < limit;
    }

    public boolean needsUpgrade(User user) {
        return !canAddVehicle(user);
    }

    /** The plan that actually fits the user's current vehicle count, independent of what they're contracted for. */
    public Plan getRequiredPlan(User user) {
        return pricingService.resolvePlanForVehicleCount(toIntVehicleCount(getActiveVehicleCount(user)));
    }

    /**
     * Everything GET /api/billing/me needs, computed with exactly one subscription lookup and
     * one vehicle count query (Plan/PlanPricingTier reads are additionally covered by Hibernate's
     * 2nd-level cache) - see the Billing Etapa 2 report for why this exists instead of composing
     * the individual getters above.
     */
    public EntitlementSnapshot getSnapshot(User user) {
        Optional<Subscription> subscription = subscriptionService.getCurrentSubscription(user);
        Plan currentPlan = subscription.map(Subscription::getPlan).orElseGet(subscriptionService::getFreePlan);
        long activeVehicleCount = getActiveVehicleCount(user);
        Integer vehicleLimit = currentPlan.getMaxVehicles();
        boolean canAdd = vehicleLimit == null || activeVehicleCount < vehicleLimit;
        Plan requiredPlan = pricingService.resolvePlanForVehicleCount(toIntVehicleCount(activeVehicleCount));

        return new EntitlementSnapshot(
            subscription.orElse(null),
            currentPlan,
            requiredPlan,
            activeVehicleCount,
            vehicleLimit,
            canAdd,
            !canAdd
        );
    }

    private static int toIntVehicleCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }
}
