package com.localuz.repository;

import com.localuz.domain.DriverDocument;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Ownership check that does not load the entity (so a locking read can load it fresh afterwards). */
    @Query(
        "select count(document) > 0 from DriverDocument document " +
        "where document.user.login = ?#{principal.username} and document.id = :id"
    )
    boolean existsByCurrentUserAndId(@Param("id") Long id);

    /** SELECT ... FOR UPDATE of the document row only; call it after the ownership check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from DriverDocument document where document.id = :id")
    Optional<DriverDocument> findByIdForUpdate(@Param("id") Long id);

    /** The document issued from a pendency for one type (unique by origin_pendency_id + type). */
    @Query("select document from DriverDocument document where document.originPendencyId = :pendencyId and document.type = :type")
    Optional<DriverDocument> findByOriginPendencyIdAndType(@Param("pendencyId") Long pendencyId, @Param("type") DocumentType type);
}
