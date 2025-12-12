package com.localuz.repository;

import com.localuz.domain.Driver;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Driver entity. */
@SuppressWarnings("unused")
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByCpf(String cpf);
}
