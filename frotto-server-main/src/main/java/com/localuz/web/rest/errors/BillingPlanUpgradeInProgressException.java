package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12.1: a prorated upgrade is still awaiting payment or being applied - no other plan change may start meanwhile. */
@SuppressWarnings("java:S110")
public class BillingPlanUpgradeInProgressException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanUpgradeInProgressException() {
        super(ErrorConstants.DEFAULT_TYPE, "A plan upgrade is in progress", Status.CONFLICT,
            "Já existe um upgrade aguardando a confirmação do pagamento. Conclua ou aguarde a confirmação antes de fazer outra alteração.",
            null, null, Map.of("message", "error.BILLING_PLAN_UPGRADE_IN_PROGRESS"));
    }
}
