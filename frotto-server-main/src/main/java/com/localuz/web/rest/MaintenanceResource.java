package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Service;
import com.localuz.repository.CarRepository;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.repository.ServiceRepository;
import com.localuz.service.CarService;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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

/** REST controller for managing {@link com.localuz.domain.Maintenance}. */
@RestController
@RequestMapping("/api")
@Transactional
public class MaintenanceResource {

    private final Logger log = LoggerFactory.getLogger(MaintenanceResource.class);

    private static final String ENTITY_NAME = "maintenance";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MaintenanceRepository maintenanceRepository;

    private final ServiceRepository serviceRepository;

    private final CarRepository carRepository;

    private final CarService carService;

    public MaintenanceResource(
        MaintenanceRepository maintenanceRepository,
        CarRepository carRepository,
        CarService carService,
        ServiceRepository serviceRepository
    ) {
        this.maintenanceRepository = maintenanceRepository;
        this.carRepository = carRepository;
        this.carService = carService;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/maintenances/car/{carId}")
    public List<Maintenance> getMaintenanceByCar(@PathVariable Long carId) {
        log.debug("REST request to get Maintenance  by carId : {}", carId);
        List<Maintenance> driverCars = maintenanceRepository.findByCurrentUserAndCarIdByDate(carId);
        return driverCars;
    }

    @GetMapping("/maintenances/{id}")
    public Maintenance getMaintenanceById(@PathVariable Long id) {
        log.debug("REST request to get Maintenance  by id : {}", id);
        Optional<Maintenance> maintenance = maintenanceRepository.findByCurrentUserAndMaintenanceId(id);
        if (maintenance.isPresent()) {
            return maintenance.get();
        }
        return null;
    }

    @PostMapping("/maintenances/car/{carId}")
    public ResponseEntity<Maintenance> createMaintenance(@PathVariable Long carId, @Valid @RequestBody Maintenance maintenance)
        throws URISyntaxException {
        log.debug("REST request to save Maintenance : {}", maintenance);
        if (maintenance.getId() != null) {
            throw new BadRequestAlertException("A new maintenance cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car maintenanceCar = existingCarOpt.get();
        maintenance.setCar(maintenanceCar);

        carService.updateCarOdometerByDate(maintenance.getDate(), maintenance.getOdometer(), maintenanceCar);

        Maintenance result = maintenanceRepository.save(maintenance);
        return ResponseEntity
            .created(new URI("/api/maintenances/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/maintenances/{id}")
    public ResponseEntity<Void> deleteMaintenance(@PathVariable Long id) {
        log.debug("REST request to delete Maintenance : {}", id);
        Optional<Maintenance> existingMaintenanceOpt = maintenanceRepository.findByCurrentUserAndMaintenanceId(id);
        if (!existingMaintenanceOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Maintenance not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        maintenanceRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/maintenances/{id}")
    public ResponseEntity<Maintenance> updateMaintenance(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Maintenance maintenance
    ) throws URISyntaxException {
        log.debug("REST request to update Maintenance : {}, {}", id, maintenance);
        if (maintenance.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, maintenance.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Maintenance> existingMaintenanceOpt = maintenanceRepository.findByCurrentUserAndMaintenanceId(id);
        if (!existingMaintenanceOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car maintenanceCar = existingMaintenanceOpt.get().getCar();
        maintenance.setCar(maintenanceCar);

        List<Service> currentServices = serviceRepository.findAllByMaintenance(maintenance);
        List<Long> currentServiceIds = currentServices.stream().map(Service::getId).collect(Collectors.toList());
        List<Long> serviceIds = maintenance.getServices().stream().map(Service::getId).collect(Collectors.toList());
        List<Long> serviceIdsToDelete = currentServiceIds
            .stream()
            .filter(serviceId -> !serviceIds.contains(serviceId))
            .collect(Collectors.toList());
        if (serviceIdsToDelete.size() > 0) {
            serviceRepository.deleteAllByIdIn(serviceIdsToDelete);
        }

        serviceRepository.saveAll(maintenance.getServices());
        carService.updateCarOdometerByDate(maintenance.getDate(), maintenance.getOdometer(), maintenanceCar);

        Maintenance result = maintenanceRepository.save(maintenance);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, maintenance.getId().toString()))
            .body(result);
    }
}
