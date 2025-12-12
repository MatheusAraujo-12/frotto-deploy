package com.localuz.repository;

import com.localuz.domain.Pendency;
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
        "select pendency from Pendency pendency  join pendency.driverCar driverCar join driverCar.car car  where car.user.login = ?#{principal.username} and driverCar.id = :driverCarId"
    )
    List<Pendency> findByCurrentUserAndDriverCarId(@Param("driverCarId") Long driverCarId);

    @Query(
        "select pendency from Pendency pendency  join pendency.driverCar.car car  where car.user.login = ?#{principal.username} and pendency.id = :id"
    )
    Optional<Pendency> findByCurrentUserAndPendencyId(@Param("id") Long id);
}
