package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * An idempotency key that already belongs to another operation (other values, or a key this account cannot see):
 * nothing is created and nothing about the other operation is revealed.
 */
@SuppressWarnings("java:S110")
public class IdempotencyConflictException extends AbstractThrowableProblem {

    private static final long serialVersionUID = 1L;

    public static final String ERROR_KEY = "idempotencykeyconflict";

    public IdempotencyConflictException() {
        super(
            ErrorConstants.DEFAULT_TYPE,
            "Esta operação já foi usada para outra cobrança. Abra a tela novamente para registrar uma nova.",
            Status.CONFLICT,
            null,
            null,
            null,
            Map.of("message", "error." + ERROR_KEY, "params", "pendency")
        );
    }

    public String getErrorKey() {
        return ERROR_KEY;
    }
}
