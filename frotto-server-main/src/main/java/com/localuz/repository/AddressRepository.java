package com.localuz.repository;

import com.localuz.domain.Address;
import org.springframework.data.jpa.repository.*;

/** Spring Data JPA repository for the Address entity. */
public interface AddressRepository extends JpaRepository<Address, Long> {}
