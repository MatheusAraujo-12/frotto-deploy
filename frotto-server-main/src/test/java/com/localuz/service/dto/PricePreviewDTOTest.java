package com.localuz.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PricePreviewDTO only wraps PricingResult (no pricing math of its own) except the average. */
class PricePreviewDTOTest {

    @Test
    void computesAveragePricePerVehicleFromTheWrappedResult() {
        PricingResult result = new PricingResult(PlanCode.PLATINUM, "Platinum", 50, new BigDecimal("129.90"), List.of());

        PricePreviewDTO dto = PricePreviewDTO.from(result, BillingCycle.MONTHLY);

        assertThat(dto.getVehicleCount()).isEqualTo(50);
        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(dto.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(dto.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("129.90"));
        assertThat(dto.getAveragePricePerVehicle()).isEqualByComparingTo(new BigDecimal("2.60"));
    }

    @Test
    void doesNotDivideByZeroWhenVehicleCountIsZero() {
        PricingResult result = new PricingResult(PlanCode.FREE, "Gratuito", 0, new BigDecimal("0.00"), List.of());

        PricePreviewDTO dto = PricePreviewDTO.from(result, BillingCycle.MONTHLY);

        assertThat(dto.getAveragePricePerVehicle()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void carriesTheComponentsThroughUnmodified() {
        PricingComponent component = new PricingComponent(101, 200, new BigDecimal("2.00"), 50, new BigDecimal("100.00"));
        PricingResult result = new PricingResult(PlanCode.FROTTA, "Frotta", 150, new BigDecimal("354.90"), List.of(component));

        PricePreviewDTO dto = PricePreviewDTO.from(result, BillingCycle.MONTHLY);

        assertThat(dto.getComponents()).containsExactly(component);
    }
}
