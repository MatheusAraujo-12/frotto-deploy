package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.User;
import com.localuz.repository.CarRepository;
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
 */
@Service
@Transactional(readOnly = true)
public class EntitlementService {

    private final SubscriptionService subscriptionService;
    private final CarRepository carRepository;

    public EntitlementService(SubscriptionService subscriptionService, CarRepository carRepository) {
        this.subscriptionService = subscriptionService;
        this.carRepository = carRepository;
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
}
