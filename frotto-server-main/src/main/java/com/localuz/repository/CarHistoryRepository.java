package com.localuz.repository;

import com.localuz.domain.CarHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Car History entity. */
@SuppressWarnings("unused")
@Repository
public interface CarHistoryRepository extends JpaRepository<CarHistory, Long> {
    List<CarHistory> findAllByCarId(Long carId);
}
