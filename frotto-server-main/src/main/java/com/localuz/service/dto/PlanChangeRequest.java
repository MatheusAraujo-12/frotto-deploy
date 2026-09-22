package com.localuz.service.dto;
import com.localuz.domain.enumeration.PlanCode;
import javax.validation.constraints.NotNull;
/** Request body for POST /api/billing/change-plan. Never carries price or userId - both are always server-derived. */
public class PlanChangeRequest {
    @NotNull private PlanCode targetPlanCode;
    public PlanCode getTargetPlanCode() { return targetPlanCode; }
    public void setTargetPlanCode(PlanCode targetPlanCode) { this.targetPlanCode = targetPlanCode; }
}
