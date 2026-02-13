package com.localuz.repository;

import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Pendency entity. */
@SuppressWarnings("unused")
@Repository
public interface PendencyRepository extends JpaRepository<Pendency, Long> {
    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserAndDriverCarIdOrderByDateDesc(@Param("driverCarId") Long driverCarId);

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId and pendency.status in :statuses " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserAndDriverCarIdAndStatusInOrderByDateDesc(
        @Param("driverCarId") Long driverCarId,
        @Param("statuses") List<PendencyStatus> statuses
    );

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.id = :id"
    )
    Optional<Pendency> findByCurrentUserAndPendencyId(@Param("id") Long id);

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserOrderByDateDesc();

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.status in :statuses " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserAndStatusInOrderByDateDesc(@Param("statuses") List<PendencyStatus> statuses);

    @Query(
        "select coalesce(sum(case when pendency.status is null or pendency.status <> com.localuz.domain.enumeration.PendencyStatus.PAID " +
        "then coalesce(pendency.remainingAmount, pendency.cost, 0) else 0 end), 0) " +
        "from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId"
    )
    BigDecimal findOutstandingTotalByCurrentUserAndDriverCarId(@Param("driverCarId") Long driverCarId);

    @Query(
        "select count(pendency) from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId " +
        "and (pendency.status is null or pendency.status <> com.localuz.domain.enumeration.PendencyStatus.PAID)"
    )
    long countOpenByCurrentUserAndDriverCarId(@Param("driverCarId") Long driverCarId);

    @Query(
        "select count(pendency) from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId"
    )
    long countByCurrentUserAndDriverCarId(@Param("driverCarId") Long driverCarId);
}
