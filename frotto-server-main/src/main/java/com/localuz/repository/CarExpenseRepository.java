package com.localuz.repository;

import com.localuz.domain.CarExpense;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarExpenseRepository extends JpaRepository<CarExpense, Long> {
    @Query(
        "select carExpense from CarExpense carExpense  join carExpense.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY carExpense.date DESC"
    )
    List<CarExpense> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query(
        "select carExpense from CarExpense carExpense  join carExpense.car car  where car.user.login = ?#{principal.username} and carExpense.id = :id"
    )
    Optional<CarExpense> findByCurrentUserAndCarExpenseId(@Param("id") Long id);

    @Query(
        "select carExpense from CarExpense carExpense join carExpense.car car  where car.id = :carId and month(carExpense.date) = :month and year(carExpense.date) = :year  ORDER BY carExpense.date ASC"
    )
    List<CarExpense> findByCarIdAndMonthYear(@Param("carId") Long carId, @Param("month") int month, @Param("year") int year);
}
