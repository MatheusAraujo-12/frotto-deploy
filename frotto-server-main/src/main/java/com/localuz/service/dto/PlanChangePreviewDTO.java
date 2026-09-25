package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 5G.12.1: GET /api/billing/change-plan/preview - everything the confirmation modal shows, computed
 * by the backend (PricingService + authoritative cycle), never on the frontend. Informational only:
 * POST /change-plan recomputes everything itself and never trusts a previewed value.
 *
 * - chargeNow: prorated amount due immediately (UPGRADE only, null for a DOWNGRADE).
 * - newMonthlyPrice: the recurring amount from the next renewal on.
 * - cycleEnd: end of the current paid cycle (a DOWNGRADE takes effect here).
 */
public class PlanChangePreviewDTO {
    private final PlanCode currentPlan;
    private final PlanCode targetPlan;
    private final PlanChangeType changeType;
    private final BigDecimal currentPrice;
    private final BigDecimal newMonthlyPrice;
    private final BigDecimal chargeNow;
    private final Instant cycleEnd;

    public PlanChangePreviewDTO(PlanCode currentPlan, PlanCode targetPlan, PlanChangeType changeType, BigDecimal currentPrice,
        BigDecimal newMonthlyPrice, BigDecimal chargeNow, Instant cycleEnd) {
        this.currentPlan = currentPlan;
        this.targetPlan = targetPlan;
        this.changeType = changeType;
        this.currentPrice = currentPrice;
        this.newMonthlyPrice = newMonthlyPrice;
        this.chargeNow = chargeNow;
        this.cycleEnd = cycleEnd;
    }

    public PlanCode getCurrentPlan() { return currentPlan; }
    public PlanCode getTargetPlan() { return targetPlan; }
    public PlanChangeType getChangeType() { return changeType; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getNewMonthlyPrice() { return newMonthlyPrice; }
    public BigDecimal getChargeNow() { return chargeNow; }
    public Instant getCycleEnd() { return cycleEnd; }
}
