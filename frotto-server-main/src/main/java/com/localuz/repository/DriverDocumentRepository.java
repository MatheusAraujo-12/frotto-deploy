package com.localuz.repository;

import com.localuz.domain.DriverDocument;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {
    @Query(
        "select document from DriverDocument document " +
        "left join fetch document.driver " +
        "left join fetch document.car " +
        "where document.user.login = ?#{principal.username} and document.id = :id"
    )
    Optional<DriverDocument> findByCurrentUserAndId(@Param("id") Long id);

    @Query(
        "select document from DriverDocument document " +
        "left join document.driver driver " +
        "left join document.car car " +
        "where document.user.login = ?#{principal.username} " +
        "and (:driverId is null or driver.id = :driverId) " +
        "and (:carId is null or car.id = :carId) " +
        "and (:type is null or document.type = :type) " +
        "and (:status is null or document.status = :status) " +
        "order by document.createdAt desc, document.id desc"
    )
    List<DriverDocument> findByCurrentUserWithFilters(
        @Param("driverId") Long driverId,
        @Param("carId") Long carId,
        @Param("type") DocumentType type,
        @Param("status") DocumentStatus status,
        Pageable pageable
    );
}
