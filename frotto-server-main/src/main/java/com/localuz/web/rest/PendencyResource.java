package com.localuz.web.rest;

import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

/** REST controller for managing {@link com.localuz.domain.Pendency}. */
@RestController
@RequestMapping("/api")
@Transactional
public class PendencyResource {

    private final Logger log = LoggerFactory.getLogger(PendencyResource.class);

    private static final String ENTITY_NAME = "pendency";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PendencyRepository pendencyRepository;

    private final DriverCarRepository driverCarRepository;

    public PendencyResource(PendencyRepository pendencyRepository, DriverCarRepository driverCarRepository) {
        this.pendencyRepository = pendencyRepository;
        this.driverCarRepository = driverCarRepository;
    }

    @GetMapping("/pendencies/car-driver/{carDriverId}")
    public List<Pendency> getPendencyByCarDriver(@PathVariable Long carDriverId) {
        log.debug("REST request to get Pendency  by carDriverId : {}", carDriverId);
        List<Pendency> pendencies = pendencyRepository.findByCurrentUserAndDriverCarId(carDriverId);
        return pendencies;
    }

    @GetMapping("/pendencies/{id}")
    public Pendency getPendencyById(@PathVariable Long id) {
        log.debug("REST request to get Pendency  by id : {}", id);
        Optional<Pendency> pendency = pendencyRepository.findByCurrentUserAndPendencyId(id);
        if (pendency.isPresent()) {
            return pendency.get();
        }
        return null;
    }

    @PostMapping("/pendencies/car-driver/{carDriverId}")
    public ResponseEntity<Pendency> createPendency(@PathVariable Long carDriverId, @Valid @RequestBody Pendency pendency)
        throws URISyntaxException {
        log.debug("REST request to save Pendency : {}", pendency);
        if (pendency.getId() != null) {
            throw new BadRequestAlertException("A new pendency cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<DriverCar> existingCarDriverOpt = driverCarRepository.findByCurrentUserAndId(carDriverId);
        if (!existingCarDriverOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        DriverCar pendencyCarDriver = existingCarDriverOpt.get();
        pendency.setDriverCar(pendencyCarDriver);

        Pendency result = pendencyRepository.save(pendency);
        return ResponseEntity
            .created(new URI("/api/pendencies/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/pendencies/{id}")
    public ResponseEntity<Void> deletePendency(@PathVariable Long id) {
        log.debug("REST request to delete Pendency : {}", id);
        Optional<Pendency> existingPendencyOpt = pendencyRepository.findByCurrentUserAndPendencyId(id);
        if (!existingPendencyOpt.isPresent()) {
            throw new BadRequestAlertException("Pendency not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        pendencyRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/pendencies/{id}")
    public ResponseEntity<Pendency> updatePendency(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Pendency pendency
    ) throws URISyntaxException {
        log.debug("REST request to update Pendency : {}, {}", id, pendency);
        if (pendency.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, pendency.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Pendency> existingPendencyOpt = pendencyRepository.findByCurrentUserAndPendencyId(id);
        if (!existingPendencyOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Driver not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        DriverCar pendencyCarDriver = existingPendencyOpt.get().getDriverCar();
        pendency.setDriverCar(pendencyCarDriver);

        Pendency result = pendencyRepository.save(pendency);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, pendency.getId().toString()))
            .body(result);
    }
}
