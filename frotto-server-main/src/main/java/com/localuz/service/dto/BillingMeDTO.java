package com.localuz.service.dto;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response for GET /api/billing/me. Deliberately excludes anything gateway/admin-facing
 * (externalProvider, externalSubscriptionId, internal ids) - see EntitlementSnapshot for the
 * server-side data this is built from.
 *
 * requiredPlanCode/requiredPlanName reflect the plan that actually fits the user's current
 * vehicle count; they are NOT a silent substitute for planCode/planName, which stay the plan
 * the user is actually contracted for. The two differ when a user is over their contracted
 * limit (e.g. a legacy/grandfathered fleet).
 */
public class BillingMeDTO {

    private final PlanCode planCode;
    private final String planName;
    private final SubscriptionStatus subscriptionStatus;
    private final BillingCycle billingCycle;

    private final long activeVehicleCount;
    private final Integer vehicleLimit;
    private final boolean canAddVehicle;
    private final boolean needsUpgrade;

    private final PlanCode requiredPlanCode;
    private final String requiredPlanName;

    private final BigDecimal currentMonthlyPrice;

    private final Instant currentPeriodStart;
    private final Instant currentPeriodEnd;
    private final boolean cancelAtPeriodEnd;

    public BillingMeDTO(
        PlanCode planCode,
        String planName,
        SubscriptionStatus subscriptionStatus,
        BillingCycle billingCycle,
        long activeVehicleCount,
        Integer vehicleLimit,
        boolean canAddVehicle,
        boolean needsUpgrade,
        PlanCode requiredPlanCode,
        String requiredPlanName,
        BigDecimal currentMonthlyPrice,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
    ) {
        this.planCode = planCode;
        this.planName = planName;
        this.subscriptionStatus = subscriptionStatus;
        this.billingCycle = billingCycle;
        this.activeVehicleCount = activeVehicleCount;
        this.vehicleLimit = vehicleLimit;
        this.canAddVehicle = canAddVehicle;
        this.needsUpgrade = needsUpgrade;
        this.requiredPlanCode = requiredPlanCode;
        this.requiredPlanName = requiredPlanName;
        this.currentMonthlyPrice = currentMonthlyPrice;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public static BillingMeDTO from(EntitlementSnapshot snapshot) {
        Subscription subscription = snapshot.getSubscription();
        Plan currentPlan = snapshot.getCurrentPlan();
        Plan requiredPlan = snapshot.getRequiredPlan();

        BigDecimal currentMonthlyPrice = subscription != null ? subscription.getContractedPrice() : currentPlan.getMonthlyBasePrice();

        return new BillingMeDTO(
            currentPlan.getCode(),
            currentPlan.getName(),
            subscription != null ? subscription.getStatus() : null,
            subscription != null ? subscription.getBillingCycle() : null,
            snapshot.getActiveVehicleCount(),
            snapshot.getVehicleLimit(),
            snapshot.isCanAddVehicle(),
            snapshot.isNeedsUpgrade(),
            requiredPlan.getCode(),
            requiredPlan.getName(),
            currentMonthlyPrice,
            subscription != null ? subscription.getCurrentPeriodStart() : null,
            subscription != null ? subscription.getCurrentPeriodEnd() : null,
            subscription != null && Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())
        );
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
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

    public PlanCode getRequiredPlanCode() {
        return requiredPlanCode;
    }

    public String getRequiredPlanName() {
        return requiredPlanName;
    }

    public BigDecimal getCurrentMonthlyPrice() {
        return currentMonthlyPrice;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }
}
