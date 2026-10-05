package com.localuz.repository;

import com.localuz.domain.Driver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for the Driver entity. */
public interface DriverRepository extends JpaRepository<Driver, Long> {
    // Não escopado por usuário: usado internamente (ex.: DriverCarResource) para decidir se um
    // motorista com este CPF já existe no sistema antes de vincular a um novo contrato. Não expor
    // via endpoint REST diretamente — use findByCurrentUserAndCpf para requests autenticados.
    Optional<Driver> findByCpf(String cpf);

    /** Row lock serializing concurrent assignments of the same driver (see DriverAssignmentService). */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select driver from Driver driver where driver.id = :id")
    Optional<Driver> findByIdForUpdate(@Param("id") Long id);

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
        "where car.user.login = ?#{principal.username} and driver.cpf = :cpf"
    )
    Optional<Driver> findByCurrentUserAndCpf(@Param("cpf") String cpf);

    /** Same filter as findByCurrentUserAndCpf, tolerating accounts that already hold duplicated CPFs. */
    @Query(
        "select distinct driver from Driver driver " +
        "join driver.driverCars driverCar " +
        "join driverCar.car car " +
        "where car.user.login = ?#{principal.username} and driver.cpf = :cpf"
    )
    List<Driver> findAllByCurrentUserAndCpf(@Param("cpf") String cpf);

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
