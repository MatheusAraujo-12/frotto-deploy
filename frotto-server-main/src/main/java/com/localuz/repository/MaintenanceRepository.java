package com.localuz.repository;

import com.localuz.domain.Car;
import com.localuz.domain.Inspection;
import com.localuz.domain.Maintenance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Maintenance entity. */
@SuppressWarnings("unused")
@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    Maintenance findFirstByCarOrderByDateDesc(Car car);

    @Query(
        "select maintenance from Maintenance maintenance  join maintenance.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY maintenance.date DESC"
    )
    List<Maintenance> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query(
        "select maintenance from Maintenance maintenance  join maintenance.car car  where car.user.login = ?#{principal.username} and maintenance.id = :id"
    )
    Optional<Maintenance> findByCurrentUserAndMaintenanceId(@Param("id") Long id);

    @Query(
        "select maintenance from Maintenance maintenance join maintenance.car car  where car.id = :carId and month(maintenance.date) = :month and year(maintenance.date) = :year ORDER BY maintenance.date ASC"
    )
    List<Maintenance> findByCarIdAndMonthYear(@Param("carId") Long carId, @Param("month") int month, @Param("year") int year);

    @Query(
        "select maintenance from Maintenance maintenance join maintenance.car car  where car.id = :carId and year(maintenance.date) = :year ORDER BY maintenance.date DESC"
    )
    List<Maintenance> findByCarIdAndYear(@Param("carId") Long carId, @Param("year") int year);
}
