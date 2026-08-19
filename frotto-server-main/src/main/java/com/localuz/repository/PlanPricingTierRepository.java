package com.localuz.repository;

import com.localuz.domain.PlanPricingTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the PlanPricingTier entity. */
public interface PlanPricingTierRepository extends JpaRepository<PlanPricingTier, Long> {
    List<PlanPricingTier> findByPlanIdOrderByTierOrderAsc(Long planId);
}
