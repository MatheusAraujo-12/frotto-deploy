package com.localuz.service.dto;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
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
    private final SubscriptionSource subscriptionSource;

    private final long activeVehicleCount;
    private final Integer vehicleLimit;
    private final boolean canAddVehicle;
    private final boolean needsUpgrade;

    private final PlanCode requiredPlanCode;
    private final String requiredPlanName;

    private final BigDecimal currentMonthlyPrice;

    private final Instant currentPeriodStart;
    private final Instant currentPeriodEnd;
    private final Instant grantExpiresAt;
    private final boolean cancelAtPeriodEnd;
    private final SubscriptionCancellationState cancellationState;

    /** 5G.12: only non-null while a downgrade is scheduled (Subscription#pendingPlan) - additive, never replaces planCode/planName which stay the CURRENT (still effective) plan until planChangeEffectiveAt. */
    private final PlanCode pendingPlanCode;
    private final String pendingPlanName;
    private final BigDecimal pendingPlanPrice;
    private final Instant planChangeEffectiveAt;

    public BillingMeDTO(
        PlanCode planCode,
        String planName,
        SubscriptionStatus subscriptionStatus,
        BillingCycle billingCycle,
        SubscriptionSource subscriptionSource,
        long activeVehicleCount,
        Integer vehicleLimit,
        boolean canAddVehicle,
        boolean needsUpgrade,
        PlanCode requiredPlanCode,
        String requiredPlanName,
        BigDecimal currentMonthlyPrice,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant grantExpiresAt,
        boolean cancelAtPeriodEnd,
        SubscriptionCancellationState cancellationState,
        PlanCode pendingPlanCode,
        String pendingPlanName,
        BigDecimal pendingPlanPrice,
        Instant planChangeEffectiveAt
    ) {
        this.planCode = planCode;
        this.planName = planName;
        this.subscriptionStatus = subscriptionStatus;
        this.billingCycle = billingCycle;
        this.subscriptionSource = subscriptionSource;
        this.activeVehicleCount = activeVehicleCount;
        this.vehicleLimit = vehicleLimit;
        this.canAddVehicle = canAddVehicle;
        this.needsUpgrade = needsUpgrade;
        this.requiredPlanCode = requiredPlanCode;
        this.requiredPlanName = requiredPlanName;
        this.currentMonthlyPrice = currentMonthlyPrice;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.grantExpiresAt = grantExpiresAt;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.cancellationState = cancellationState;
        this.pendingPlanCode = pendingPlanCode;
        this.pendingPlanName = pendingPlanName;
        this.pendingPlanPrice = pendingPlanPrice;
        this.planChangeEffectiveAt = planChangeEffectiveAt;
    }

    public static BillingMeDTO from(EntitlementSnapshot snapshot) {
        Subscription subscription = snapshot.getSubscription();
        Plan currentPlan = snapshot.getCurrentPlan();
        Plan requiredPlan = snapshot.getRequiredPlan();
        Plan pendingPlan = subscription != null ? subscription.getPendingPlan() : null;

        BigDecimal currentMonthlyPrice = subscription != null ? subscription.getContractedPrice() : currentPlan.getMonthlyBasePrice();

        return new BillingMeDTO(
            currentPlan.getCode(),
            currentPlan.getName(),
            subscription != null ? subscription.getStatus() : null,
            subscription != null ? subscription.getBillingCycle() : null,
            subscription != null ? subscription.getSource() : null,
            snapshot.getActiveVehicleCount(),
            snapshot.getVehicleLimit(),
            snapshot.isCanAddVehicle(),
            snapshot.isNeedsUpgrade(),
            requiredPlan.getCode(),
            requiredPlan.getName(),
            currentMonthlyPrice,
            subscription != null ? subscription.getCurrentPeriodStart() : null,
            subscription != null ? subscription.getCurrentPeriodEnd() : null,
            subscription != null && subscription.getSource() == SubscriptionSource.ADMIN_GRANT
                ? subscription.getGrantExpiresAt()
                : null,
            subscription != null && Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
            subscription == null ? SubscriptionCancellationState.NONE
                : SubscriptionCancellationState.from(subscription.getCancelAtPeriodEnd(), subscription.getCanceledAt()),
            pendingPlan != null ? pendingPlan.getCode() : null,
            pendingPlan != null ? pendingPlan.getName() : null,
            pendingPlan != null ? subscription.getPendingContractedPrice() : null,
            pendingPlan != null ? subscription.getPlanChangeEffectiveAt() : null
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

    public SubscriptionSource getSubscriptionSource() {
        return subscriptionSource;
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

    public Instant getGrantExpiresAt() {
        return grantExpiresAt;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public SubscriptionCancellationState getCancellationState() {
        return cancellationState;
    }

    public PlanCode getPendingPlanCode() {
        return pendingPlanCode;
    }

    public String getPendingPlanName() {
        return pendingPlanName;
    }

    public BigDecimal getPendingPlanPrice() {
        return pendingPlanPrice;
    }

    public Instant getPlanChangeEffectiveAt() {
        return planChangeEffectiveAt;
    }
}
