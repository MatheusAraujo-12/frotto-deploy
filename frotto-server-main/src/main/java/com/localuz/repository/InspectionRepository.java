package com.localuz.repository;

import com.localuz.domain.Car;
import com.localuz.domain.Inspection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Inspection entity.
 *
 * <p>When extending this class, extend InspectionRepositoryWithBagRelationships too. For more
 * information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface InspectionRepository extends InspectionRepositoryWithBagRelationships, JpaRepository<Inspection, Long> {
    default Optional<Inspection> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findById(id));
    }

    default List<Inspection> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAll());
    }

    default Page<Inspection> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAll(pageable));
    }

    Inspection findFirstByCarOrderByDateDesc(Car car);

    @Query(
        "select inspection from Inspection inspection  join inspection.car car  where car.user.login = ?#{principal.username} and car.id = :carId ORDER BY inspection.date DESC"
    )
    List<Inspection> findByCurrentUserAndCarIdByDate(@Param("carId") Long carId);

    @Query(
        "select inspection from Inspection inspection  join inspection.car car  where car.user.login = ?#{principal.username} and inspection.id = :id"
    )
    Optional<Inspection> findByCurrentUserAndInspectionId(@Param("id") Long id);

    @Query(
        "select inspection from Inspection inspection join inspection.car car  where car.id = :carId and month(inspection.date) = :month and year(inspection.date) = :year ORDER BY inspection.date ASC"
    )
    List<Inspection> findByCarIdAndMonthYear(@Param("carId") Long carId, @Param("month") int month, @Param("year") int year);
}
