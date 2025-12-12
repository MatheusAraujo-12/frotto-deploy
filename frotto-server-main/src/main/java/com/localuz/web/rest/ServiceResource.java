package com.localuz.web.rest;

import com.localuz.domain.Maintenance;
import com.localuz.domain.Service;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.repository.ServiceRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/** REST controller for managing {@link com.localuz.domain.Service}. */
@RestController
@RequestMapping("/api")
@Transactional
public class ServiceResource {

    private final Logger log = LoggerFactory.getLogger(ServiceResource.class);

    private static final String ENTITY_NAME = "service";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ServiceRepository serviceRepository;

    private final MaintenanceRepository maintenanceRepository;

    public ServiceResource(ServiceRepository serviceRepository, MaintenanceRepository maintenanceRepository) {
        this.serviceRepository = serviceRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping("/services/{id}")
    public Service getServiceById(@PathVariable Long id) {
        log.debug("REST request to get Service  by id : {}", id);
        Optional<Service> existingServiceOpt = serviceRepository.findByCurrentUserAndServiceId(id);
        if (existingServiceOpt.isPresent()) {
            return existingServiceOpt.get();
        }
        return null;
    }

    @PostMapping("/services/maintenance/{maintenanceId}")
    public ResponseEntity<Service> createServiceByMaintenanceId(@Valid @RequestBody Service service, @PathVariable Long maintenanceId)
        throws URISyntaxException {
        log.debug("REST request to save Service : {}", service);
        if (service.getId() != null) {
            throw new BadRequestAlertException("A new service cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Maintenance> existingMaintenanceOpt = maintenanceRepository.findByCurrentUserAndMaintenanceId(maintenanceId);
        if (!existingMaintenanceOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        service.setMaintenance(existingMaintenanceOpt.get());

        Service result = serviceRepository.save(service);
        return ResponseEntity
            .created(new URI("/api/services/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        log.debug("REST request to delete Service : {}", id);
        Optional<Service> existingServiceOpt = serviceRepository.findByCurrentUserAndServiceId(id);
        if (!existingServiceOpt.isPresent()) {
            throw new BadRequestAlertException("Maintenance-Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        serviceRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<Service> updateService(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Service service
    ) throws URISyntaxException {
        log.debug("REST request to update Service : {}, {}", id, service);
        if (service.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, service.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Service> existingServiceOpt = serviceRepository.findByCurrentUserAndServiceId(id);
        if (!existingServiceOpt.isPresent()) {
            throw new BadRequestAlertException("Maintenance-Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        service.setMaintenance(existingServiceOpt.get().getMaintenance());

        Service result = serviceRepository.save(service);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, service.getId().toString()))
            .body(result);
    }
}
