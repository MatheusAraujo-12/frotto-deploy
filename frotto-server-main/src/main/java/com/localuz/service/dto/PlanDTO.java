package com.localuz.service.dto;

import com.localuz.domain.Plan;
import com.localuz.domain.PlanPricingTier;
import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Response element for GET /api/billing/plans. Never the Plan entity itself. */
public class PlanDTO {

    public static final String BILLING_MODEL_FLAT = "FLAT";
    public static final String BILLING_MODEL_PROGRESSIVE = "PROGRESSIVE";

    private final PlanCode code;
    private final String name;
    private final Integer minVehicles;
    private final Integer maxVehicles;
    private final BigDecimal monthlyBasePrice;
    private final String billingModel;
    private final List<PlanPricingTierDTO> tiers;

    public PlanDTO(
        PlanCode code,
        String name,
        Integer minVehicles,
        Integer maxVehicles,
        BigDecimal monthlyBasePrice,
        String billingModel,
        List<PlanPricingTierDTO> tiers
    ) {
        this.code = code;
        this.name = name;
        this.minVehicles = minVehicles;
        this.maxVehicles = maxVehicles;
        this.monthlyBasePrice = monthlyBasePrice;
        this.billingModel = billingModel;
        this.tiers = tiers == null ? Collections.emptyList() : Collections.unmodifiableList(tiers);
    }

    public static PlanDTO from(Plan plan, List<PlanPricingTier> tiers) {
        List<PlanPricingTierDTO> tierDTOs = tiers.stream().map(PlanPricingTierDTO::from).collect(Collectors.toList());
        String billingModel = tierDTOs.isEmpty() ? BILLING_MODEL_FLAT : BILLING_MODEL_PROGRESSIVE;

        return new PlanDTO(
            plan.getCode(),
            plan.getName(),
            plan.getMinVehicles(),
            plan.getMaxVehicles(),
            plan.getMonthlyBasePrice(),
            billingModel,
            tierDTOs
        );
    }

    public PlanCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getMinVehicles() {
        return minVehicles;
    }

    public Integer getMaxVehicles() {
        return maxVehicles;
    }

    public BigDecimal getMonthlyBasePrice() {
        return monthlyBasePrice;
    }

    public String getBillingModel() {
        return billingModel;
    }

    public List<PlanPricingTierDTO> getTiers() {
        return tiers;
    }
}
