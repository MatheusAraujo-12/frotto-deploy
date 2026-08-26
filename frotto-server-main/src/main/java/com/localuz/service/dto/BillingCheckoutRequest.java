package com.localuz.service.dto;
import com.localuz.domain.enumeration.PlanCode;
import javax.validation.constraints.NotNull;
public class BillingCheckoutRequest {
    @NotNull private PlanCode planCode;
    public PlanCode getPlanCode() { return planCode; }
    public void setPlanCode(PlanCode planCode) { this.planCode = planCode; }
}
