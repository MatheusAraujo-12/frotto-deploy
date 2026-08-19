package com.localuz.service.dto;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;

/**
 * Result of EntitlementService#getSnapshot. Not persisted; not returned directly by any
 * endpoint - BillingMeDTO maps it to the response shape and is what leaves the server.
 */
public class EntitlementSnapshot {

    private final Subscription subscription;
    private final Plan currentPlan;
    private final Plan requiredPlan;
    private final long activeVehicleCount;
    private final Integer vehicleLimit;
    private final boolean canAddVehicle;
    private final boolean needsUpgrade;

    public EntitlementSnapshot(
        Subscription subscription,
        Plan currentPlan,
        Plan requiredPlan,
        long activeVehicleCount,
        Integer vehicleLimit,
        boolean canAddVehicle,
        boolean needsUpgrade
    ) {
        this.subscription = subscription;
        this.currentPlan = currentPlan;
        this.requiredPlan = requiredPlan;
        this.activeVehicleCount = activeVehicleCount;
        this.vehicleLimit = vehicleLimit;
        this.canAddVehicle = canAddVehicle;
        this.needsUpgrade = needsUpgrade;
    }

    /** Null when the user has no ACTIVE/PAST_DUE subscription (i.e. is on FREE). */
    public Subscription getSubscription() {
        return subscription;
    }

    public Plan getCurrentPlan() {
        return currentPlan;
    }

    public Plan getRequiredPlan() {
        return requiredPlan;
    }

    public long getActiveVehicleCount() {
        return activeVehicleCount;
    }

    public Integer getVehicleLimit() {
        return vehicleLimit;
    }

    public boolean isCanAddVehicle() {
        return canAddVehicle;
    }

    public boolean isNeedsUpgrade() {
        return needsUpgrade;
    }
}
