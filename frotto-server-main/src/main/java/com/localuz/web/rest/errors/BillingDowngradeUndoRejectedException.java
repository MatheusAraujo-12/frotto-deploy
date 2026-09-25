package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** 5G.12.1: Mercado Pago did not confirm restoring the recurring amount, so the scheduled downgrade is kept exactly as it was. */
@SuppressWarnings("java:S110")
public class BillingDowngradeUndoRejectedException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingDowngradeUndoRejectedException() {
        super(ErrorConstants.DEFAULT_TYPE, "Downgrade undo not confirmed", Status.CONFLICT,
            "O Mercado Pago não confirmou a restauração do valor da assinatura. O downgrade continua agendado.",
            null, null, Map.of("message", "error.BILLING_DOWNGRADE_UNDO_REJECTED"));
    }
}
