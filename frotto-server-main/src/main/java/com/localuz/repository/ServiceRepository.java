package com.localuz.repository;

import com.localuz.domain.Maintenance;
import com.localuz.domain.Service;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the Service entity. */
@SuppressWarnings("unused")
@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
    @Query(
        "select service from Service service  join service.maintenance.car car  where car.user.login = ?#{principal.username} and service.id = :id"
    )
    Optional<Service> findByCurrentUserAndServiceId(@Param("id") Long id);

    List<Service> findAllByMaintenance(Maintenance maintenance);

    @Modifying
    @Query("delete from Service service where service.id in :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);
}
