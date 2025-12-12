package com.localuz.repository;

import com.localuz.domain.Tire;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Tire entity. */
@SuppressWarnings("unused")
@Repository
public interface TireRepository extends JpaRepository<Tire, Long> {}
