package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.dto.ReserveReturnResultDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.DriverAssignmentConflictException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational assignment of drivers to cars.
 *
 * <p>A contract (driver_car) is ACTIVE (operational), SUSPENDED (a primary whose driver is on a reserve car) or
 * CONCLUDED. A reserve contract references the primary contract it temporarily replaces. A driver operates at most
 * one car and a car is operated by at most one driver; putting a driver who has an open contract in another car
 * requires the caller's explicit choice: RESERVE (the primary is suspended, then restored on return - the same row,
 * never a new one) or PERMANENT (every open contract is concluded, the new one is the primary).
 *
 * <p>Contracts are history: their pendencies stay on them (origin) and with their frozen debtor; nothing here ever
 * touches a pendency. Every change that makes a contract operational runs under the car lock, then the driver lock,
 * and decides with counts read from the database.
 *
 * <p>Callers run in READ COMMITTED (the DriverCar routes and /documents/{id}/finalize declare it): the counts read after
 * the locks must see what the previous holder of the locks committed - a REPEATABLE READ snapshot taken before them
 * would not. Rows loaded before the locks are re-read under them ({@link #lockContract}).
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DriverAssignmentService {

    private static final String ENTITY_NAME = "driverCar";
    private static final long NONE = -1L;

    private final DriverCarRepository driverCarRepository;
    private final DriverRepository driverRepository;
    private final CarRepository carRepository;
    private final PendencyRepository pendencyRepository;
    private final EntityManager entityManager;

    public DriverAssignmentService(
        DriverCarRepository driverCarRepository,
        DriverRepository driverRepository,
        CarRepository carRepository,
        PendencyRepository pendencyRepository,
        EntityManager entityManager
    ) {
        this.driverCarRepository = driverCarRepository;
        this.driverRepository = driverRepository;
        this.carRepository = carRepository;
        this.pendencyRepository = pendencyRepository;
        this.entityManager = entityManager;
    }

    /**
     * Serializes concurrent assignments of the same car or driver until the transaction ends. Always the car first,
     * then the driver: two requests never wait on each other in opposite order.
     */
    public void lockForAssignment(Car car, Long driverId) {
        lockForAssignment(car.getId(), driverId);
    }

    public void lockForAssignment(Long carId, Long driverId) {
        if (carId != null) {
            lockRow(Car.class, carId, () -> carRepository.findByIdForUpdate(carId));
        }
        if (driverId != null) {
            lockRow(Driver.class, driverId, () -> driverRepository.findByIdForUpdate(driverId));
        }
    }

    /**
     * Locks everything an operation on this contract may change, always in the domain order: the cars (ascending id),
     * then the driver, then the contract rows. A reserve also takes its primary's car and row up front - returning it
     * may restore the primary - so a return never waits for a car while holding the driver (the order of /restore,
     * POST / PUT and the checklist: no lock cycle). The caller read the contract before the locks: it is re-read under
     * them, so every decision (open, suspended, concluded) is taken on the latest committed rows and the save never
     * writes back what another request changed.
     */
    public void lockContract(DriverCar contract) {
        Long primaryId = contract.getPrimaryDriverCarId();
        Long carId = contract.getCar() == null ? null : contract.getCar().getId();
        Long primaryCarId = primaryId == null ? null : driverCarRepository.findCarIdById(primaryId);
        java.util.stream.Stream.of(carId, primaryCarId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .sorted()
            .forEach(id -> lockRow(Car.class, id, () -> carRepository.findByIdForUpdate(id)));
        lockForAssignment((Long) null, contract.getDriver() == null ? null : contract.getDriver().getId());
        lockRow(DriverCar.class, contract.getId(), () -> driverCarRepository.findByIdForUpdate(contract.getId()));
        if (primaryId != null) {
            lockRow(DriverCar.class, primaryId, () -> driverCarRepository.findByIdForUpdate(primaryId));
        }
    }

    /**
     * Only the contract row (locked and re-read), for a caller that already holds the car and driver locks of the
     * contract and changes nothing else (a delivery registered on an existing contract): keeps the domain order.
     */
    public void lockContractRow(DriverCar contract) {
        lockRow(DriverCar.class, contract.getId(), () -> driverCarRepository.findByIdForUpdate(contract.getId()));
    }

    /**
     * SELECT ... FOR UPDATE that also refreshes the managed instance: an entity loaded before the lock (with the
     * document, the contract, the route's first read) is never decided on nor saved with its stale state. Once locked in
     * this transaction the row is not re-read again (pending changes stay). A row that does not exist has nothing to lock.
     */
    private void lockRow(Class<?> type, Long id, Runnable lockingQuery) {
        try {
            Object entity = entityManager.find(type, id);
            if (entity == null) {
                lockingQuery.run();
                return;
            }
            if (entityManager.getLockMode(entity) != LockModeType.PESSIMISTIC_WRITE) {
                entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
            }
        } catch (PersistenceException e) {
            // A lock timeout / deadlock victim: the same Spring exception a repository would throw (409, retry).
            DataAccessException translated = EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(e);
            throw translated != null ? translated : e;
        }
    }

    /** True when another driver is operating the car (suspended primaries do not occupy it). */
    public boolean isCarOccupied(Long carId, Long exceptDriverCarId) {
        return driverCarRepository.countOperationalOnCar(carId, exceptDriverCarId == null ? NONE : exceptDriverCarId) > 0;
    }

    /**
     * New open contract of an existing driver on {@code targetCar} (locks already held, car checked free). Applies the
     * caller's explicit choice to the driver's open contracts and returns the primary the new contract must reference
     * when it is a reserve, or null when the new contract is a normal primary.
     */
    public DriverCar prepareAssignment(Long driverId, Car targetCar, DriverAssignmentType type, LocalDate date) {
        List<DriverCar> open = driverCarRepository.findOpenByCurrentUserAndDriver(driverId);
        if (open.isEmpty()) {
            if (type == DriverAssignmentType.RESERVE) {
                throw new BadRequestAlertException(
                    "Carro reserva exige um vínculo principal do motorista.",
                    ENTITY_NAME,
                    "drivercarreserverequiresprimary"
                );
            }
            return null;
        }
        if (open.stream().anyMatch(contract -> contract.getCar() != null && contract.getCar().getId().equals(targetCar.getId()))) {
            throw new BadRequestAlertException(
                "O motorista já possui um vínculo aberto neste veículo.",
                ENTITY_NAME,
                "driverhasopencontractoncar"
            );
        }
        if (type == null) {
            DriverCar current = open.stream().filter(DriverAssignmentService::isOperational).findFirst().orElse(open.get(0));
            throw new DriverAssignmentConflictException(
                DriverAssignmentConflictException.ASSIGNMENT_REQUIRED,
                "O motorista já possui vínculo em outro veículo: informe carro reserva (RESERVE) ou transferência definitiva (PERMANENT).",
                current.getId(),
                current.getCar() == null ? null : current.getCar().getPlate()
            );
        }
        if (type == DriverAssignmentType.PERMANENT) {
            open.forEach(contract -> conclude(contract, date));
            return null;
        }

        List<DriverCar> primaries = open.stream().filter(contract -> !contract.isReserve()).collect(Collectors.toList());
        if (primaries.size() != 1) {
            throw new BadRequestAlertException(
                primaries.isEmpty()
                    ? "Carro reserva exige um vínculo principal do motorista."
                    : "O motorista possui mais de um vínculo principal aberto: use a transferência definitiva.",
                ENTITY_NAME,
                primaries.isEmpty() ? "drivercarreserverequiresprimary" : "driverhasmultipleopencontracts"
            );
        }
        DriverCar primary = primaries.get(0);
        // Reserve swapped for another reserve: the current one ends, the new one points to the same primary.
        open.stream().filter(DriverCar::isReserve).forEach(reserve -> conclude(reserve, date));
        if (!Boolean.TRUE.equals(primary.getSuspended())) {
            primary.setSuspended(true);
            primary.setConcluded(false);
            driverCarRepository.save(primary);
        }
        return primary;
    }

    /**
     * New open contract of an existing driver on {@code car} with the same rules as POST /driver-cars: locks, the car
     * must be free, and the explicit PERMANENT / RESERVE choice applied by {@link #prepareAssignment} (a conflict
     * without a choice is the same 409 the contract screen answers). Used by the Checklist de Entrega.
     */
    public DriverCar openContract(Driver driver, Car car, DriverAssignmentType type, LocalDate startDate) {
        lockForAssignment(car, driver.getId());
        if (isCarOccupied(car.getId(), null)) {
            throw new BadRequestAlertException("Active Driver-Car exists, may not add another active driver", ENTITY_NAME, "activedriverexists");
        }
        DriverCar contract = new DriverCar();
        contract.setCar(car);
        contract.setDriver(driver);
        contract.setStartDate(startDate);
        contract.setConcluded(false);
        contract.setSuspended(false);
        contract.setPrimaryDriverCar(prepareAssignment(driver.getId(), car, type, startDate));
        return driverCarRepository.save(contract);
    }

    /** Concludes a contract as the contract screen does when it is closed (never suspended afterwards). */
    public void concludeContract(DriverCar contract, LocalDate date) {
        conclude(contract, date);
    }

    /** POST /return: concludes an active reserve and restores its primary when that is safe. */
    public ReserveReturnResultDTO returnReserve(DriverCar reserve, LocalDate endDate) {
        lockContract(reserve);
        if (!reserve.isReserve()) {
            throw new BadRequestAlertException("O vínculo não é de carro reserva.", ENTITY_NAME, "drivercarnotreserve");
        }
        if (Boolean.TRUE.equals(reserve.getConcluded())) {
            throw new BadRequestAlertException("O carro reserva já foi devolvido.", ENTITY_NAME, "drivercarreservealreadyreturned");
        }
        conclude(reserve, endDate);
        return restoreAfterReserve(reserve);
    }

    /**
     * After a reserve was concluded (by /return or by a PUT): the same primary row goes back to ACTIVE only if its car
     * is free and the driver operates no other car. Otherwise nobody is removed and the primary stays SUSPENDED - a
     * suspended primary without an open reserve is the pending-return state the client can show.
     */
    public ReserveReturnResultDTO restoreAfterReserve(DriverCar reserve) {
        Long primaryId = reserve.getPrimaryDriverCarId();
        Long driverId = reserve.getDriver() == null ? null : reserve.getDriver().getId();
        lockForAssignment(driverCarRepository.findCarIdById(primaryId), driverId);
        lockRow(DriverCar.class, primaryId, () -> driverCarRepository.findByIdForUpdate(primaryId));
        DriverCar primary = driverCarRepository.findById(primaryId).orElseThrow();

        ReserveReturnResultDTO result = new ReserveReturnResultDTO();
        result.setReturnedDriverCarId(reserve.getId());
        result.setPrimaryDriverCarId(primary.getId());
        result.setPrimaryCarPlate(primary.getCar() == null ? null : primary.getCar().getPlate());

        if (Boolean.TRUE.equals(primary.getConcluded())) {
            result.setOutcome(ReserveReturnResultDTO.Outcome.PRIMARY_CONCLUDED);
            return result;
        }
        if (isCarOccupied(primary.getCar().getId(), primary.getId())) {
            DriverCar occupying = driverCarRepository.findOperationalOnCar(primary.getCar().getId()).stream().findFirst().orElse(null);
            result.setOutcome(ReserveReturnResultDTO.Outcome.PRIMARY_CAR_OCCUPIED);
            result.setOccupyingDriverCarId(occupying == null ? null : occupying.getId());
            result.setOccupyingDriverName(occupying == null || occupying.getDriver() == null ? null : occupying.getDriver().getName());
            return result;
        }
        if (driverId != null && driverCarRepository.countOperationalByCurrentUserAndDriver(driverId, primary.getId()) > 0) {
            result.setOutcome(ReserveReturnResultDTO.Outcome.DRIVER_ACTIVE_ELSEWHERE);
            return result;
        }
        if (Boolean.TRUE.equals(primary.getSuspended())) {
            primary.setSuspended(false);
            driverCarRepository.save(primary);
        }
        result.setOutcome(ReserveReturnResultDTO.Outcome.RESTORED);
        result.setPrimaryRestored(true);
        return result;
    }

    /** POST /restore: a SUSPENDED primary back to ACTIVE once its car is free and its driver operates no other car. */
    public DriverCar restorePrimary(DriverCar primary) {
        lockContract(primary);
        if (primary.isReserve() || Boolean.TRUE.equals(primary.getConcluded()) || !Boolean.TRUE.equals(primary.getSuspended())) {
            throw new BadRequestAlertException("Somente um vínculo principal suspenso pode ser restaurado.", ENTITY_NAME, "drivercarnotsuspended");
        }
        Long driverId = primary.getDriver() == null ? null : primary.getDriver().getId();
        if (isCarOccupied(primary.getCar().getId(), primary.getId())) {
            DriverCar occupying = driverCarRepository.findOperationalOnCar(primary.getCar().getId()).stream().findFirst().orElse(null);
            throw new DriverAssignmentConflictException(
                DriverAssignmentConflictException.RESTORE_CONFLICT,
                "O veículo do vínculo principal está em uso por outro motorista.",
                occupying == null ? null : occupying.getId(),
                primary.getCar().getPlate()
            );
        }
        if (driverId != null && driverCarRepository.countOperationalByCurrentUserAndDriver(driverId, primary.getId()) > 0) {
            throw new DriverAssignmentConflictException(
                DriverAssignmentConflictException.RESTORE_CONFLICT,
                "O motorista está operando outro veículo: devolva o carro reserva.",
                null,
                null
            );
        }
        primary.setSuspended(false);
        return driverCarRepository.save(primary);
    }

    /** True when the driver has an open (active or suspended) contract of this account other than {@code exceptDriverCarId}. */
    public boolean hasOpenContractElsewhere(Long driverId, Long exceptDriverCarId) {
        return driverCarRepository
            .findOpenByCurrentUserAndDriver(driverId)
            .stream()
            .anyMatch(contract -> !contract.getId().equals(exceptDriverCarId));
    }

    /** True when pendencies were recorded during this contract: it is their origin and cannot disappear. */
    public boolean hasPendencies(Long driverCarId) {
        return pendencyRepository.countByCurrentUserAndDriverCarId(driverCarId) > 0;
    }

    /** True when a reserve contract points to this one as its primary. */
    public boolean isPrimaryOfReserves(Long driverCarId) {
        return driverCarRepository.existsByPrimaryDriverCarId(driverCarId);
    }

    private void conclude(DriverCar contract, LocalDate date) {
        contract.setConcluded(true);
        contract.setSuspended(false);
        if (contract.getEndDate() == null) {
            contract.setEndDate(date);
        }
        driverCarRepository.save(contract);
    }

    private static boolean isOperational(DriverCar contract) {
        return !Boolean.TRUE.equals(contract.getConcluded()) && !Boolean.TRUE.equals(contract.getSuspended());
    }
}
