package com.localuz.repository;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.domain.DriverCar;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the DriverCar entity. */
@SuppressWarnings("unused")
@Repository
public interface DriverCarRepository extends JpaRepository<DriverCar, Long> {
    DriverCar findFirstByConcludedAndCar(Boolean concluded, Car car);

    @Query(
        "select driverCar from DriverCar driverCar  join driverCar.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY driverCar.startDate DESC"
    )
    List<DriverCar> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query(
        "select driverCar from DriverCar driverCar  join driverCar.car car  where car.user.login = ?#{principal.username} and driverCar.id = :id"
    )
    Optional<DriverCar> findByCurrentUserAndId(@Param("id") Long id);
}
