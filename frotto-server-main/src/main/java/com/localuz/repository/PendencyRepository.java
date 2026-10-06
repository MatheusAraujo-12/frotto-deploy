package com.localuz.repository;

import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the Pendency entity. */
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
        "where car.user.login = ?#{principal.username} and pendency.debtor.id = :driverId " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserAndDriverIdOrderByDateDesc(@Param("driverId") Long driverId);

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and pendency.debtor.id = :driverId " +
        "and (pendency.status is null or pendency.status <> com.localuz.domain.enumeration.PendencyStatus.PAID) " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findOpenByCurrentUserAndDriverIdOrderByDateDesc(@Param("driverId") Long driverId);

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
        "where car.user.login = ?#{principal.username} and pendency.debtor.id = :driverId and pendency.status in :statuses " +
        "order by pendency.date desc, pendency.id desc"
    )
    List<Pendency> findByCurrentUserAndDriverIdAndStatusInOrderByDateDesc(
        @Param("driverId") Long driverId,
        @Param("statuses") List<PendencyStatus> statuses
    );

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.id = :id"
    )
    Optional<Pendency> findByCurrentUserAndPendencyId(@Param("id") Long id);

    /** Only the current user's pendencies among {@code ids}: ids of other accounts or missing ids are simply absent. */
    @Query(
        "select pendency from Pendency pendency " +
        "join fetch pendency.driverCar driverCar " +
        "join fetch driverCar.car car " +
        "left join fetch driverCar.driver driver " +
        "left join fetch pendency.debtor debtor " +
        "where car.user.login = ?#{principal.username} and pendency.id in :ids"
    )
    List<Pendency> findByCurrentUserAndIdIn(@Param("ids") Collection<Long> ids);

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
        "select coalesce(sum(case when pendency.status is null or pendency.status <> com.localuz.domain.enumeration.PendencyStatus.PAID " +
        "then coalesce(pendency.remainingAmount, pendency.cost, 0) else 0 end), 0) " +
        "from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.debtor.id = :driverId"
    )
    BigDecimal findOutstandingTotalByCurrentUserAndDriverId(@Param("driverId") Long driverId);

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
        "where car.user.login = ?#{principal.username} and pendency.debtor.id = :driverId " +
        "and (pendency.status is null or pendency.status <> com.localuz.domain.enumeration.PendencyStatus.PAID)"
    )
    long countOpenByCurrentUserAndDriverId(@Param("driverId") Long driverId);

    @Query(
        "select count(pendency) from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId"
    )
    long countByCurrentUserAndDriverCarId(@Param("driverCarId") Long driverCarId);

    @Query(
        "select count(pendency) from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.debtor.id = :driverId"
    )
    long countByCurrentUserAndDriverId(@Param("driverId") Long driverId);

    @Query(
        "select pendency from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.idempotencyKey = :key"
    )
    Optional<Pendency> findByCurrentUserAndIdempotencyKey(@Param("key") String key);

    /** Whether a key is taken at all (any account): only to tell a foreign key from another integrity error. */
    @Query("select count(pendency) > 0 from Pendency pendency where pendency.idempotencyKey = :key")
    boolean existsByIdempotencyKey(@Param("key") String key);

    @Query(
        "select count(pendency) > 0 from Pendency pendency " +
        "join pendency.driverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and pendency.id = :id"
    )
    boolean existsByCurrentUserAndId(@Param("id") Long id);

    /** SELECT ... FOR UPDATE of the pendency row only; call it after the ownership check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pendency from Pendency pendency where pendency.id = :id")
    Optional<Pendency> findByIdForUpdate(@Param("id") Long id);

    /** Locking read of every charge of a maintenance: always the latest committed rows, whatever the isolation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pendency from Pendency pendency where pendency.originMaintenanceId = :maintenanceId")
    List<Pendency> findByOriginMaintenanceIdForUpdate(@Param("maintenanceId") Long maintenanceId);

    /** Charges of a maintenance for display (the limit itself is always checked with the locking read above). */
    @Query("select pendency from Pendency pendency where pendency.originMaintenanceId = :maintenanceId order by pendency.id")
    List<Pendency> findByOriginMaintenanceId(@Param("maintenanceId") Long maintenanceId);

    @Query("select count(pendency) > 0 from Pendency pendency where pendency.originDocumentId = :documentId")
    boolean existsByOriginDocumentId(@Param("documentId") Long documentId);
}
