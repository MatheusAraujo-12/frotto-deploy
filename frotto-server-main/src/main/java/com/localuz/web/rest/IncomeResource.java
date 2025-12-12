package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.Income;
import com.localuz.repository.CarRepository;
import com.localuz.repository.IncomeRepository;
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

/** REST controller for managing {@link com.localuz.domain.Income}. */
@RestController
@RequestMapping("/api")
@Transactional
public class IncomeResource {

    private final Logger log = LoggerFactory.getLogger(IncomeResource.class);

    private static final String ENTITY_NAME = "income";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final IncomeRepository incomeRepository;

    private final CarRepository carRepository;

    public IncomeResource(IncomeRepository incomeRepository, CarRepository carRepository) {
        this.incomeRepository = incomeRepository;
        this.carRepository = carRepository;
    }

    @GetMapping("/incomes/car/{carId}")
    public List<Income> getIncomeByCar(@PathVariable Long carId) {
        log.debug("REST request to get Income  by carId : {}", carId);
        List<Income> incomes = incomeRepository.findByCurrentUserAndCarIdByDate(carId);
        return incomes;
    }

    @GetMapping("/incomes/{id}")
    public Income getIncomeById(@PathVariable Long id) {
        log.debug("REST request to get Income  by id : {}", id);
        Optional<Income> income = incomeRepository.findByCurrentUserAndIncomeId(id);
        if (income.isPresent()) {
            return income.get();
        }
        return null;
    }

    @PostMapping("/incomes/car/{carId}")
    public ResponseEntity<Income> createIncome(@PathVariable Long carId, @Valid @RequestBody Income income) throws URISyntaxException {
        log.debug("REST request to save Income : {}", income);
        if (income.getId() != null) {
            throw new BadRequestAlertException("A new income cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car incomeCar = existingCarOpt.get();
        income.setCar(incomeCar);

        Income result = incomeRepository.save(income);
        return ResponseEntity
            .created(new URI("/api/incomes/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/incomes/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable Long id) {
        log.debug("REST request to delete Income : {}", id);
        Optional<Income> existingIncomeOpt = incomeRepository.findByCurrentUserAndIncomeId(id);
        if (!existingIncomeOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Income not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        incomeRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/incomes/{id}")
    public ResponseEntity<Income> updateIncome(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Income income
    ) throws URISyntaxException {
        log.debug("REST request to update Income : {}, {}", id, income);
        if (income.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, income.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Income> existingIncomeOpt = incomeRepository.findByCurrentUserAndIncomeId(id);
        if (!existingIncomeOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car incomeCar = existingIncomeOpt.get().getCar();
        income.setCar(incomeCar);

        Income result = incomeRepository.save(income);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, income.getId().toString()))
            .body(result);
    }
}
