package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.repository.AddressRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
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

/** REST controller for managing {@link com.localuz.domain.DriverCar}. */
@RestController
@RequestMapping("/api")
@Transactional
public class DriverCarResource {

    private final Logger log = LoggerFactory.getLogger(DriverCarResource.class);

    private static final String ENTITY_NAME = "driverCar";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DriverCarRepository driverCarRepository;

    private final CarRepository carRepository;

    private final DriverRepository driverRepository;

    private final AddressRepository addressRepository;

    public DriverCarResource(
        DriverCarRepository driverCarRepository,
        CarRepository carRepository,
        AddressRepository addressRepository,
        DriverRepository driverRepository
    ) {
        this.driverCarRepository = driverCarRepository;
        this.carRepository = carRepository;
        this.driverRepository = driverRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/driver-cars/car/{carId}")
    public List<DriverCar> getDriverCarByCar(@PathVariable Long carId) {
        log.debug("REST request to get DriverCar  by carId : {}", carId);
        List<DriverCar> driverCars = driverCarRepository.findByCurrentUserAndCarIdByDate(carId);
        return driverCars;
    }

    @GetMapping("/driver-cars/{id}")
    public DriverCar getDriverCarById(@PathVariable Long id) {
        log.debug("REST request to get DriverCar  by id : {}", id);
        Optional<DriverCar> driverCar = driverCarRepository.findByCurrentUserAndId(id);
        if (driverCar.isPresent()) {
            return driverCar.get();
        }
        return null;
    }

    @PostMapping("/driver-cars/car/{carId}")
    public ResponseEntity<DriverCar> createDriverCarByCar(@PathVariable Long carId, @Valid @RequestBody DriverCar driverCar)
        throws URISyntaxException {
        log.debug("REST request to save DriverCar : {}", driverCar);
        if (driverCar.getId() != null) {
            throw new BadRequestAlertException("A new driverCar cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (driverCar.getDriver() == null) {
            throw new BadRequestAlertException("Driver is empty", ENTITY_NAME, "driverempty");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        if (driverCar.getConcluded() == null || driverCar.getConcluded() == false) {
            DriverCar driverCarActive = driverCarRepository.findFirstByConcludedAndCar(false, existingCarOpt.get());
            if (driverCarActive != null) {
                throw new BadRequestAlertException(
                    "Active Driver-Car exists, may not add another active driver",
                    ENTITY_NAME,
                    "activedriverexists"
                );
            }
        }
        driverCar.setCar(existingCarOpt.get());

        Optional<Driver> driver = driverRepository.findByCpf(driverCar.getDriver().getCpf());
        Driver updatedDriver = driverCar.getDriver();
        Long existingAddressId = null;
        if (driver.isPresent()) {
            updatedDriver.setId(driver.get().getId());
            if (driver.get().getAddress() != null) {
                existingAddressId = driver.get().getAddress().getId();
            }
        }
        if (updatedDriver.getAddress() != null) {
            if (existingAddressId != null) {
                updatedDriver.getAddress().setId(existingAddressId);
            }
            addressRepository.save(updatedDriver.getAddress());
        }

        Driver savedDriver = driverRepository.save(updatedDriver);
        driverCar.setDriver(savedDriver);
        DriverCar result = driverCarRepository.save(driverCar);

        return ResponseEntity
            .created(new URI("/api/driver-cars/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/driver-cars/{id}")
    public ResponseEntity<Void> deleteDriverCarById(@PathVariable Long id) {
        log.debug("REST request to delete DriverCar : {}", id);
        Optional<DriverCar> carBodyDamage = driverCarRepository.findByCurrentUserAndId(id);
        if (!carBodyDamage.isPresent()) {
            throw new BadRequestAlertException("DriverCar not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        driverCarRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/driver-cars/{id}")
    public ResponseEntity<DriverCar> updateDriverCarById(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody DriverCar driverCar
    ) {
        log.debug("REST request to update DriverCar : {}, {}", id, driverCar);
        if (driverCar.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, driverCar.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (driverCar.getDriver() == null || driverCar.getDriver().getId() == null) {
            throw new BadRequestAlertException("Driver is empty, no id provided", ENTITY_NAME, "driverempty");
        }
        if (driverCar.getDriver().getAddress() == null || driverCar.getDriver().getAddress().getId() == null) {
            throw new BadRequestAlertException("Driver Address is empty, no id provided", ENTITY_NAME, "driverempty");
        }
        Optional<DriverCar> existingCarOpt = driverCarRepository.findByCurrentUserAndId(id);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        if (driverCar.getConcluded() == null || driverCar.getConcluded() == false) {
            DriverCar driverCarActive = driverCarRepository.findFirstByConcludedAndCar(false, existingCarOpt.get().getCar());
            if (driverCarActive != null && !driverCarActive.getId().equals(driverCar.getId())) {
                throw new BadRequestAlertException(
                    "Active Driver-Car exists, may not add another active driver",
                    ENTITY_NAME,
                    "activedriverexists"
                );
            }
        }

        driverCar.setCar(existingCarOpt.get().getCar());

        addressRepository.save(driverCar.getDriver().getAddress());
        driverRepository.save(driverCar.getDriver());
        DriverCar result = driverCarRepository.save(driverCar);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, driverCar.getId().toString()))
            .body(result);
    }
}
