package com.localuz.service.dto;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin-facing view of a Subscription - full audit trail (who granted it, why, until when),
 * but still never externalProvider/externalSubscriptionId (gateway metadata, unused here and
 * not this endpoint's business anyway).
 */
public class SubscriptionAdminDTO {

    private final Long id;
    private final Long userId;
    private final String userLogin;
    private final PlanCode planCode;
    private final String planName;
    private final SubscriptionSource source;
    private final SubscriptionStatus status;
    private final BillingCycle billingCycle;
    private final Instant startDate;
    private final Long grantedByUserId;
    private final String grantedByLogin;
    private final Instant grantedAt;
    private final String grantReason;
    private final Instant grantExpiresAt;
    private final Instant canceledAt;
    private final BigDecimal contractedPrice;
    private final int contractedVehicleCount;

    public SubscriptionAdminDTO(
        Long id,
        Long userId,
        String userLogin,
        PlanCode planCode,
        String planName,
        SubscriptionSource source,
        SubscriptionStatus status,
        BillingCycle billingCycle,
        Instant startDate,
        Long grantedByUserId,
        String grantedByLogin,
        Instant grantedAt,
        String grantReason,
        Instant grantExpiresAt,
        Instant canceledAt,
        BigDecimal contractedPrice,
        int contractedVehicleCount
    ) {
        this.id = id;
        this.userId = userId;
        this.userLogin = userLogin;
        this.planCode = planCode;
        this.planName = planName;
        this.source = source;
        this.status = status;
        this.billingCycle = billingCycle;
        this.startDate = startDate;
        this.grantedByUserId = grantedByUserId;
        this.grantedByLogin = grantedByLogin;
        this.grantedAt = grantedAt;
        this.grantReason = grantReason;
        this.grantExpiresAt = grantExpiresAt;
        this.canceledAt = canceledAt;
        this.contractedPrice = contractedPrice;
        this.contractedVehicleCount = contractedVehicleCount;
    }

    public static SubscriptionAdminDTO from(Subscription subscription) {
        return new SubscriptionAdminDTO(
            subscription.getId(),
            subscription.getUser().getId(),
            subscription.getUser().getLogin(),
            subscription.getPlan().getCode(),
            subscription.getPlan().getName(),
            subscription.getSource(),
            subscription.getStatus(),
            subscription.getBillingCycle(),
            subscription.getStartDate(),
            subscription.getGrantedBy() != null ? subscription.getGrantedBy().getId() : null,
            subscription.getGrantedBy() != null ? subscription.getGrantedBy().getLogin() : null,
            subscription.getGrantedAt(),
            subscription.getGrantReason(),
            subscription.getGrantExpiresAt(),
            subscription.getCanceledAt(),
            subscription.getContractedPrice(),
            subscription.getContractedVehicleCount()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserLogin() {
        return userLogin;
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

    public SubscriptionStatus getStatus() {
        return status;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Long getGrantedByUserId() {
        return grantedByUserId;
    }

    public String getGrantedByLogin() {
        return grantedByLogin;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public String getGrantReason() {
        return grantReason;
    }

    public Instant getGrantExpiresAt() {
        return grantExpiresAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public BigDecimal getContractedPrice() {
        return contractedPrice;
    }

    public int getContractedVehicleCount() {
        return contractedVehicleCount;
    }
}
