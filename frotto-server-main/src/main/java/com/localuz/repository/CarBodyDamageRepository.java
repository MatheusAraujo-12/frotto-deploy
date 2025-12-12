package com.localuz.repository;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the CarBodyDamage entity. */
@SuppressWarnings("unused")
@Repository
public interface CarBodyDamageRepository extends JpaRepository<CarBodyDamage, Long> {
    List<CarBodyDamage> findAllByCar(Car car);

    @Query(
        "select carBD from CarBodyDamage carBD  join carBD.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY carBD.date DESC"
    )
    List<CarBodyDamage> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query(
        "select carBD from CarBodyDamage carBD  join carBD.car car  where car.user.login = ?#{principal.username} and car.id = :carId and carBD.resolved = false ORDER BY carBD.date DESC"
    )
    List<CarBodyDamage> findActiveByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query("select carBD from CarBodyDamage carBD  join carBD.car car  where car.user.login = ?#{principal.username} and carBD.id = :id")
    Optional<CarBodyDamage> findByCurrentUserAndCarBdId(@Param("id") Long id);
}
