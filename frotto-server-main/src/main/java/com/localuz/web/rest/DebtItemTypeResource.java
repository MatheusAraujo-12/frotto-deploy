package com.localuz.web.rest;

import com.localuz.service.DebtItemTypeService;
import com.localuz.service.dto.DebtItemTypeDTO;
import com.localuz.service.dto.DebtItemTypeSaveDTO;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

@RestController
@RequestMapping("/api")
@Transactional
public class DebtItemTypeResource {

    private final Logger log = LoggerFactory.getLogger(DebtItemTypeResource.class);

    private static final String ENTITY_NAME = "debtItemType";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DebtItemTypeService debtItemTypeService;

    public DebtItemTypeResource(DebtItemTypeService debtItemTypeService) {
        this.debtItemTypeService = debtItemTypeService;
    }

    @GetMapping("/debt-item-types")
    public List<DebtItemTypeDTO> getDebtItemTypes(@RequestParam(name = "active", required = false, defaultValue = "true") String active) {
        log.debug("REST request to get debt item types with active filter: {}", active);
        return debtItemTypeService.list(active);
    }

    @PostMapping("/debt-item-types")
    public ResponseEntity<DebtItemTypeDTO> createDebtItemType(@RequestBody DebtItemTypeSaveDTO payload) throws URISyntaxException {
        log.debug("REST request to create debt item type: {}", payload == null ? null : payload.getName());
        DebtItemTypeDTO result = debtItemTypeService.create(payload);
        return ResponseEntity
            .created(new URI("/api/debt-item-types/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @PatchMapping("/debt-item-types/{id}")
    public ResponseEntity<DebtItemTypeDTO> updateDebtItemType(@PathVariable Long id, @RequestBody DebtItemTypeSaveDTO payload) {
        log.debug("REST request to update debt item type id={} payload={}", id, payload == null ? null : payload.getName());
        DebtItemTypeDTO result = debtItemTypeService.update(id, payload);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/debt-item-types/{id}")
    public ResponseEntity<Void> deleteDebtItemType(@PathVariable Long id) {
        log.debug("REST request to deactivate debt item type id={}", id);
        debtItemTypeService.deactivate(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
