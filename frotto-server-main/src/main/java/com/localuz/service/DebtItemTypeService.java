package com.localuz.service;

import com.localuz.domain.DebtItemType;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.service.dto.DebtItemTypeDTO;
import com.localuz.service.dto.DebtItemTypeSaveDTO;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DebtItemTypeService {

    private final DebtItemTypeRepository debtItemTypeRepository;

    public DebtItemTypeService(DebtItemTypeRepository debtItemTypeRepository) {
        this.debtItemTypeRepository = debtItemTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<DebtItemTypeDTO> list(String activeFilter) {
        String normalizedFilter = normalizeFilter(activeFilter);
        List<DebtItemType> items;
        if ("false".equals(normalizedFilter)) {
            items = debtItemTypeRepository.findByActiveFalseOrderBySortOrderAscNameAsc();
        } else if ("all".equals(normalizedFilter)) {
            items = debtItemTypeRepository.findAllByOrderBySortOrderAscNameAsc();
        } else {
            items = debtItemTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        }
        return items.stream().map(this::toDto).collect(Collectors.toList());
    }

    public DebtItemTypeDTO create(DebtItemTypeSaveDTO payload) {
        String name = normalizeAndValidateName(payload == null ? null : payload.getName(), null);
        DebtItemType entity = new DebtItemType();
        entity.setName(name);
        entity.setActive(payload == null || payload.getActive() == null ? Boolean.TRUE : payload.getActive());
        entity.setSortOrder(payload == null || payload.getSortOrder() == null ? 0 : payload.getSortOrder());
        return toDto(debtItemTypeRepository.save(entity));
    }

    public DebtItemTypeDTO update(Long id, DebtItemTypeSaveDTO payload) {
        DebtItemType entity = getOrThrow(id);
        if (payload == null) {
            return toDto(entity);
        }

        if (payload.getName() != null) {
            entity.setName(normalizeAndValidateName(payload.getName(), id));
        }
        if (payload.getActive() != null) {
            entity.setActive(payload.getActive());
        }
        if (payload.getSortOrder() != null) {
            entity.setSortOrder(payload.getSortOrder());
        }
        return toDto(debtItemTypeRepository.save(entity));
    }

    public void deactivate(Long id) {
        DebtItemType entity = getOrThrow(id);
        if (!Boolean.FALSE.equals(entity.getActive())) {
            entity.setActive(Boolean.FALSE);
            debtItemTypeRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public DebtItemType getOrThrow(Long id) {
        return debtItemTypeRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Debt item type not found"));
    }

    private String normalizeFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return "true";
        }
        String normalized = filter.trim().toLowerCase(Locale.ROOT);
        if ("false".equals(normalized) || "all".equals(normalized)) {
            return normalized;
        }
        return "true";
    }

    private String normalizeAndValidateName(String rawName, Long currentId) {
        if (rawName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        String normalized = rawName.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }

        boolean duplicated =
            currentId == null
                ? debtItemTypeRepository.existsByNameIgnoreCase(normalized)
                : debtItemTypeRepository.existsByNameIgnoreCaseAndIdNot(normalized, currentId);
        if (duplicated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debt item type already exists");
        }

        return normalized;
    }

    private DebtItemTypeDTO toDto(DebtItemType entity) {
        DebtItemTypeDTO dto = new DebtItemTypeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setActive(entity.getActive());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
