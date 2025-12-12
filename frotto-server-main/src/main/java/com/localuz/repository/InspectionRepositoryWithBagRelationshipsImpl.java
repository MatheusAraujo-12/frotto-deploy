package com.localuz.repository;

import com.localuz.domain.Inspection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.hibernate.annotations.QueryHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Utility repository to load bag relationships based on
 * https://vladmihalcea.com/hibernate-multiplebagfetchexception/
 */
public class InspectionRepositoryWithBagRelationshipsImpl implements InspectionRepositoryWithBagRelationships {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Inspection> fetchBagRelationships(Optional<Inspection> inspection) {
        return inspection.map(this::fetchCarBodyDamages);
    }

    @Override
    public Page<Inspection> fetchBagRelationships(Page<Inspection> inspections) {
        return new PageImpl<>(fetchBagRelationships(inspections.getContent()), inspections.getPageable(), inspections.getTotalElements());
    }

    @Override
    public List<Inspection> fetchBagRelationships(List<Inspection> inspections) {
        return Optional.of(inspections).map(this::fetchCarBodyDamages).orElse(Collections.emptyList());
    }

    Inspection fetchCarBodyDamages(Inspection result) {
        return entityManager
            .createQuery(
                "select inspection from Inspection inspection left join fetch inspection.carBodyDamages where inspection is :inspection",
                Inspection.class
            )
            .setParameter("inspection", result)
            .setHint(QueryHints.PASS_DISTINCT_THROUGH, false)
            .getSingleResult();
    }

    List<Inspection> fetchCarBodyDamages(List<Inspection> inspections) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, inspections.size()).forEach(index -> order.put(inspections.get(index).getId(), index));
        List<Inspection> result = entityManager
            .createQuery(
                "select distinct inspection from Inspection inspection left join fetch inspection.carBodyDamages where inspection in :inspections",
                Inspection.class
            )
            .setParameter("inspections", inspections)
            .setHint(QueryHints.PASS_DISTINCT_THROUGH, false)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
