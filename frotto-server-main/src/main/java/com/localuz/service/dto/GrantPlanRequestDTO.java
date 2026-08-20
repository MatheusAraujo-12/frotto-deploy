package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;
import java.time.Instant;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Request body for POST /api/admin/billing/grants. The user is always looked up by id. */
public class GrantPlanRequestDTO {

    @NotNull
    private Long userId;

    @NotNull
    private PlanCode planCode;

    private Instant expiresAt;

    @Size(max = 500)
    private String reason;

    public GrantPlanRequestDTO() {}

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public void setPlanCode(PlanCode planCode) {
        this.planCode = planCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
