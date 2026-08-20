package com.localuz.service.dto;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** One row of a user's Subscription history, for GET /api/admin/billing/users/{userId}. */
public class SubscriptionHistoryEntryDTO {

    private final Long id;
    private final PlanCode planCode;
    private final String planName;
    private final SubscriptionSource source;
    private final SubscriptionStatus status;
    private final Instant startDate;
    private final Instant canceledAt;
    private final String grantedByLogin;
    private final String grantReason;
    private final Instant grantExpiresAt;
    private final BigDecimal contractedPrice;
    private final int contractedVehicleCount;

    public SubscriptionHistoryEntryDTO(
        Long id,
        PlanCode planCode,
        String planName,
        SubscriptionSource source,
        SubscriptionStatus status,
        Instant startDate,
        Instant canceledAt,
        String grantedByLogin,
        String grantReason,
        Instant grantExpiresAt,
        BigDecimal contractedPrice,
        int contractedVehicleCount
    ) {
        this.id = id;
        this.planCode = planCode;
        this.planName = planName;
        this.source = source;
        this.status = status;
        this.startDate = startDate;
        this.canceledAt = canceledAt;
        this.grantedByLogin = grantedByLogin;
        this.grantReason = grantReason;
        this.grantExpiresAt = grantExpiresAt;
        this.contractedPrice = contractedPrice;
        this.contractedVehicleCount = contractedVehicleCount;
    }

    public static SubscriptionHistoryEntryDTO from(Subscription subscription) {
        return new SubscriptionHistoryEntryDTO(
            subscription.getId(),
            subscription.getPlan().getCode(),
            subscription.getPlan().getName(),
            subscription.getSource(),
            subscription.getStatus(),
            subscription.getStartDate(),
            subscription.getCanceledAt(),
            subscription.getGrantedBy() != null ? subscription.getGrantedBy().getLogin() : null,
            subscription.getGrantReason(),
            subscription.getGrantExpiresAt(),
            subscription.getContractedPrice(),
            subscription.getContractedVehicleCount()
        );
    }

    public Long getId() {
        return id;
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

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public String getGrantedByLogin() {
        return grantedByLogin;
    }

    public String getGrantReason() {
        return grantReason;
    }

    public Instant getGrantExpiresAt() {
        return grantExpiresAt;
    }

    public BigDecimal getContractedPrice() {
        return contractedPrice;
    }

    public int getContractedVehicleCount() {
        return contractedVehicleCount;
    }
}
