package com.localuz.service.dto;

import com.localuz.domain.PlanPricingTier;
import java.math.BigDecimal;

/** One progressive-pricing tier of a plan, for GET /api/billing/plans. Not persisted. */
public class PlanPricingTierDTO {

    private final Integer fromVehicleCount;
    private final Integer toVehicleCount;
    private final BigDecimal pricePerVehicle;

    public PlanPricingTierDTO(Integer fromVehicleCount, Integer toVehicleCount, BigDecimal pricePerVehicle) {
        this.fromVehicleCount = fromVehicleCount;
        this.toVehicleCount = toVehicleCount;
        this.pricePerVehicle = pricePerVehicle;
    }

    public static PlanPricingTierDTO from(PlanPricingTier tier) {
        return new PlanPricingTierDTO(tier.getFromVehicleCount(), tier.getToVehicleCount(), tier.getPricePerVehicle());
    }

    public Integer getFromVehicleCount() {
        return fromVehicleCount;
    }

    public Integer getToVehicleCount() {
        return toVehicleCount;
    }

    public BigDecimal getPricePerVehicle() {
        return pricePerVehicle;
    }
}
