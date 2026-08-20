package com.localuz.service.dto;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response for GET /api/admin/billing/users/{userId}. Same entitlement fields as
 * BillingMeDTO (reuses EntitlementSnapshot, not a second implementation of the same logic)
 * plus admin-only audit data: source, grant metadata, and a summarized subscription history.
 * Never externalProvider/externalSubscriptionId.
 *
 * currentSubscriptionId is the id of the *effective* Subscription row (null when FREE) - it's
 * exposed specifically so an admin UI can call POST /grants/{subscriptionId}/revoke without
 * having to reverse-engineer which history entry is "the current one".
 */
public class AdminBillingUserDTO {

    private final Long userId;
    private final String userLogin;
    private final String userEmail;

    private final Long currentSubscriptionId;
    private final PlanCode planCode;
    private final String planName;
    private final SubscriptionSource source;
    private final SubscriptionStatus subscriptionStatus;
    private final BillingCycle billingCycle;
    private final Instant grantExpiresAt;
    private final Instant currentPeriodStart;
    private final Instant currentPeriodEnd;

    private final long activeVehicleCount;
    private final Integer vehicleLimit;
    private final boolean canAddVehicle;
    private final boolean needsUpgrade;
    private final PlanCode requiredPlanCode;
    private final String requiredPlanName;

    private final List<SubscriptionHistoryEntryDTO> history;

    public AdminBillingUserDTO(
        Long userId,
        String userLogin,
        String userEmail,
        Long currentSubscriptionId,
        PlanCode planCode,
        String planName,
        SubscriptionSource source,
        SubscriptionStatus subscriptionStatus,
        BillingCycle billingCycle,
        Instant grantExpiresAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        long activeVehicleCount,
        Integer vehicleLimit,
        boolean canAddVehicle,
        boolean needsUpgrade,
        PlanCode requiredPlanCode,
        String requiredPlanName,
        List<SubscriptionHistoryEntryDTO> history
    ) {
        this.userId = userId;
        this.userLogin = userLogin;
        this.userEmail = userEmail;
        this.currentSubscriptionId = currentSubscriptionId;
        this.planCode = planCode;
        this.planName = planName;
        this.source = source;
        this.subscriptionStatus = subscriptionStatus;
        this.billingCycle = billingCycle;
        this.grantExpiresAt = grantExpiresAt;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.activeVehicleCount = activeVehicleCount;
        this.vehicleLimit = vehicleLimit;
        this.canAddVehicle = canAddVehicle;
        this.needsUpgrade = needsUpgrade;
        this.requiredPlanCode = requiredPlanCode;
        this.requiredPlanName = requiredPlanName;
        this.history = history == null ? Collections.emptyList() : Collections.unmodifiableList(history);
    }

    public static AdminBillingUserDTO from(User user, EntitlementSnapshot snapshot, List<Subscription> history) {
        Subscription subscription = snapshot.getSubscription();
        Plan currentPlan = snapshot.getCurrentPlan();
        Plan requiredPlan = snapshot.getRequiredPlan();

        return new AdminBillingUserDTO(
            user.getId(),
            user.getLogin(),
            user.getEmail(),
            subscription != null ? subscription.getId() : null,
            currentPlan.getCode(),
            currentPlan.getName(),
            subscription != null ? subscription.getSource() : null,
            subscription != null ? subscription.getStatus() : null,
            subscription != null ? subscription.getBillingCycle() : null,
            subscription != null ? subscription.getGrantExpiresAt() : null,
            subscription != null ? subscription.getCurrentPeriodStart() : null,
            subscription != null ? subscription.getCurrentPeriodEnd() : null,
            snapshot.getActiveVehicleCount(),
            snapshot.getVehicleLimit(),
            snapshot.isCanAddVehicle(),
            snapshot.isNeedsUpgrade(),
            requiredPlan.getCode(),
            requiredPlan.getName(),
            history.stream().map(SubscriptionHistoryEntryDTO::from).collect(Collectors.toList())
        );
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public Long getCurrentSubscriptionId() {
        return currentSubscriptionId;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public SubscriptionSource getSource() {
        return source;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public Instant getGrantExpiresAt() {
        return grantExpiresAt;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
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

    public List<SubscriptionHistoryEntryDTO> getHistory() {
        return history;
    }
}
