package com.localuz.web.rest.errors;

import com.localuz.domain.enumeration.PlanCode;
import java.util.HashMap;
import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * Prepared for the future car-creation enforcement point (EntitlementService#canAddVehicle) -
 * not thrown anywhere yet. Car creation does not call EntitlementService today; see the Billing
 * Etapa 2 report for the grandfathering plan that must land before this can be wired in safely.
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class VehicleLimitReachedException extends AbstractThrowableProblem {

    private static final long serialVersionUID = 1L;

    public VehicleLimitReachedException(PlanCode currentPlan, long currentVehicleCount, Integer vehicleLimit, PlanCode requiredPlan) {
        super(
            ErrorConstants.VEHICLE_LIMIT_REACHED_TYPE,
            "Vehicle limit reached for the current plan",
            Status.CONFLICT,
            null,
            null,
            null,
            parameters(currentPlan, currentVehicleCount, vehicleLimit, requiredPlan)
        );
    }

    private static Map<String, Object> parameters(
        PlanCode currentPlan,
        long currentVehicleCount,
        Integer vehicleLimit,
        PlanCode requiredPlan
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("message", "error.VEHICLE_LIMIT_REACHED");
        params.put("currentPlan", currentPlan);
        params.put("currentVehicleCount", currentVehicleCount);
        params.put("vehicleLimit", vehicleLimit);
        params.put("requiredPlan", requiredPlan);
        return params;
    }
}
