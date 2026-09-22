package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12: the requested target plan is already the contracted plan - explicit conflict, never a silent no-op, never a provider call. */
@SuppressWarnings("java:S110")
public class BillingPlanChangeNoOpException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanChangeNoOpException() {
        super(ErrorConstants.DEFAULT_TYPE, "Target plan is already the current plan", Status.CONFLICT,
            "Você já está no plano selecionado.",
            null, null, Map.of("message", "error.BILLING_PLAN_CHANGE_NOOP"));
    }
}
