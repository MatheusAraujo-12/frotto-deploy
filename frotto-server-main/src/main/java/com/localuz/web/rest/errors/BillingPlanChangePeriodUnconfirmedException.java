package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12.1: the current paid cycle cannot be determined authoritatively, so no prorated amount is computed (fail closed, never estimated). */
@SuppressWarnings("java:S110")
public class BillingPlanChangePeriodUnconfirmedException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanChangePeriodUnconfirmedException() {
        super(ErrorConstants.DEFAULT_TYPE, "Current billing cycle could not be confirmed", Status.CONFLICT,
            "Não foi possível confirmar o ciclo atual da sua assinatura para calcular o valor proporcional. Nenhuma alteração foi feita. Tente novamente mais tarde.",
            null, null, Map.of("message", "error.BILLING_PLAN_CHANGE_PERIOD_UNCONFIRMED"));
    }
}
