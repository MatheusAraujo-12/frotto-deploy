package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.domain.Expense;
import com.localuz.domain.Inspection;
import com.localuz.domain.Tire;
import com.localuz.repository.CarRepository;
import com.localuz.repository.ExpenseRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.TireRepository;
import com.localuz.service.CarService;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
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

/** REST controller for managing {@link com.localuz.domain.Inspection}. */
@RestController
@RequestMapping("/api")
@Transactional
public class InspectionResource {

    private final Logger log = LoggerFactory.getLogger(InspectionResource.class);

    private static final String ENTITY_NAME = "inspection";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final InspectionRepository inspectionRepository;

    private final CarRepository carRepository;

    private final TireRepository tireRepository;

    private final ExpenseRepository expenseRepository;

    private final CarService carService;

    public InspectionResource(
        InspectionRepository inspectionRepository,
        CarRepository carRepository,
        TireRepository tireRepository,
        ExpenseRepository expenseRepository,
        CarService carService
    ) {
        this.inspectionRepository = inspectionRepository;
        this.carRepository = carRepository;
        this.tireRepository = tireRepository;
        this.expenseRepository = expenseRepository;
        this.carService = carService;
    }

    @GetMapping("/inspections/car/{carId}")
    public List<Inspection> getInspectionByCar(@PathVariable Long carId) {
        log.debug("REST request to get Inspection  by carId : {}", carId);
        List<Inspection> driverCars = inspectionRepository.findByCurrentUserAndCarIdByDate(carId);
        return driverCars;
    }

    @GetMapping("/inspections/{id}")
    public Inspection getInspectionById(@PathVariable Long id) {
        log.debug("REST request to get Inspection  by id : {}", id);
        Optional<Inspection> inspection = inspectionRepository.findByCurrentUserAndInspectionId(id);
        if (inspection.isPresent()) {
            return inspection.get();
        }
        return null;
    }

    @PostMapping("/inspections/car/{carId}")
    public ResponseEntity<Inspection> createInspection(@Valid @RequestBody Inspection inspection, @PathVariable Long carId)
        throws URISyntaxException {
        log.debug("REST request to save Inspection : {}", inspection);
        if (inspection.getId() != null) {
            throw new BadRequestAlertException("A new inspection cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car inspectionCar = existingCarOpt.get();
        carService.updateCarOdometerByDate(inspection.getDate(), inspection.getOdometer(), inspectionCar);
        inspection.setCar(inspectionCar);
        Inspection result = inspectionRepository.save(inspection);
        return ResponseEntity
            .created(new URI("/api/inspections/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/inspections/{id}")
    public ResponseEntity<Void> deleteInspection(@PathVariable Long id) {
        log.debug("REST request to delete Inspection : {}", id);
        Optional<Inspection> existingInspectionOpt = inspectionRepository.findByCurrentUserAndInspectionId(id);
        if (!existingInspectionOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Inspection not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        inspectionRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/inspections/{id}")
    public ResponseEntity<Inspection> updateInspection(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Inspection inspection
    ) throws URISyntaxException {
        log.debug("REST request to update Inspection : {}, {}", id, inspection);
        if (inspection.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, inspection.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        Optional<Inspection> existingInspectionOpt = inspectionRepository.findByCurrentUserAndInspectionId(id);
        if (!existingInspectionOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Inspection existingInspection = existingInspectionOpt.get();

        Car inspectionCar = existingInspection.getCar();
        carService.updateCarOdometerByDate(inspection.getDate(), inspection.getOdometer(), inspectionCar);
        inspection.setCar(inspectionCar);
        ArrayList<Tire> tires = new ArrayList<Tire>(
            Arrays.asList(
                inspection.getLeftBack(),
                inspection.getLeftFront(),
                inspection.getRightBack(),
                inspection.getRightFront(),
                inspection.getSpare()
            )
        );
        tireRepository.saveAll(tires);

        List<Expense> currentExpenses = expenseRepository.findAllByInspection(inspection);
        List<Long> currentExpenseIds = currentExpenses.stream().map(Expense::getId).collect(Collectors.toList());
        List<Long> expenseIds = inspection.getExpenses().stream().map(Expense::getId).collect(Collectors.toList());
        List<Long> expenseIdsToDelete = currentExpenseIds
            .stream()
            .filter(expenseId -> !expenseIds.contains(expenseId))
            .collect(Collectors.toList());
        if (expenseIdsToDelete.size() > 0) {
            expenseRepository.deleteAllByIdIn(expenseIdsToDelete);
        }
        expenseRepository.saveAll(inspection.getExpenses());

        for (CarBodyDamage carDamage : inspection.getCarBodyDamages()) {
            carDamage.setCar(inspectionCar);
        }

        Inspection result = inspectionRepository.save(inspection);

        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, inspection.getId().toString()))
            .body(result);
    }
}
