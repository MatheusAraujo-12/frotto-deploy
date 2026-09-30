package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public class BillingDowngradeUndoConflictException extends AbstractThrowableProblem {
    public BillingDowngradeUndoConflictException() {
        super(ErrorConstants.DEFAULT_TYPE, "Downgrade undo conflict", Status.CONFLICT,
            "Não foi possível concluir o desfazimento porque o estado da assinatura mudou durante a operação. Atualize os dados e tente novamente.",
            null, null, Map.of("message", "error.BILLING_DOWNGRADE_UNDO_CONFLICT"));
    }
}
