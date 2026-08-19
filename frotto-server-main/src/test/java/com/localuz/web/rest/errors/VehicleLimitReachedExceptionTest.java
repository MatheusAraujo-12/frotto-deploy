package com.localuz.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.enumeration.PlanCode;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Status;

/**
 * Not wired into any endpoint yet (see the Billing Etapa 2 report); this only locks in the
 * shape the exception will have once car-creation enforcement is turned on.
 */
class VehicleLimitReachedExceptionTest {

    @Test
    void exposesTheDataNeededByTheFrontendToOfferAnUpgrade() {
        VehicleLimitReachedException ex = new VehicleLimitReachedException(PlanCode.BRONZE, 5L, 5, PlanCode.SILVER);

        assertThat(ex.getStatus()).isEqualTo(Status.CONFLICT);
        assertThat(ex.getParameters()).containsEntry("currentPlan", PlanCode.BRONZE);
        assertThat(ex.getParameters()).containsEntry("currentVehicleCount", 5L);
        assertThat(ex.getParameters()).containsEntry("vehicleLimit", 5);
        assertThat(ex.getParameters()).containsEntry("requiredPlan", PlanCode.SILVER);
    }
}
