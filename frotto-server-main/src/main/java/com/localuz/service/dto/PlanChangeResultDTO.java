package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response for POST /api/billing/change-plan and POST /api/billing/change-plan/undo-downgrade.
 * Deliberately excludes anything gateway/admin-facing (externalSubscriptionId, preference/payment
 * ids, idempotency keys) - same safe-field philosophy as BillingMeDTO/SubscriptionCancellationResultDTO.
 * checkoutUrl is the Mercado Pago payment page the user must open to pay the prorated charge; it is
 * only present while status=UPGRADE_PAYMENT_REQUIRED.
 *
 * 5G.12.1: status is the authoritative outcome (see PlanChangeStatus). An UPGRADE is never active
 * before status=UPGRADE_APPLIED; currentPlan stays effective in every other status.
 *
 * - chargeAmount: prorated amount due now (UPGRADE only; 0.00 when nothing is due).
 * - nextRenewalPrice: the recurring amount the provider will charge from the next renewal on.
 * - contractedPrice: kept for compatibility - the recurring price of targetPlan.
 */
public class PlanChangeResultDTO {
    private final PlanCode currentPlan;
    private final PlanCode targetPlan;
    private final PlanChangeType changeType;
    private final PlanChangeStatus status;
    private final Instant effectiveAt;
    private final BigDecimal contractedPrice;
    private final BigDecimal chargeAmount;
    private final BigDecimal nextRenewalPrice;
    private final String checkoutUrl;
    private final boolean pending;

    public PlanChangeResultDTO(PlanCode currentPlan, PlanCode targetPlan, PlanChangeType changeType, PlanChangeStatus status,
        Instant effectiveAt, BigDecimal contractedPrice, BigDecimal chargeAmount, BigDecimal nextRenewalPrice, String checkoutUrl) {
        this.currentPlan = currentPlan;
        this.targetPlan = targetPlan;
        this.changeType = changeType;
        this.status = status;
        this.effectiveAt = effectiveAt;
        this.contractedPrice = contractedPrice;
        this.chargeAmount = chargeAmount;
        this.nextRenewalPrice = nextRenewalPrice;
        this.checkoutUrl = checkoutUrl;
        this.pending = status != PlanChangeStatus.UPGRADE_APPLIED && status != PlanChangeStatus.DOWNGRADE_UNDONE;
    }

    public PlanCode getCurrentPlan() { return currentPlan; }
    public PlanCode getTargetPlan() { return targetPlan; }
    public PlanChangeType getChangeType() { return changeType; }
    public PlanChangeStatus getStatus() { return status; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public BigDecimal getContractedPrice() { return contractedPrice; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public BigDecimal getNextRenewalPrice() { return nextRenewalPrice; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public boolean isPending() { return pending; }
}
