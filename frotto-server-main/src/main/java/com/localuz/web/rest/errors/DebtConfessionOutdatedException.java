package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * The pendencies of a Confissão de Dívida no longer match what the document shows (paid, balance or data changed):
 * the confession must be refreshed, it is never corrected silently.
 */
@SuppressWarnings("java:S110")
public class DebtConfessionOutdatedException extends AbstractThrowableProblem {

    private static final long serialVersionUID = 1L;

    public static final String ERROR_KEY = "confessionpendencieschanged";

    public DebtConfessionOutdatedException() {
        super(
            ErrorConstants.DEFAULT_TYPE,
            "As pendências da confissão foram alteradas. Atualize a confissão antes de continuar.",
            Status.CONFLICT,
            null,
            null,
            null,
            Map.of("message", "error." + ERROR_KEY, "params", "document")
        );
    }

    public String getErrorKey() {
        return ERROR_KEY;
    }
}
