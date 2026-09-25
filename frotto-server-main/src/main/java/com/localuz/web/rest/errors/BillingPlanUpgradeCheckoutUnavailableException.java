package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12.1: the prorated-charge checkout could not be created. Nothing was charged and the current plan is unchanged. */
@SuppressWarnings("java:S110")
public class BillingPlanUpgradeCheckoutUnavailableException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanUpgradeCheckoutUnavailableException() {
        super(ErrorConstants.DEFAULT_TYPE, "Upgrade checkout unavailable", Status.CONFLICT,
            "Não foi possível gerar o pagamento do upgrade no Mercado Pago. Nada foi cobrado e seu plano atual continua o mesmo.",
            null, null, Map.of("message", "error.BILLING_PLAN_UPGRADE_CHECKOUT_UNAVAILABLE"));
    }
}
