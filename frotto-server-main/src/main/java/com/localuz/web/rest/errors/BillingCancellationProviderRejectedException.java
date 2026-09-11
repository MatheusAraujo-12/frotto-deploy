package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** Safe cancellation failure, deliberately detached from the provider exception. */
public class BillingCancellationProviderRejectedException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingCancellationProviderRejectedException() {
        super(ErrorConstants.DEFAULT_TYPE, "Cancellation rejected", Status.CONFLICT,
            "O Mercado Pago não aceitou o cancelamento neste momento. Sua assinatura permanece ativa e nenhuma alteração de cobrança foi confirmada.",
            null, null, Map.of("message", "error.BILLING_CANCELLATION_PROVIDER_REJECTED"));
    }
}
