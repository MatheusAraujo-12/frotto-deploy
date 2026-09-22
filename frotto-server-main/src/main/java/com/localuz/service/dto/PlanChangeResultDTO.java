package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response for POST /api/billing/change-plan. Deliberately excludes anything gateway/admin-facing
 * (externalSubscriptionId, idempotency keys) - same safe-field philosophy as BillingMeDTO/
 * SubscriptionCancellationResultDTO.
 *
 * currentPlan/targetPlan describe the transition that was requested, not necessarily what GET
 * /api/billing/me reports right after this call: for an UPGRADE, targetPlan is already the new
 * effective plan (pending=false); for a DOWNGRADE (including a paid-plan target=FREE, which
 * reuses the existing cancellation flow), currentPlan stays effective until effectiveAt
 * (pending=true) - see docs/billing-plan-change-5g12.md.
 */
public class PlanChangeResultDTO {
    private final PlanCode currentPlan;
    private final PlanCode targetPlan;
    private final PlanChangeType changeType;
    private final Instant effectiveAt;
    private final BigDecimal contractedPrice;
    private final boolean pending;

    public PlanChangeResultDTO(PlanCode currentPlan, PlanCode targetPlan, PlanChangeType changeType,
        Instant effectiveAt, BigDecimal contractedPrice, boolean pending) {
        this.currentPlan = currentPlan;
        this.targetPlan = targetPlan;
        this.changeType = changeType;
        this.effectiveAt = effectiveAt;
        this.contractedPrice = contractedPrice;
        this.pending = pending;
    }

    public PlanCode getCurrentPlan() { return currentPlan; }
    public PlanCode getTargetPlan() { return targetPlan; }
    public PlanChangeType getChangeType() { return changeType; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public BigDecimal getContractedPrice() { return contractedPrice; }
    public boolean isPending() { return pending; }
}
