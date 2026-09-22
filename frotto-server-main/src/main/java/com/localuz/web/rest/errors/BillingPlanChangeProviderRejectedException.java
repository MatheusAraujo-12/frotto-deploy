package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * 5G.12: Mercado Pago either definitively rejected the new recurring amount, or the authoritative
 * confirming GET could not prove the new value/currency took effect (including the fail-closed
 * "could not determine" case) - safe cancellation-style failure, detached from the provider
 * exception itself. Never means the local plan/pending state was left inconsistent: see
 * SubscriptionPlanChangeService for exactly what is/ isn't rolled back in each case.
 */
public class BillingPlanChangeProviderRejectedException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanChangeProviderRejectedException() {
        super(ErrorConstants.DEFAULT_TYPE, "Plan change rejected", Status.CONFLICT,
            "O Mercado Pago não confirmou a alteração do valor da assinatura. Nenhuma mudança de plano foi aplicada.",
            null, null, Map.of("message", "error.BILLING_PLAN_CHANGE_PROVIDER_REJECTED"));
    }
}
