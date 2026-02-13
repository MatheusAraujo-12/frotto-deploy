package com.localuz.web.rest;

import com.localuz.DTO.CarDriverDto;
import com.localuz.domain.Car;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Inspection;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Reminder;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.CommissionType;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.repository.ReminderRepository;
import com.localuz.service.UserService;
import com.localuz.service.dto.CarDTO;
import com.localuz.service.dto.CarFormDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/** REST controller for managing {@link com.localuz.domain.Car}. */
@RestController
@RequestMapping("/api")
@Transactional
public class CarResource {

    private final Logger log = LoggerFactory.getLogger(CarResource.class);

    private static final String ENTITY_NAME = "car";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CarRepository carRepository;
    private final DriverCarRepository driverCarRepository;
    private final InspectionRepository inspectionRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final ReminderRepository reminderRepository;

    private final UserService userService;

    public CarResource(
        CarRepository carRepository,
        UserService userService,
        DriverCarRepository driverCarRepository,
        InspectionRepository inspectionRepository,
        ReminderRepository reminderRepository,
        MaintenanceRepository maintenanceRepository
    ) {
        this.carRepository = carRepository;
        this.userService = userService;
        this.driverCarRepository = driverCarRepository;
        this.inspectionRepository = inspectionRepository;
        this.reminderRepository = reminderRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping("/cars")
    public List<Car> getAllCarsByUser() {
        log.debug("REST request to get all Cars by current User");
        return carRepository.findByCurrentUser();
    }

    @GetMapping("/cars-drivers/active")
    public List<CarDriverDto> getAllCarsByUserAndDrivers() {
        log.debug("REST request to get all Active Cars and Drivers by current User");
        return carRepository.findActiveByCurrentUserAndDriver();
    }

    @GetMapping("/cars/active")
    public List<Car> getAllActiveCarsByUser() {
        log.debug("REST request to get all Active Cars by current User");
        return carRepository.findActiveByCurrentUser();
    }

    @GetMapping("/cars/active/groups")
    public List<String> getAllActiveCarGroupsByUser() {
        log.debug("REST request to get all Active Car Groups by current User");
        List<String> groups = carRepository.findActiveGroupsByCurrentUser();
        groups.removeAll(Arrays.asList("", null));
        return groups;
    }

    @GetMapping("/cars/{id}")
    public CarDTO getCarById(@PathVariable Long id) {
        log.debug("REST request to get Car by id");
        Optional<Car> existingCar = carRepository.findByCurrentUserAndId(id);

        if (existingCar.isPresent()) {
            Car car = existingCar.get();
            CarDTO carDto = new CarDTO();
            carDto.setCar(car);
            DriverCar activeDriver = driverCarRepository.findFirstByConcludedAndCar(false, car);
            carDto.setActiveDriver(activeDriver);
            Inspection lastInspection = inspectionRepository.findFirstByCarOrderByDateDesc(car);
            carDto.setLastInspection(lastInspection);
            Maintenance lastMaintenance = maintenanceRepository.findFirstByCarOrderByDateDesc(car);
            carDto.setLastMaintenance(lastMaintenance);
            //      List<Reminder> reminders = reminderRepository.findByCurrentUserAndCarId(car.getId());
            //      carDto.setReminders(reminders);

            return carDto;
        }
        return null;
    }

    @PostMapping(value = "/cars", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Car> createCar(@Valid @RequestBody Car car) throws URISyntaxException {
        return createCarInternal(car);
    }

    @PostMapping(value = "/cars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Car> createCarMultipart(@ModelAttribute CarFormDTO carForm) throws URISyntaxException {
        return createCarInternal(carForm.toCar());
    }

    @DeleteMapping("/cars/{id}")
    public ResponseEntity<Void> softDeleteCar(@PathVariable Long id) {
        log.debug("REST request to delete Car : {}", id);
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(id);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car existingCar = existingCarOpt.get();
        existingCar.setActive(false);
        carRepository.save(existingCar);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PatchMapping(value = "/cars/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Car> partialUpdateCar(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Car car
    ) {
        return partialUpdateCarInternal(id, car);
    }

    @PatchMapping(value = "/cars/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Car> partialUpdateCarMultipart(
        @PathVariable(value = "id", required = false) final Long id,
        @ModelAttribute CarFormDTO carForm
    ) {
        Car car = carForm.toCar();
        if (car.getId() == null) {
            car.setId(id);
        }
        return partialUpdateCarInternal(id, car);
    }

    @GetMapping("/admin/cars")
    public List<Car> getAllCars() {
        log.debug("REST request to get all Cars");
        return carRepository.findAll();
    }

    @GetMapping("admin/cars/user/{userId}")
    public List<Car> getAllCarsByUserId(@PathVariable Long userId) {
        log.debug("REST request to get all Cars by User Id");
        return carRepository.findAllByUserId(userId);
    }

    private ResponseEntity<Car> createCarInternal(Car car) throws URISyntaxException {
        log.debug("REST request to save Car : {}", car);
        Optional<User> currentUser = userService.getUserWithAuthorities();
        if (car.getId() != null) {
            throw new BadRequestAlertException("A new car cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (!currentUser.isPresent()) {
            throw new BadRequestAlertException("A new car cannot have an empty User", ENTITY_NAME, "emptyuser");
        }
        applyCommissionDefaults(car);
        if (car.getCommissionType() == null) {
            car.setCommissionType(CommissionType.PERCENT_PROFIT);
        }
        validateCommission(car);
        car.setUser(currentUser.get());
        car.setActive(true);
        Car result = carRepository.save(car);
        return ResponseEntity
            .created(new URI("/api/cars/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    private ResponseEntity<Car> partialUpdateCarInternal(Long id, Car car) {
        log.debug("REST request to partial update Car partially : {}, {}", id, car);
        if (car.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, car.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(id);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car existingCar = existingCarOpt.get();
        applyPartialUpdates(existingCar, car);
        applyCommissionDefaults(existingCar);
        validateCommission(existingCar);
        carRepository.save(existingCar);

        return ResponseUtil.wrapOrNotFound(
            existingCarOpt,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, car.getId().toString())
        );
    }

    private void applyPartialUpdates(Car existingCar, Car car) {
        if (car.getName() != null) {
            existingCar.setName(car.getName());
        }
        if (car.getModel() != null) {
            existingCar.setModel(car.getModel());
        }
        if (car.getColor() != null) {
            existingCar.setColor(car.getColor());
        }
        if (car.getPlate() != null) {
            existingCar.setPlate(car.getPlate());
        }
        if (car.getOdometer() != null) {
            existingCar.setOdometer(car.getOdometer());
        }
        if (car.getCommissionPercent() != null) {
            existingCar.setCommissionPercent(car.getCommissionPercent());
        }
        if (car.getCommissionType() != null) {
            existingCar.setCommissionType(car.getCommissionType());
        }
        if (car.getCommissionFixed() != null) {
            existingCar.setCommissionFixed(car.getCommissionFixed());
        }
        if (car.getInitialValue() != null) {
            existingCar.setInitialValue(car.getInitialValue());
        }
        if (car.getYear() != null) {
            existingCar.setYear(car.getYear());
        }
        if (car.getGroup() != null) {
            existingCar.setGroup(car.getGroup());
        }
        if (car.getActive() != null) {
            existingCar.setActive(car.getActive());
        }
    }

    private void applyCommissionDefaults(Car car) {
        if (car == null || car.getCommissionType() != null) {
            return;
        }
        if (car.getCommissionFixed() != null) {
            car.setCommissionType(CommissionType.FIXED);
            return;
        }
        if (car.getCommissionPercent() != null) {
            car.setCommissionType(CommissionType.PERCENT_PROFIT);
        }
    }

    private void validateCommission(Car car) {
        if (car == null || car.getCommissionType() == null) {
            return;
        }
        if (car.getCommissionType() == CommissionType.FIXED) {
            if (car.getCommissionFixed() == null) {
                throw new BadRequestAlertException(
                    "Commission fixed is required when commissionType is FIXED",
                    ENTITY_NAME,
                    "commissionfixedrequired"
                );
            }
            if (car.getCommissionFixed().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestAlertException(
                    "Commission fixed must be >= 0",
                    ENTITY_NAME,
                    "commissionfixedinvalid"
                );
            }
            return;
        }
        if (car.getCommissionPercent() == null) {
            throw new BadRequestAlertException(
                "Commission percent is required when commissionType is PERCENT_PROFIT",
                ENTITY_NAME,
                "commissionpercentrequired"
            );
        }
        if (car.getCommissionPercent() < 0) {
            throw new BadRequestAlertException(
                "Commission percent must be >= 0",
                ENTITY_NAME,
                "commissionpercentinvalid"
            );
        }
    }
}
