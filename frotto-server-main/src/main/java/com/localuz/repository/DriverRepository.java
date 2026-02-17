package com.localuz.repository;

import com.localuz.domain.Driver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Driver entity. */
@SuppressWarnings("unused")
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByCpf(String cpf);

    @Query(
        "select distinct driver from Driver driver " +
        "join driver.driverCars driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driver.id = :id"
    )
    Optional<Driver> findByCurrentUserAndId(@Param("id") Long id);

    @Query(
        "select distinct driver from Driver driver " +
        "join driver.driverCars driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} " +
        "and (" +
        ":q = '' " +
        "or lower(coalesce(driver.name, '')) like lower(concat('%', :q, '%')) " +
        "or (:qDigits <> '' and replace(replace(replace(replace(coalesce(driver.cpf, ''), '.', ''), '-', ''), '/', ''), ' ', '') like concat('%', :qDigits, '%'))" +
        ") " +
        "order by driver.name asc"
    )
    List<Driver> searchByCurrentUser(@Param("q") String q, @Param("qDigits") String qDigits);
}
