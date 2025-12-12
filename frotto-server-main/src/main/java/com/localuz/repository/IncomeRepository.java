package com.localuz.repository;

import com.localuz.domain.Income;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Income entity. */
@SuppressWarnings("unused")
@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {
    @Query(
        "select income from Income income  join income.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY income.date DESC"
    )
    List<Income> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query("select income from Income income  join income.car car  where car.user.login = ?#{principal.username} and income.id = :id")
    Optional<Income> findByCurrentUserAndIncomeId(@Param("id") Long id);

    @Query(
        "select income from Income income join income.car car  where car.id = :carId and month(income.date) = :month and year(income.date) = :year  ORDER BY income.date ASC"
    )
    List<Income> findByCarIdAndMonthYear(@Param("carId") Long carId, @Param("month") int month, @Param("year") int year);
}
