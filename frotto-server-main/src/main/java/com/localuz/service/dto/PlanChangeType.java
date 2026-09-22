package com.localuz.service.dto;

/**
 * 5G.12: determined structurally from Plan#getMinVehicles ordering (never from price - see
 * SubscriptionPlanChangeService#directionOf). FREE is handled by reusing the existing cancellation
 * flow rather than emitting DOWNGRADE for it (see BillingResource#changePlan).
 */
public enum PlanChangeType {
    UPGRADE,
    DOWNGRADE
}
