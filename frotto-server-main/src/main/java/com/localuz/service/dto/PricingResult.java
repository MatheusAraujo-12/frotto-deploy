package com.localuz.service.dto;

import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** Result of PricingService#calculateMonthlyPrice. Not persisted. */
public class PricingResult {

    private final PlanCode planCode;
    private final String planName;
    private final int vehicleCount;
    private final BigDecimal monthlyPrice;
    private final List<PricingComponent> components;

    public PricingResult(PlanCode planCode, String planName, int vehicleCount, BigDecimal monthlyPrice, List<PricingComponent> components) {
        this.planCode = planCode;
        this.planName = planName;
        this.vehicleCount = vehicleCount;
        this.monthlyPrice = monthlyPrice;
        this.components = components == null ? Collections.emptyList() : Collections.unmodifiableList(components);
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public List<PricingComponent> getComponents() {
        return components;
    }
}
