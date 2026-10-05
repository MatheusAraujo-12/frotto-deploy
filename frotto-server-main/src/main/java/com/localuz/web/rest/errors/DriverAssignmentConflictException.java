package com.localuz.web.rest.errors;

import java.util.HashMap;
import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * The driver's or the car's current contracts require a decision from the user (reserve or definitive transfer) or
 * block the operation. Carries the conflicting contract so the client can explain it.
 */
@SuppressWarnings("java:S110")
public class DriverAssignmentConflictException extends AbstractThrowableProblem {

    private static final long serialVersionUID = 1L;

    public static final String ASSIGNMENT_REQUIRED = "driverassignmentrequired";
    public static final String RESTORE_CONFLICT = "drivercarrestoreconflict";

    private final String errorKey;

    public DriverAssignmentConflictException(String errorKey, String message, Long conflictingDriverCarId, String conflictingCarPlate) {
        super(ErrorConstants.DEFAULT_TYPE, message, Status.CONFLICT, null, null, null, parameters(errorKey, conflictingDriverCarId, conflictingCarPlate));
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }

    private static Map<String, Object> parameters(String errorKey, Long conflictingDriverCarId, String conflictingCarPlate) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", "error." + errorKey);
        parameters.put("params", "driverCar");
        parameters.put("conflictingDriverCarId", conflictingDriverCarId);
        parameters.put("conflictingCarPlate", conflictingCarPlate);
        return parameters;
    }
}
