package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;

/**
 * What GrandfatheringService#preview/apply computed for one user, before any write happens.
 * wouldCreateSubscription is false when the user already has a current subscription (any
 * source - grandfathering never overrides an existing one) or when their actual vehicle count
 * already fits FREE (nothing to backfill).
 */
public class GrandfatherPreviewDTO {

    private final Long userId;
    private final long activeVehicleCount;
    private final PlanCode requiredPlanCode;
    private final String requiredPlanName;
    private final boolean alreadyHasCurrentSubscription;
    private final boolean wouldCreateSubscription;

    public GrandfatherPreviewDTO(
        Long userId,
        long activeVehicleCount,
        PlanCode requiredPlanCode,
        String requiredPlanName,
        boolean alreadyHasCurrentSubscription,
        boolean wouldCreateSubscription
    ) {
        this.userId = userId;
        this.activeVehicleCount = activeVehicleCount;
        this.requiredPlanCode = requiredPlanCode;
        this.requiredPlanName = requiredPlanName;
        this.alreadyHasCurrentSubscription = alreadyHasCurrentSubscription;
        this.wouldCreateSubscription = wouldCreateSubscription;
    }

    public Long getUserId() {
        return userId;
    }

    public long getActiveVehicleCount() {
        return activeVehicleCount;
    }

    public PlanCode getRequiredPlanCode() {
        return requiredPlanCode;
    }

    public String getRequiredPlanName() {
        return requiredPlanName;
    }

    public boolean isAlreadyHasCurrentSubscription() {
        return alreadyHasCurrentSubscription;
    }

    public boolean isWouldCreateSubscription() {
        return wouldCreateSubscription;
    }
}
