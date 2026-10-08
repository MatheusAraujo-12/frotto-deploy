package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.enumeration.ChecklistType;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.repository.AddressRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.service.DriverAssignmentService;
import com.localuz.service.dto.ReserveReturnResultDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

/** REST controller for managing {@link com.localuz.domain.DriverCar}. */
@RestController
@RequestMapping("/api/driver-cars")
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

    private final DriverAssignmentService driverAssignmentService;

    public DriverCarResource(
        DriverCarRepository driverCarRepository,
        CarRepository carRepository,
        AddressRepository addressRepository,
        DriverRepository driverRepository,
        DriverAssignmentService driverAssignmentService
    ) {
        this.driverAssignmentService = driverAssignmentService;
        this.driverCarRepository = driverCarRepository;
        this.carRepository = carRepository;
        this.driverRepository = driverRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/car/{carId}")
    public List<DriverCar> getDriverCarByCar(@PathVariable Long carId) {
        log.debug("REST request to get DriverCar  by carId : {}", carId);
        List<DriverCar> driverCars = driverCarRepository.findByCurrentUserAndCarIdByDate(carId);
        return driverCars;
    }

    @GetMapping("/{id}")
    public DriverCar getDriverCarById(@PathVariable Long id) {
        log.debug("REST request to get DriverCar  by id : {}", id);
        Optional<DriverCar> driverCar = driverCarRepository.findByCurrentUserAndId(id);
        if (driverCar.isPresent()) {
            return driverCar.get();
        }
        return null;
    }

    /**
     * READ COMMITTED (as /documents/{id}/finalize): after the car / driver / contract locks every read sees the latest
     * committed state, so a contract opened, concluded or restored meanwhile by another request is never missed.
     */
    @PostMapping("/car/{carId}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<DriverCar> createDriverCarByCar(
        @PathVariable Long carId,
        @RequestParam(name = "assignment", required = false) DriverAssignmentType assignment,
        @Valid @RequestBody DriverCar driverCar
    ) throws URISyntaxException {
        log.debug("REST request to save DriverCar : {}", driverCar);
        if (driverCar.getId() != null) {
            throw new BadRequestAlertException("A new driverCar cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        Car car = existingCarOpt.get();
        Driver updatedDriver = driverCar.getDriver();
        Optional<Driver> driver = updatedDriver != null && StringUtils.hasText(updatedDriver.getCpf())
            ? driverRepository.findByCpf(updatedDriver.getCpf())
            : Optional.empty();
        if (updatedDriver != null && !driver.isPresent() && updatedDriver.getId() != null && !driverRepository.findByCurrentUserAndId(updatedDriver.getId()).isPresent()) {
            // A driver id chosen by the client must be a driver of this fleet: never another account's driver.
            throw new BadRequestAlertException("Driver not found for current user", ENTITY_NAME, "drivernotfound");
        }
        Long assignedDriverId = driver.map(Driver::getId).orElse(updatedDriver == null ? null : updatedDriver.getId());
        boolean activeContract = !Boolean.TRUE.equals(driverCar.getConcluded());

        if (activeContract) {
            // Serializes concurrent assignments of this car and of this driver until the transaction ends.
            driverAssignmentService.lockForAssignment(car, assignedDriverId);
            if (driverAssignmentService.isCarOccupied(car.getId(), null)) {
                throw new BadRequestAlertException(
                    "Active Driver-Car exists, may not add another active driver",
                    ENTITY_NAME,
                    "activedriverexists"
                );
            }
        }
        com.localuz.service.VehicleLifecycleService.requireOperational(car);
        driverCar.setCar(car);
        // Server-owned state: a new contract is never suspended; it is a reserve only through the assignment below.
        driverCar.setSuspended(false);
        // Never store NULL: "not concluded" is false (NULL keeps meaning the same thing for legacy rows).
        driverCar.setConcluded(Boolean.TRUE.equals(driverCar.getConcluded()));
        driverCar.setPrimaryDriverCar(null);
        if (activeContract && assignedDriverId != null) {
            // RESERVE suspends the primary, PERMANENT concludes the open contracts. Same transaction: any failure
            // below rolls it back.
            driverCar.setPrimaryDriverCar(
                driverAssignmentService.prepareAssignment(
                    assignedDriverId,
                    car,
                    assignment,
                    driverCar.getStartDate() == null ? LocalDate.now() : driverCar.getStartDate()
                )
            );
        } else if (assignment == DriverAssignmentType.RESERVE) {
            throw new BadRequestAlertException(
                "Carro reserva exige um vínculo principal do motorista.",
                ENTITY_NAME,
                "drivercarreserverequiresprimary"
            );
        }

        if (updatedDriver != null) {
            Long existingAddressId = null;
            if (driver.isPresent()) {
                if (!activeContract) {
                    // Same driver lock as an assignment: the contacts below are decided on its latest committed row.
                    driverAssignmentService.lockForAssignment((Long) null, driver.get().getId());
                }
                applyEmergencyContacts(driver.get(), updatedDriver);
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
        }
        DriverCar result = driverCarRepository.save(driverCar);

        return ResponseEntity
            .created(new URI("/api/driver-cars/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDriverCarById(@PathVariable Long id) {
        log.debug("REST request to delete DriverCar : {}", id);
        Optional<DriverCar> carBodyDamage = driverCarRepository.findByCurrentUserAndId(id);
        if (!carBodyDamage.isPresent()) {
            throw new BadRequestAlertException("DriverCar not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        com.localuz.service.VehicleLifecycleService.requireOperational(carBodyDamage.get().getCar());
        if (driverAssignmentService.hasPendencies(id)) {
            // pendency.driver_car_id has no database FK: deleting the contract would orphan its debts.
            throw new BadRequestAlertException(
                "O vínculo possui pendências e não pode ser excluído. Encerre o vínculo.",
                ENTITY_NAME,
                "drivercarhaspendencies"
            );
        }
        DriverCar contract = carBodyDamage.get();
        if (
            Boolean.TRUE.equals(contract.getSuspended()) ||
            (contract.isReserve() && !Boolean.TRUE.equals(contract.getConcluded())) ||
            driverAssignmentService.isPrimaryOfReserves(id)
        ) {
            // The primary/reserve link must stay: return the reserve (or conclude the contract) instead.
            throw new BadRequestAlertException(
                "O vínculo está suspenso ou ligado a um carro reserva e não pode ser excluído.",
                ENTITY_NAME,
                "drivercarhasreserves"
            );
        }
        try {
            driverCarRepository.deleteById(id);
            driverCarRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Checklists / inspections of the contract reference it (RESTRICT): their trace keeps the contract.
            throw new BadRequestAlertException(
                "O vínculo possui checklist de entrega/devolução ou inspeção e não pode ser excluído. Encerre o vínculo.",
                ENTITY_NAME,
                "drivercarhaschecklists"
            );
        }
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/{id}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        Optional<DriverCar> existingCarOpt = driverCarRepository.findByCurrentUserAndId(id);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        // The edit form was read before: the contract is decided and saved on its locked, latest committed row.
        DriverCar existingDriverCar = existingCarOpt.get();
        driverAssignmentService.lockContract(existingDriverCar);
        boolean wasOpen = !Boolean.TRUE.equals(existingDriverCar.getConcluded());
        boolean staysOrBecomesOpen = !Boolean.TRUE.equals(driverCar.getConcluded());
        // A suspended primary does not occupy its car: editing it never conflicts with whoever drives the car now.
        if (
            staysOrBecomesOpen &&
            existingDriverCar.getCar() != null &&
            !Boolean.TRUE.equals(existingDriverCar.getSuspended()) &&
            driverAssignmentService.isCarOccupied(existingDriverCar.getCar().getId(), existingDriverCar.getId())
        ) {
            throw new BadRequestAlertException(
                "Active Driver-Car exists, may not add another active driver",
                ENTITY_NAME,
                "activedriverexists"
            );
        }

        com.localuz.service.VehicleLifecycleService.requireOperational(existingDriverCar.getCar());

        Driver updatedDriver = driverCar.getDriver();
        requireSameDriver(existingDriverCar.getDriver(), updatedDriver);
        if (!wasOpen && staysOrBecomesOpen) {
            if (driverCarRepository.hasFinalChecklist(existingDriverCar.getId(), ChecklistType.DEVOLUCAO)) {
                // Its finalized Devolução records the return: reopening would contradict it (and block the next one).
                throw new BadRequestAlertException(
                    "Este vínculo foi encerrado pelo checklist de devolução finalizado e não pode ser reaberto. Cadastre um novo vínculo.",
                    ENTITY_NAME,
                    "drivercarreturnedbychecklist"
                );
            }
            if (existingDriverCar.isReserve()) {
                // A returned reserve is history; a new reserve is created through the assignment.
                throw new BadRequestAlertException(
                    "Um carro reserva devolvido não pode ser reaberto.",
                    ENTITY_NAME,
                    "drivercarreservecannotreopen"
                );
            }
            if (existingDriverCar.getDriver() != null) {
                // Re-opening a concluded primary never leaves the driver with two open contracts.
                Long driverId = existingDriverCar.getDriver().getId();
                driverAssignmentService.lockForAssignment(existingDriverCar.getCar(), driverId);
                if (driverAssignmentService.hasOpenContractElsewhere(driverId, existingDriverCar.getId())) {
                    throw new BadRequestAlertException(
                        "O motorista já possui um vínculo ativo em outro veículo.",
                        ENTITY_NAME,
                        "driveractiveelsewhere"
                    );
                }
            }
        }
        if (updatedDriver != null) {
            Driver existingDriver = existingDriverCar.getDriver();
            if (updatedDriver.getId() == null && existingDriver != null) {
                updatedDriver.setId(existingDriver.getId());
            }
            if (existingDriver != null && Objects.equals(existingDriver.getId(), updatedDriver.getId())) {
                applyEmergencyContacts(existingDriver, updatedDriver);
            }
            if (updatedDriver.getAddress() != null) {
                boolean shouldReuseAddressId =
                    updatedDriver.getAddress().getId() == null && existingDriver != null && existingDriver.getAddress() != null;
                if (shouldReuseAddressId) {
                    updatedDriver.getAddress().setId(existingDriver.getAddress().getId());
                }
                addressRepository.save(updatedDriver.getAddress());
            }
            existingDriverCar.setDriver(driverRepository.save(updatedDriver));
        } else {
            existingDriverCar.setDriver(null);
        }

        existingDriverCar.setStartDate(driverCar.getStartDate());
        existingDriverCar.setEndDate(driverCar.getEndDate());
        existingDriverCar.setWarranty(driverCar.getWarranty());
        existingDriverCar.setScore(driverCar.getScore());
        existingDriverCar.setDebt(driverCar.getDebt());
        existingDriverCar.setConcluded(Boolean.TRUE.equals(driverCar.getConcluded()));
        existingDriverCar.setContractNumber(driverCar.getContractNumber());
        boolean concludedNow = wasOpen && Boolean.TRUE.equals(existingDriverCar.getConcluded());
        if (concludedNow) {
            // A concluded contract is never suspended.
            existingDriverCar.setSuspended(false);
        }

        DriverCar result = driverCarRepository.save(existingDriverCar);
        if (concludedNow && result.isReserve()) {
            // Concluding a reserve is returning it: same rules as POST /return (the primary comes back only if safe).
            driverAssignmentService.restoreAfterReserve(result);
        }
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, driverCar.getId().toString()))
            .body(result);
    }

    /**
     * Returns a reserve car: the reserve is concluded and the same primary contract goes back to ACTIVE when its car
     * is free; otherwise the primary stays SUSPENDED and the outcome says why (nobody is ever removed from a car).
     */
    @PostMapping("/{id}/return")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReserveReturnResultDTO returnReserveCar(
        @PathVariable Long id,
        @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.debug("REST request to return reserve DriverCar : {}", id);
        DriverCar reserve = driverCarRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new BadRequestAlertException("DriverCar not found for current user", ENTITY_NAME, "notcurrentuser"));
        com.localuz.service.VehicleLifecycleService.requireOperational(reserve.getCar());
        return driverAssignmentService.returnReserve(reserve, endDate == null ? LocalDate.now() : endDate);
    }

    /** Restores a SUSPENDED primary contract once its car is free (e.g. after a return conflict was resolved). */
    @PostMapping("/{id}/restore")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DriverCar restorePrimaryContract(@PathVariable Long id) {
        log.debug("REST request to restore suspended DriverCar : {}", id);
        DriverCar primary = driverCarRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new BadRequestAlertException("DriverCar not found for current user", ENTITY_NAME, "notcurrentuser"));
        com.localuz.service.VehicleLifecycleService.requireOperational(primary.getCar());
        return driverAssignmentService.restorePrimary(primary);
    }

    /**
     * Emergency contacts of an existing driver sent by the vínculo form, decided against the driver's current row
     * (locked): a contact the user did not touch keeps its current value - e.g. one filled meanwhile by a checklist de
     * Entrega - and an edited one is applied only if the current value is still the one the form loaded; if both
     * changed, the edit is refused (the user reloads and sees the current contacts). A request without the loaded
     * values (an older client) only fills empty contacts: stale form data never overwrites the current ones.
     */
    static void applyEmergencyContacts(Driver current, Driver submitted) {
        java.util.List<String> loaded = submitted.getLoadedEmergencyContacts();
        submitted.setEmergencyContact(mergeContact(current.getEmergencyContact(), submitted.getEmergencyContact(), loaded, 0));
        submitted.setEmergencyContactSecond(
            mergeContact(current.getEmergencyContactSecond(), submitted.getEmergencyContactSecond(), loaded, 1)
        );
    }

    private static String mergeContact(String current, String submitted, java.util.List<String> loaded, int index) {
        String now = blankToNull(current);
        String wanted = blankToNull(submitted);
        if (loaded == null) {
            return now == null ? submitted : current;
        }
        String seen = loaded.size() > index ? blankToNull(loaded.get(index)) : null;
        if (Objects.equals(wanted, seen) || Objects.equals(wanted, now)) {
            return current;
        }
        if (Objects.equals(now, seen)) {
            return submitted;
        }
        throw new BadRequestAlertException(
            "Os contatos de emergência do motorista foram alterados enquanto este formulário estava aberto (por exemplo, por um checklist de entrega). Feche e abra o vínculo novamente para editá-los.",
            ENTITY_NAME,
            "driveremergencycontactschanged"
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * A driver_car is the contract of one person: its pendencies, warranty, score and period belong to that driver.
     * Editing it may update that same driver's data (same id, or no id), never put another person in it - another
     * driver of the car needs a new driver_car. Identity is the driver id; a CPF only counts when it is the CPF of
     * another driver of this account (the edit form switches to that driver when such a CPF is typed).
     */
    private void requireSameDriver(Driver existingDriver, Driver requestedDriver) {
        if (existingDriver == null) {
            return;
        }
        boolean otherPerson = requestedDriver == null || (requestedDriver.getId() != null && !requestedDriver.getId().equals(existingDriver.getId()));
        String requestedCpf = requestedDriver == null || requestedDriver.getCpf() == null ? "" : requestedDriver.getCpf().trim();
        String currentCpf = existingDriver.getCpf() == null ? "" : existingDriver.getCpf().trim();
        if (!otherPerson && !requestedCpf.isEmpty() && !requestedCpf.equals(currentCpf)) {
            otherPerson =
                driverRepository
                    .findAllByCurrentUserAndCpf(requestedCpf)
                    .stream()
                    .anyMatch(found -> !found.getId().equals(existingDriver.getId()));
        }
        if (otherPerson) {
            throw new BadRequestAlertException(
                "O motorista de um vínculo existente não pode ser trocado. Encerre este vínculo e cadastre um novo para o outro motorista.",
                ENTITY_NAME,
                "drivercarchangeforbidden"
            );
        }
    }
}
