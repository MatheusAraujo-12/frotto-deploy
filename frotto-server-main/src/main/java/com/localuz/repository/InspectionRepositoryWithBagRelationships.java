package com.localuz.repository;

import com.localuz.domain.Inspection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

public interface InspectionRepositoryWithBagRelationships {
    Optional<Inspection> fetchBagRelationships(Optional<Inspection> inspection);

    List<Inspection> fetchBagRelationships(List<Inspection> inspections);

    Page<Inspection> fetchBagRelationships(Page<Inspection> inspections);
}
