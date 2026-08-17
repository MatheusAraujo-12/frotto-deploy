package com.localuz.repository;

import com.localuz.domain.CarHistory;
import com.localuz.domain.CarHistoryId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the Car History entity. */
public interface CarHistoryRepository extends JpaRepository<CarHistory, CarHistoryId> {
    List<CarHistory> findAllByCarId(Long carId);

    List<CarHistory> findByCarIdAndDateBetween(Long carId, LocalDate startDate, LocalDate endDate);
}
