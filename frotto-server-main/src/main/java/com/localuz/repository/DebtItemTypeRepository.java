package com.localuz.repository;

import com.localuz.domain.DebtItemType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DebtItemTypeRepository extends JpaRepository<DebtItemType, Long> {
    List<DebtItemType> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<DebtItemType> findByActiveFalseOrderBySortOrderAscNameAsc();

    List<DebtItemType> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
