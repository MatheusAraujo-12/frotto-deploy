package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarExpense;
import com.localuz.repository.CarExpenseRepository;
import com.localuz.repository.CarRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

/** REST controller for managing {@link com.localuz.domain.CarExpense}. */
@RestController
@RequestMapping("/api")
@Transactional
public class CarExpenseResource {

    private final Logger log = LoggerFactory.getLogger(CarExpenseResource.class);

    private static final String ENTITY_NAME = "carExpense";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CarExpenseRepository carExpenseRepository;

    private final CarRepository carRepository;

    public CarExpenseResource(CarExpenseRepository carExpenseRepository, CarRepository carRepository) {
        this.carExpenseRepository = carExpenseRepository;
        this.carRepository = carRepository;
    }

    @GetMapping("/car-expenses/car/{carId}")
    public List<CarExpense> getCarExpenseByCar(@PathVariable Long carId) {
        log.debug("REST request to get CarExpense  by carId : {}", carId);
        List<CarExpense> carExpenses = carExpenseRepository.findByCurrentUserAndCarIdByDate(carId);
        return carExpenses;
    }

    @GetMapping("/car-expenses/{id}")
    public CarExpense getCarExpenseById(@PathVariable Long id) {
        log.debug("REST request to get CarExpense  by id : {}", id);
        Optional<CarExpense> carExpense = carExpenseRepository.findByCurrentUserAndCarExpenseId(id);
        if (carExpense.isPresent()) {
            return carExpense.get();
        }
        return null;
    }

    @PostMapping("/car-expenses/car/{carId}")
    public ResponseEntity<CarExpense> createCarExpense(@PathVariable Long carId, @Valid @RequestBody CarExpense carExpense)
        throws URISyntaxException {
        log.debug("REST request to save CarExpense : {}", carExpense);
        if (carExpense.getId() != null) {
            throw new BadRequestAlertException("A new carExpense cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car carExpenseCar = existingCarOpt.get();
        carExpense.setCar(carExpenseCar);

        CarExpense result = carExpenseRepository.save(carExpense);
        return ResponseEntity
            .created(new URI("/api/carExpenses/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/car-expenses/{id}")
    public ResponseEntity<Void> deleteCarExpense(@PathVariable Long id) {
        log.debug("REST request to delete CarExpense : {}", id);
        Optional<CarExpense> existingCarExpenseOpt = carExpenseRepository.findByCurrentUserAndCarExpenseId(id);
        if (!existingCarExpenseOpt.isPresent()) {
            throw new BadRequestAlertException("Car-CarExpense not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        carExpenseRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/car-expenses/{id}")
    public ResponseEntity<CarExpense> updateCarExpense(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody CarExpense carExpense
    ) throws URISyntaxException {
        log.debug("REST request to update CarExpense : {}, {}", id, carExpense);
        if (carExpense.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, carExpense.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<CarExpense> existingCarExpenseOpt = carExpenseRepository.findByCurrentUserAndCarExpenseId(id);
        if (!existingCarExpenseOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car carExpenseCar = existingCarExpenseOpt.get().getCar();
        carExpense.setCar(carExpenseCar);

        CarExpense result = carExpenseRepository.save(carExpense);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, carExpense.getId().toString()))
            .body(result);
    }

    @PostMapping("/car-expenses/car-all")
    public ResponseEntity<List<CarExpense>> createCarExpenseForAllActiveCars(@Valid @RequestBody CarExpense carExpense)
        throws URISyntaxException {
        log.debug("REST request to save CarExpense for all active cars : {}", carExpense);
        if (carExpense.getId() != null) {
            throw new BadRequestAlertException("A new carExpense cannot already have an ID", ENTITY_NAME, "idexists");
        }
        List<Car> activeCars = carRepository.findActiveByCurrentUser();
        if (activeCars == null || activeCars.size() < 1) {
            throw new BadRequestAlertException("No active cars found for this user", ENTITY_NAME, "noActivaCars");
        }
        List<CarExpense> carExpenses = new ArrayList<>();

        for (Car activeCar : activeCars) {
            CarExpense newCarExp = new CarExpense();
            newCarExp.setCar(activeCar);
            newCarExp.setCost(carExpense.getCost());
            newCarExp.setDate(carExpense.getDate());
            newCarExp.setName(carExpense.getName());
            carExpenses.add(newCarExp);
        }
        List<CarExpense> result = carExpenseRepository.saveAll(carExpenses);

        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, String.valueOf(result.size())))
            .body(result);
    }
}
