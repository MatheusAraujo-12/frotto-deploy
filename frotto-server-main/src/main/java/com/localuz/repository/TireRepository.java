package com.localuz.repository;

import com.localuz.domain.Tire;
import org.springframework.data.jpa.repository.*;

/** Spring Data JPA repository for the Tire entity. */
public interface TireRepository extends JpaRepository<Tire, Long> {}
