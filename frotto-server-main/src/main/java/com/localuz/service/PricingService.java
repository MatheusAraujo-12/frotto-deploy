package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.PlanPricingTier;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.localuz.service.dto.PricingComponent;
import com.localuz.service.dto.PricingResult;

/**
 * Single source of truth for translating a vehicle count into a plan and a price.
 *
 * Plan ranges and flat base prices come from the `plan` table; progressive per-vehicle
 * increments (PLATINUM, FROTTA) come from `plan_pricing_tier`. No plan-selection or
 * pricing constants are hardcoded here so that changing prices/limits never requires a
 * code change - only the DB configuration.
 */
@Service
@Transactional(readOnly = true)
public class PricingService {

    private final PlanRepository planRepository;
    private final PlanPricingTierRepository planPricingTierRepository;

    public PricingService(PlanRepository planRepository, PlanPricingTierRepository planPricingTierRepository) {
        this.planRepository = planRepository;
        this.planPricingTierRepository = planPricingTierRepository;
    }

    public PricingResult calculatePrice(int vehicleCount, BillingCycle billingCycle) {
        if (billingCycle == BillingCycle.YEARLY) {
            throw new UnsupportedOperationException(
                "Yearly pricing is not yet configured; only MONTHLY is implemented in this stage"
            );
        }
        return calculateMonthlyPrice(vehicleCount);
    }

    public PricingResult calculateMonthlyPrice(int vehicleCount) {
        Plan plan = resolvePlanForVehicleCount(vehicleCount);
        List<PlanPricingTier> tiers = planPricingTierRepository.findByPlanIdOrderByTierOrderAsc(plan.getId());

        if (tiers.isEmpty()) {
            return new PricingResult(plan.getCode(), plan.getName(), vehicleCount, scale(plan.getMonthlyBasePrice()), List.of());
        }

        BigDecimal total = plan.getMonthlyBasePrice();
        List<PricingComponent> components = new ArrayList<>();
        for (PlanPricingTier tier : tiers) {
            int from = tier.getFromVehicleCount();
            if (vehicleCount < from) {
                continue;
            }
            int effectiveTo = tier.getToVehicleCount() != null ? Math.min(vehicleCount, tier.getToVehicleCount()) : vehicleCount;
            int quantity = effectiveTo - from + 1;
            if (quantity <= 0) {
                continue;
            }
            BigDecimal subtotal = tier.getPricePerVehicle().multiply(BigDecimal.valueOf(quantity));
            total = total.add(subtotal);
            components.add(new PricingComponent(from, tier.getToVehicleCount(), tier.getPricePerVehicle(), quantity, scale(subtotal)));
        }

        return new PricingResult(plan.getCode(), plan.getName(), vehicleCount, scale(total), components);
    }

    /**
     * The plan whose [minVehicles, maxVehicles] range covers vehicleCount, regardless of what
     * plan any given user is actually contracted for. Used both by calculateMonthlyPrice and by
     * EntitlementService to compute a user's "required plan" independently of their "current plan".
     */
    public Plan resolvePlanForVehicleCount(int vehicleCount) {
        if (vehicleCount < 0) {
            throw new IllegalArgumentException("vehicleCount must be >= 0, got " + vehicleCount);
        }
        return planRepository
            .findByActiveTrueOrderByMinVehiclesAsc()
            .stream()
            .filter(p -> p.getMinVehicles() != null && vehicleCount >= p.getMinVehicles())
            .filter(p -> p.getMaxVehicles() == null || vehicleCount <= p.getMaxVehicles())
            .max(Comparator.comparingInt(Plan::getMinVehicles))
            .orElseThrow(() -> new IllegalStateException("No active plan configured for vehicleCount=" + vehicleCount));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
