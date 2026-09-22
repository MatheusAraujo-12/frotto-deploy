package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12: at most one scheduled plan change per subscription (section 13) - a second request is refused, not silently replaced. */
@SuppressWarnings("java:S110")
public class BillingPlanChangeAlreadyPendingException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanChangeAlreadyPendingException() {
        super(ErrorConstants.DEFAULT_TYPE, "A plan change is already scheduled", Status.CONFLICT,
            "Já existe uma mudança de plano agendada para esta assinatura. Aguarde a efetivação antes de solicitar outra.",
            null, null, Map.of("message", "error.BILLING_PLAN_CHANGE_ALREADY_PENDING"));
    }
}
