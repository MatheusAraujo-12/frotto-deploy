package com.localuz.repository;

import com.localuz.domain.DriverCar;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the DriverCar entity. */
public interface DriverCarRepository extends JpaRepository<DriverCar, Long> {

    @Query(
        "select driverCar from DriverCar driverCar  join driverCar.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY driverCar.startDate DESC"
    )
    List<DriverCar> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    /**
     * Vehicle history of one driver in this account: every contract (open or concluded) on a car of the current user,
     * most recent first. A driver shared with another account never brings that account's cars.
     */
    @Query(
        "select driverCar from DriverCar driverCar join fetch driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driverCar.driver.id = :driverId " +
        "order by driverCar.startDate desc, driverCar.id desc"
    )
    List<DriverCar> findHistoryByCurrentUserAndDriver(@Param("driverId") Long driverId);

    @Query(
        "select driverCar from DriverCar driverCar  join driverCar.car car  where car.user.login = ?#{principal.username} and driverCar.id = :id"
    )
    Optional<DriverCar> findByCurrentUserAndId(@Param("id") Long id);

    @Query(
        "select driverCar from DriverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and driverCar.driver.id = :driverId and driverCar.car.id = :carId " +
        "and (driverCar.concluded = false or driverCar.concluded is null) " +
        "order by driverCar.startDate desc, driverCar.id desc"
    )
    List<DriverCar> findActiveByCurrentUserAndDriverAndCar(
        @Param("driverId") Long driverId,
        @Param("carId") Long carId
    );

    /** Open contracts (not concluded: ACTIVE or SUSPENDED) of a driver in the current user's fleet, any car. */
    @Query(
        "select driverCar from DriverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and driverCar.driver.id = :driverId " +
        "and (driverCar.concluded = false or driverCar.concluded is null) " +
        "order by driverCar.startDate desc, driverCar.id desc"
    )
    List<DriverCar> findOpenByCurrentUserAndDriver(@Param("driverId") Long driverId);

    /** Operational (ACTIVE, not suspended) contracts of a driver in the current user's fleet, except one. Always read from the database. */
    @Query(
        "select count(driverCar) from DriverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and driverCar.driver.id = :driverId and driverCar.id <> :exceptId " +
        "and (driverCar.concluded = false or driverCar.concluded is null) and driverCar.suspended = false"
    )
    long countOperationalByCurrentUserAndDriver(@Param("driverId") Long driverId, @Param("exceptId") Long exceptId);

    /**
     * Operational contracts occupying a car, except one: not concluded (false or NULL - the same rule as the driver
     * side) and not suspended. Always read from the database.
     */
    @Query(
        "select count(driverCar) from DriverCar driverCar " +
        "where driverCar.car.id = :carId and driverCar.id <> :exceptId " +
        "and (driverCar.concluded = false or driverCar.concluded is null) and driverCar.suspended = false"
    )
    long countOperationalOnCar(@Param("carId") Long carId, @Param("exceptId") Long exceptId);

    /** Operational contracts of a car (same rule as countOperationalOnCar), most recent first. */
    @Query(
        "select driverCar from DriverCar driverCar " +
        "where driverCar.car.id = :carId " +
        "and (driverCar.concluded = false or driverCar.concluded is null) and driverCar.suspended = false " +
        "order by driverCar.startDate desc, driverCar.id desc"
    )
    List<DriverCar> findOperationalOnCar(@Param("carId") Long carId);

    /** Car of a contract, read without loading the contract (used to take the car lock first). */
    @Query("select driverCar.car.id from DriverCar driverCar where driverCar.id = :id")
    Long findCarIdById(@Param("id") Long id);

    /** True when a reserve contract points to {@code primaryDriverCarId} as its primary. Explicit JPQL: not derivable by name. */
    @Query(
        "select case when count(driverCar) > 0 then true else false end from DriverCar driverCar " +
        "where driverCar.primaryDriverCar.id = :primaryDriverCarId"
    )
    boolean existsByPrimaryDriverCarId(@Param("primaryDriverCarId") Long primaryDriverCarId);

    @Query(
        "select driverCar from DriverCar driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and driverCar.driver.id = :driverId and driverCar.car.id = :carId " +
        "order by driverCar.startDate desc, driverCar.id desc"
    )
    List<DriverCar> findByCurrentUserAndDriverAndCarOrderByStartDateDesc(
        @Param("driverId") Long driverId,
        @Param("carId") Long carId
    );

    /** SELECT ... FOR UPDATE of one contract row (after the ownership check and the car/driver locks). */
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select driverCar from DriverCar driverCar where driverCar.id = :id")
    Optional<DriverCar> findByIdForUpdate(@Param("id") Long id);

    /** True when a FINAL checklist of this type was recorded on the contract (e.g. its Devolução). */
    @Query(
        "select count(document) > 0 from DriverDocument document where document.driverCarId = :id " +
        "and document.finalChecklistSlot = :type"
    )
    boolean hasFinalChecklist(@Param("id") Long id, @Param("type") com.localuz.domain.enumeration.ChecklistType type);
}
