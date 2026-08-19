package com.localuz.service.dto;

import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Response for GET /api/billing/price-preview. Wraps a PricingResult (the actual pricing
 * calculation, owned by PricingService) with the request's billingCycle and a derived
 * per-vehicle average - no pricing formula is duplicated here.
 */
public class PricePreviewDTO {

    private final int vehicleCount;
    private final PlanCode planCode;
    private final String planName;
    private final BillingCycle billingCycle;
    private final BigDecimal monthlyPrice;
    private final BigDecimal averagePricePerVehicle;
    private final List<PricingComponent> components;

    public PricePreviewDTO(
        int vehicleCount,
        PlanCode planCode,
        String planName,
        BillingCycle billingCycle,
        BigDecimal monthlyPrice,
        BigDecimal averagePricePerVehicle,
        List<PricingComponent> components
    ) {
        this.vehicleCount = vehicleCount;
        this.planCode = planCode;
        this.planName = planName;
        this.billingCycle = billingCycle;
        this.monthlyPrice = monthlyPrice;
        this.averagePricePerVehicle = averagePricePerVehicle;
        this.components = components;
    }

    public static PricePreviewDTO from(PricingResult result, BillingCycle billingCycle) {
        BigDecimal average = result.getVehicleCount() == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : result.getMonthlyPrice().divide(BigDecimal.valueOf(result.getVehicleCount()), 2, RoundingMode.HALF_UP);

        return new PricePreviewDTO(
            result.getVehicleCount(),
            result.getPlanCode(),
            result.getPlanName(),
            billingCycle,
            result.getMonthlyPrice(),
            average,
            result.getComponents()
        );
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public BigDecimal getAveragePricePerVehicle() {
        return averagePricePerVehicle;
    }

    public List<PricingComponent> getComponents() {
        return components;
    }
}
