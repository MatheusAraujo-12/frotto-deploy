package com.localuz.repository;

import com.localuz.domain.Plan;
import com.localuz.domain.enumeration.PlanCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the Plan entity. */
public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(PlanCode code);

    List<Plan> findByActiveTrueOrderByMinVehiclesAsc();
}
