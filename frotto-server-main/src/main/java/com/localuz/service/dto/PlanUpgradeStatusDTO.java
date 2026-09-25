package com.localuz.service.dto;

import com.localuz.domain.SubscriptionPlanUpgrade;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import com.localuz.service.SubscriptionPlanUpgradeSteps;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 5G.12.1: GET /api/billing/plan-upgrade - the caller's latest prorated upgrade attempt, after a
 * server-side reconciliation with the provider (never derived from the browser's return query
 * string). status=NONE when the user never started one. No provider ids, references or keys.
 *
 * - paymentRejected: the last payment attempt was declined; the user may retry while checkoutUrl
 *   is still offered (the attempt stays AWAITING_PAYMENT until the checkout window closes).
 */
public class PlanUpgradeStatusDTO {
    public enum Status { NONE, AWAITING_PAYMENT, APPLYING, APPLIED, EXPIRED, FAILED, REQUIRES_REVIEW }

    private final Status status;
    private final PlanCode fromPlan;
    private final PlanCode targetPlan;
    private final BigDecimal chargeAmount;
    private final BigDecimal targetPrice;
    private final String checkoutUrl;
    private final boolean paymentPending;
    private final boolean paymentRejected;
    private final Instant appliedAt;
    private final Instant updatedAt;

    private PlanUpgradeStatusDTO(Status status, PlanCode fromPlan, PlanCode targetPlan, BigDecimal chargeAmount, BigDecimal targetPrice,
        String checkoutUrl, boolean paymentPending, boolean paymentRejected, Instant appliedAt, Instant updatedAt) {
        this.status = status;
        this.fromPlan = fromPlan;
        this.targetPlan = targetPlan;
        this.chargeAmount = chargeAmount;
        this.targetPrice = targetPrice;
        this.checkoutUrl = checkoutUrl;
        this.paymentPending = paymentPending;
        this.paymentRejected = paymentRejected;
        this.appliedAt = appliedAt;
        this.updatedAt = updatedAt;
    }

    public static PlanUpgradeStatusDTO none() {
        return new PlanUpgradeStatusDTO(Status.NONE, null, null, null, null, null, false, false, null, null);
    }

    public static PlanUpgradeStatusDTO from(SubscriptionPlanUpgrade upgrade, Instant now) {
        if (upgrade == null) return none();
        boolean awaiting = upgrade.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT;
        boolean checkoutOpen = awaiting && upgrade.getCheckoutUrl() != null && now.isBefore(upgrade.getCheckoutExpiresAt());
        String last = upgrade.getLastPaymentStatus();
        return new PlanUpgradeStatusDTO(Status.valueOf(upgrade.getStatus().name()), upgrade.getFromPlan().getCode(),
            upgrade.getTargetPlan().getCode(), upgrade.getChargeAmount(), upgrade.getTargetPrice(),
            checkoutOpen ? upgrade.getCheckoutUrl() : null,
            awaiting && SubscriptionPlanUpgradeSteps.isPendingPaymentStatus(last),
            awaiting && SubscriptionPlanUpgradeSteps.isRejectedPaymentStatus(last),
            upgrade.getAppliedAt(), upgrade.getUpdatedAt());
    }

    public Status getStatus() { return status; }
    public PlanCode getFromPlan() { return fromPlan; }
    public PlanCode getTargetPlan() { return targetPlan; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public boolean isPaymentPending() { return paymentPending; }
    public boolean isPaymentRejected() { return paymentRejected; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
