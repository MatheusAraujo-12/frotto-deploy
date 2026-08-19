package com.localuz.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.Plan;
import com.localuz.domain.PlanPricingTier;
import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanDTOTest {

    @Test
    void flatPlanHasNoTiersAndIsMarkedFlat() {
        Plan bronze = new Plan();
        bronze.setCode(PlanCode.BRONZE);
        bronze.setName("Bronze");
        bronze.setMinVehicles(3);
        bronze.setMaxVehicles(5);
        bronze.setMonthlyBasePrice(new BigDecimal("15.90"));

        PlanDTO dto = PlanDTO.from(bronze, List.of());

        assertThat(dto.getBillingModel()).isEqualTo(PlanDTO.BILLING_MODEL_FLAT);
        assertThat(dto.getTiers()).isEmpty();
    }

    @Test
    void progressivePlanExposesItsTiers() {
        Plan frotta = new Plan();
        frotta.setCode(PlanCode.FROTTA);
        frotta.setName("Frotta");
        frotta.setMinVehicles(101);
        frotta.setMaxVehicles(null);
        frotta.setMonthlyBasePrice(new BigDecimal("254.90"));

        PlanPricingTier tier = new PlanPricingTier();
        tier.setFromVehicleCount(101);
        tier.setToVehicleCount(200);
        tier.setPricePerVehicle(new BigDecimal("2.00"));

        PlanDTO dto = PlanDTO.from(frotta, List.of(tier));

        assertThat(dto.getBillingModel()).isEqualTo(PlanDTO.BILLING_MODEL_PROGRESSIVE);
        assertThat(dto.getMaxVehicles()).isNull();
        assertThat(dto.getTiers()).hasSize(1);
        assertThat(dto.getTiers().get(0).getPricePerVehicle()).isEqualByComparingTo(new BigDecimal("2.00"));
    }
}
