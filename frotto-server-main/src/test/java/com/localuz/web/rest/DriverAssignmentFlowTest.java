package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.Car;
import com.localuz.domain.DebtItemType;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.AddressRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverAssignmentService;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import com.localuz.service.dto.PendencyPaymentDTO;
import com.localuz.service.dto.ReserveReturnResultDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.DriverAssignmentConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Etapas 2.2 / 2.2.1: drivers on cars - primary contract, reserve car (primary SUSPENDED, then restored: the same
 * row), definitive transfer - and the debts that always stay with their frozen debtor.
 *
 * <p>Fleet: Onix (10), HB20 (20), Argo (30). João (5) drives the Onix in driver_car 100; Maria (8) and Pedro (9) have
 * no contract. Repositories answer like the real queries (current user's rows; counts read the current state).
 */
class DriverAssignmentFlowTest {

    private final Map<Long, DriverCar> contracts = new LinkedHashMap<>();
    private final List<Pendency> savedPendencies = new ArrayList<>();
    private final Map<Long, Driver> knownDrivers = new LinkedHashMap<>();
    private DriverCarRepository driverCars;
    private DriverRepository drivers;
    private CarRepository cars;
    private PendencyRepository pendencies;
    private DriverCarResource resource;
    private PendencyResource pendencyResource;
    private DebtConfessionService confessions;

    private Driver joao;
    private Driver maria;
    private Driver pedro;
    private Car onix;
    private Car hb20;
    private Car argo;
    private DriverCar joaoOnix;
    private long nextContractId = 500;

    @BeforeEach
    void setUp() {
        joao = driver(5L, "João Silva", "11111111111");
        maria = driver(8L, "Maria Souza", "22222222222");
        pedro = driver(9L, "Pedro Lima", "33333333333");
        onix = car(10L, "ONX1A11");
        hb20 = car(20L, "HBV2B22");
        argo = car(30L, "ARG3C33");
        joaoOnix = contract(100L, joao, onix, LocalDate.of(2026, 8, 1), false);

        driverCars = mock(DriverCarRepository.class);
        when(driverCars.findByCurrentUserAndId(anyLong())).thenAnswer(call -> Optional.ofNullable(contracts.get(call.<Long>getArgument(0))));
        when(driverCars.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(contracts.get(call.<Long>getArgument(0))));
        when(driverCars.findOpenByCurrentUserAndDriver(anyLong()))
            .thenAnswer(call -> contracts.values().stream().filter(dc -> isDriver(dc, call.getArgument(0)) && !concluded(dc)).collect(Collectors.toList()));
        when(driverCars.countOperationalByCurrentUserAndDriver(anyLong(), anyLong()))
            .thenAnswer(call ->
                contracts.values().stream().filter(dc -> isDriver(dc, call.getArgument(0)) && operational(dc) && !dc.getId().equals(call.getArgument(1))).count()
            );
        when(driverCars.countOperationalOnCar(anyLong(), anyLong()))
            .thenAnswer(call ->
                contracts
                    .values()
                    .stream()
                    .filter(dc -> dc.getCar().getId().equals(call.getArgument(0)) && operational(dc))
                    .filter(dc -> !dc.getId().equals(call.getArgument(1)))
                    .count()
            );
        when(driverCars.findOperationalOnCar(anyLong()))
            .thenAnswer(call -> contracts.values().stream().filter(dc -> dc.getCar().getId().equals(call.getArgument(0)) && operational(dc)).collect(Collectors.toList()));
        when(driverCars.findCarIdById(anyLong())).thenAnswer(call -> contracts.get(call.<Long>getArgument(0)).getCar().getId());
        when(driverCars.existsByPrimaryDriverCarId(anyLong()))
            .thenAnswer(call -> contracts.values().stream().anyMatch(dc -> call.getArgument(0).equals(dc.getPrimaryDriverCarId())));
        when(driverCars.save(any(DriverCar.class)))
            .thenAnswer(call -> {
                DriverCar saved = call.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(nextContractId++);
                }
                contracts.put(saved.getId(), saved);
                return saved;
            });
        doAnswer(call -> contracts.remove(call.<Long>getArgument(0))).when(driverCars).deleteById(anyLong());

        cars = mock(CarRepository.class);
        for (Car car : List.of(onix, hb20, argo)) {
            when(cars.findByCurrentUserAndId(car.getId())).thenReturn(Optional.of(car));
        }
        drivers = mock(DriverRepository.class);
        for (Driver driver : List.of(joao, maria, pedro)) {
            when(drivers.findByCpf(driver.getCpf())).thenReturn(Optional.of(driver));
            when(drivers.findByCurrentUserAndId(driver.getId())).thenReturn(Optional.of(driver));
        }
        // Like a JPA merge: saving a known driver updates and returns the managed instance.
        when(drivers.save(any(Driver.class)))
            .thenAnswer(call -> {
                Driver saved = call.getArgument(0);
                Driver managed = saved.getId() == null ? null : knownDrivers.get(saved.getId());
                if (managed == null) {
                    return saved;
                }
                managed.setName(saved.getName());
                return managed;
            });

        pendencies = mock(PendencyRepository.class);
        when(pendencies.save(any(Pendency.class)))
            .thenAnswer(call -> {
                Pendency saved = call.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(9000L + savedPendencies.size());
                    savedPendencies.add(saved);
                }
                return saved;
            });
        when(pendencies.countByCurrentUserAndDriverCarId(anyLong()))
            .thenAnswer(call -> savedPendencies.stream().filter(p -> p.getDriverCar().getId().equals(call.getArgument(0))).count());
        when(pendencies.findByCurrentUserAndPendencyId(anyLong()))
            .thenAnswer(call -> savedPendencies.stream().filter(p -> p.getId().equals(call.getArgument(0))).findFirst());
        when(pendencies.findByCurrentUserAndIdIn(any()))
            .thenAnswer(call ->
                ((Collection<?>) call.getArgument(0)).stream()
                    .map(id -> savedPendencies.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
            );

        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        resource = new DriverCarResource(driverCars, cars, mock(AddressRepository.class), drivers, assignments);
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
        pendencyResource = new PendencyResource(pendencies, driverCars, drivers, mock(DebtConfessionService.class));
        ReflectionTestUtils.setField(pendencyResource, "applicationName", "localmaisApp");
        DebtItemTypeRepository types = mock(DebtItemTypeRepository.class);
        when(types.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.<DebtItemType>of());
        confessions = new DebtConfessionService(pendencies, types);
    }

    @Nested
    class PrimaryAndPermanent {

        @Test
        void driverWithoutContractGetsANormalActivePrimary() throws Exception {
            // 1
            DriverCar created = assign(maria, hb20, null, LocalDate.of(2026, 9, 1));

            assertThat(created.getStatus()).isEqualTo("ACTIVE");
            assertThat(created.isReserve()).isFalse();
            assertThat(created.getPrimaryDriverCarId()).isNull();
            assertThat(created.getSuspended()).isFalse();
        }

        @Test
        void activeDriverInAnotherCarWithoutIntentIsRejectedAndNothingChanges() {
            // 2
            assertThatThrownBy(() -> assign(joao, hb20, null, LocalDate.of(2026, 9, 10)))
                .isInstanceOfSatisfying(DriverAssignmentConflictException.class, failure -> {
                    assertThat(failure.getErrorKey()).isEqualTo("driverassignmentrequired");
                    assertThat(failure.getStatus().getStatusCode()).isEqualTo(409);
                    assertThat(failure.getParameters()).containsEntry("conflictingDriverCarId", 100L).containsEntry("conflictingCarPlate", "ONX1A11");
                });
            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
            assertThat(contracts).hasSize(1);
            verify(driverCars, never()).save(any());
        }

        @Test
        void permanentTransferConcludesTheOldContractAndStartsANewPrimary() throws Exception {
            // 3
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 10));

            assertThat(joaoOnix.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoOnix.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(joaoHb20.getStatus()).isEqualTo("ACTIVE");
            assertThat(joaoHb20.isReserve()).isFalse();
            assertThat(operationalOf(joao)).containsExactly(joaoHb20);
        }

        @Test
        void reserveForSomebodyWithoutPrimaryIsRejected() {
            assertThatThrownBy(() -> assign(maria, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarreserverequiresprimary"));
            Driver newcomer = driver(null, "Novo", "44444444444");
            assertThatThrownBy(() -> assign(newcomer, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarreserverequiresprimary"));
        }

        @Test
        void legacyDriverWithTwoOpenPrimariesCanOnlyBeTransferredPermanently() throws Exception {
            DriverCar legacyDuplicate = contract(101L, joao, argo, LocalDate.of(2026, 8, 2), false);

            assertThatThrownBy(() -> assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("driverhasmultipleopencontracts"));
            assign(joao, hb20, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 10));

            assertThat(joaoOnix.getStatus()).isEqualTo("CONCLUDED");
            assertThat(legacyDuplicate.getStatus()).isEqualTo("CONCLUDED");
            assertThat(operationalOf(joao)).hasSize(1);
        }

        @Test
        void pastContractsAndNewPeopleAreNeverAssignments() throws Exception {
            DriverCar past = newContract(joao, LocalDate.of(2025, 1, 1));
            past.setConcluded(true);
            past.setEndDate(LocalDate.of(2025, 6, 30));
            resource.createDriverCarByCar(20L, null, past);
            assign(driver(null, "Novo", "44444444444"), argo, null, LocalDate.of(2026, 9, 1));

            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
            verify(driverCars, never()).findOpenByCurrentUserAndDriver(anyLong());
        }
    }

    @Nested
    class Reserve {

        @Test
        void reserveSuspendsTheSamePrimaryAndPointsToIt() throws Exception {
            // 4
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));

            assertThat(contracts.get(100L)).isSameAs(joaoOnix);
            assertThat(joaoOnix.getStatus()).isEqualTo("SUSPENDED");
            assertThat(joaoOnix.getConcluded()).isFalse();
            assertThat(joaoOnix.getEndDate()).isNull();
            assertThat(joaoHb20.getStatus()).isEqualTo("ACTIVE");
            assertThat(joaoHb20.isReserve()).isTrue();
            assertThat(joaoHb20.getPrimaryDriverCarId()).isEqualTo(100L);
            assertThat(operationalOf(joao)).containsExactly(joaoHb20);
        }

        @Test
        void returningTheReserveRestoresTheSamePrimaryRow() throws Exception {
            // 5 + 6
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));

            ReserveReturnResultDTO result = resource.returnReserveCar(joaoHb20.getId(), LocalDate.of(2026, 9, 15));

            assertThat(result.getOutcome()).isEqualTo(ReserveReturnResultDTO.Outcome.RESTORED);
            assertThat(result.isPrimaryRestored()).isTrue();
            assertThat(result.getPrimaryDriverCarId()).isEqualTo(100L);
            assertThat(joaoHb20.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoHb20.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(contracts.get(100L)).isSameAs(joaoOnix);
            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
            assertThat(joaoOnix.getEndDate()).isNull();
            assertThat(contractsOf(joao)).hasSize(2);
            assertThat(contractsOf(joao).stream().filter(dc -> dc.getCar() == onix)).containsExactly(joaoOnix);
        }

        @Test
        void reserveSwappedForAnotherReserveKeepsPointingToTheSamePrimary() throws Exception {
            // 7
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            DriverCar joaoArgo = assign(joao, argo, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 15));

            assertThat(joaoHb20.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoHb20.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(joaoArgo.getStatus()).isEqualTo("ACTIVE");
            assertThat(joaoArgo.getPrimaryDriverCarId()).isEqualTo(100L);
            assertThat(joaoOnix.getStatus()).isEqualTo("SUSPENDED");
            assertThat(contractsOf(joao).stream().filter(dc -> !dc.isReserve())).containsExactly(joaoOnix);

            resource.returnReserveCar(joaoArgo.getId(), LocalDate.of(2026, 9, 18));

            assertThat(joaoArgo.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
            assertThat(operationalOf(joao)).containsExactly(joaoOnix);
            assertThat(contractsOf(joao)).hasSize(3);
            assertThat(contractsOf(joao).stream().filter(dc -> dc.getCar() == onix)).containsExactly(joaoOnix);
        }

        @Test
        void concludingTheReserveThroughPutIsAlsoAReturn() throws Exception {
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            DriverCar conclude = edit(joaoHb20);
            conclude.setConcluded(true);
            conclude.setEndDate(LocalDate.of(2026, 9, 15));

            resource.updateDriverCarById(joaoHb20.getId(), conclude);

            assertThat(joaoHb20.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void permanentTransferFromAReserveConcludesTheReserveAndTheSuspendedPrimary() throws Exception {
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            DriverCar joaoArgo = assign(joao, argo, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 20));

            assertThat(joaoHb20.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoOnix.getStatus()).isEqualTo("CONCLUDED");
            assertThat(joaoOnix.getSuspended()).isFalse();
            assertThat(joaoArgo.isReserve()).isFalse();
            assertThat(operationalOf(joao)).containsExactly(joaoArgo);
        }

        @Test
        void aReturnedReserveCannotBeReturnedOrReopenedAgain() throws Exception {
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            resource.returnReserveCar(joaoHb20.getId(), LocalDate.of(2026, 9, 15));

            assertThatThrownBy(() -> resource.returnReserveCar(joaoHb20.getId(), null))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarreservealreadyreturned"));
            DriverCar reopen = edit(joaoHb20);
            reopen.setConcluded(false);
            assertThatThrownBy(() -> resource.updateDriverCarById(joaoHb20.getId(), reopen))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarreservecannotreopen"));
            assertThatThrownBy(() -> resource.returnReserveCar(100L, null))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarnotreserve"));
        }
    }

    @Nested
    class ReturnConflict {

        @Test
        void returningWhileAnotherDriverUsesThePrimaryCarRemovesNobody() throws Exception {
            // 8
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            DriverCar mariaOnix = assign(maria, onix, null, LocalDate.of(2026, 9, 11));

            ReserveReturnResultDTO result = resource.returnReserveCar(joaoHb20.getId(), LocalDate.of(2026, 9, 15));

            assertThat(result.getOutcome()).isEqualTo(ReserveReturnResultDTO.Outcome.PRIMARY_CAR_OCCUPIED);
            assertThat(result.isPrimaryRestored()).isFalse();
            assertThat(result.getPrimaryDriverCarId()).isEqualTo(100L);
            assertThat(result.getPrimaryCarPlate()).isEqualTo("ONX1A11");
            assertThat(result.getOccupyingDriverCarId()).isEqualTo(mariaOnix.getId());
            assertThat(result.getOccupyingDriverName()).isEqualTo("Maria Souza");
            assertThat(joaoHb20.getStatus()).isEqualTo("CONCLUDED");
            assertThat(mariaOnix.getStatus()).isEqualTo("ACTIVE");
            assertThat(contracts.get(100L)).isSameAs(joaoOnix);
            assertThat(joaoOnix.getStatus()).isEqualTo("SUSPENDED");
            assertThat(contractsOf(joao)).hasSize(2);

            // The pending return is resolved later with the same row, once the Onix is free.
            assertThatThrownBy(() -> resource.restorePrimaryContract(100L))
                .isInstanceOfSatisfying(DriverAssignmentConflictException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarrestoreconflict"));
            DriverCar leave = edit(mariaOnix);
            leave.setConcluded(true);
            leave.setEndDate(LocalDate.of(2026, 9, 20));
            resource.updateDriverCarById(mariaOnix.getId(), leave);
            resource.restorePrimaryContract(100L);

            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
            assertThat(contractsOf(joao).stream().filter(dc -> dc.getCar() == onix)).containsExactly(joaoOnix);
        }

        @Test
        void aSuspendedPrimaryStaysEditableWhileSomebodyElseDrivesTheCar() throws Exception {
            assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            assign(maria, onix, null, LocalDate.of(2026, 9, 11));
            DriverCar edit = edit(joaoOnix);
            edit.setContractNumber("C-100");

            resource.updateDriverCarById(100L, edit);

            assertThat(joaoOnix.getContractNumber()).isEqualTo("C-100");
            assertThat(joaoOnix.getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        void onlyASuspendedPrimaryCanBeRestored() {
            assertThatThrownBy(() -> resource.restorePrimaryContract(100L))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarnotsuspended"));
        }

        @Test
        void restoringWhileTheReserveIsStillActiveIsRefused() throws Exception {
            assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));

            assertThatThrownBy(() -> resource.restorePrimaryContract(100L))
                .isInstanceOfSatisfying(DriverAssignmentConflictException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarrestoreconflict"));
            assertThat(joaoOnix.getStatus()).isEqualTo("SUSPENDED");
        }
    }

    @Nested
    class Consistency {

        @Test
        void aDriverNeverGetsTwoActiveContracts() throws Exception {
            // 9
            assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            assertThatThrownBy(() -> assign(joao, onix, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 11)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("driverhasopencontractoncar"));

            DriverCar oldOnix = contract(90L, joao, argo, LocalDate.of(2025, 1, 1), true);
            DriverCar reopen = edit(oldOnix);
            reopen.setConcluded(false);
            assertThatThrownBy(() -> resource.updateDriverCarById(90L, reopen))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("driveractiveelsewhere"));
            assertThat(operationalOf(joao)).hasSize(1);
            assertThat(contractsOf(joao).stream().filter(dc -> "SUSPENDED".equals(dc.getStatus()))).hasSize(1);
        }

        @Test
        void aCarNeverGetsTwoActiveDrivers() throws Exception {
            // 10
            assertThatThrownBy(() -> assign(maria, onix, null, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("activedriverexists"));
            assertThatThrownBy(() -> assign(joao, onix, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("activedriverexists"));
            assertThat(contracts).hasSize(1);
        }

        @Test
        void assignmentsLockTheCarThenTheDriverBeforeReading() throws Exception {
            assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));

            InOrder order = inOrder(cars, drivers, driverCars);
            order.verify(cars).findByIdForUpdate(20L);
            order.verify(drivers).findByIdForUpdate(5L);
            order.verify(driverCars).countOperationalOnCar(20L, -1L);
            order.verify(driverCars).findOpenByCurrentUserAndDriver(5L);
        }

        @Test
        void returnLocksThePrimaryCarThenTheDriverBeforeDeciding() throws Exception {
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            clearInvocations(cars, drivers, driverCars);

            resource.returnReserveCar(joaoHb20.getId(), null);

            InOrder order = inOrder(cars, drivers, driverCars);
            order.verify(cars).findByIdForUpdate(10L);
            order.verify(drivers).findByIdForUpdate(5L);
            order.verify(driverCars).findById(100L);
            order.verify(driverCars).countOperationalOnCar(10L, 100L);
        }

        @Test
        void failureCreatingTheReservePropagatesSoNothingStaysSuspended() {
            // 16: the suspension and the new reserve run in the same (MANDATORY) transaction; the failure is never swallowed.
            doThrow(new IllegalStateException("insert failed")).when(driverCars).save(argThat(dc -> dc != null && dc.getId() == null));

            assertThatThrownBy(() -> assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10)))
                .isInstanceOf(IllegalStateException.class);
            assertThat(
                DriverAssignmentService.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation()
            )
                .isEqualTo(org.springframework.transaction.annotation.Propagation.MANDATORY);
            assertThat(DriverCarResource.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
        }

        @Test
        void failureRestoringPropagatesSoTheReturnRollsBackAsAWhole() throws Exception {
            // 17
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            doThrow(new IllegalStateException("update failed")).when(driverCars).save(argThat(dc -> dc != null && dc.getId() != null && dc.getId() == 100L));

            assertThatThrownBy(() -> resource.returnReserveCar(joaoHb20.getId(), null)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void suspendedOrReserveLinkedContractsCannotBeDeleted() throws Exception {
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            assertThatThrownBy(() -> resource.deleteDriverCarById(100L))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarhasreserves"));
            assertThatThrownBy(() -> resource.deleteDriverCarById(joaoHb20.getId()))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarhasreserves"));
            resource.returnReserveCar(joaoHb20.getId(), null);
            assertThatThrownBy(() -> resource.deleteDriverCarById(100L))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarhasreserves"));
            assertThat(contracts).containsKeys(100L, joaoHb20.getId());
        }

        @Test
        void clientsCannotSuspendOrLinkContractsThroughTheBody() throws Exception {
            DriverCar body = newContract(maria, LocalDate.of(2026, 9, 1));
            body.setSuspended(true);
            body.setPrimaryDriverCar(joaoOnix);

            DriverCar created = resource.createDriverCarByCar(20L, null, body).getBody();

            assertThat(created.getSuspended()).isFalse();
            assertThat(created.isReserve()).isFalse();
            assertThat(joaoOnix.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void anotherAccountsDriverIdIsNeverAttached() {
            DriverCar request = new DriverCar();
            request.setConcluded(false);
            request.setDriver(driver(777L, "Outra conta", null));

            assertThatThrownBy(() -> resource.createDriverCarByCar(20L, DriverAssignmentType.PERMANENT, request))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivernotfound"));
            verify(driverCars, never()).save(any());
            verify(cars, never()).findByIdForUpdate(anyLong());
        }
    }

    @Nested
    class Debts {

        @Test
        void debtsStayWithJoaoThroughSuspensionReservesAndRestoration() throws Exception {
            // 11, 12, 13
            Pendency onixFine = record(100L, "Multa", "300.00");
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            assertThat(onixFine.getDebtorDriverId()).isEqualTo(5L);
            Pendency hb20Damage = record(joaoHb20.getId(), "Danos/Avarias", "400.00");
            assertThat(hb20Damage.getDebtorDriverId()).isEqualTo(5L);
            assertThat(hb20Damage.getDriverCar()).isSameAs(joaoHb20);
            String before = states(onixFine, hb20Damage);

            resource.returnReserveCar(joaoHb20.getId(), LocalDate.of(2026, 9, 15));

            assertThat(states(onixFine, hb20Damage)).isEqualTo(before);
            assertThat(hb20Damage.getDebtorDriverId()).isEqualTo(5L);
            assertThat(hb20Damage.getDriverCar()).isSameAs(joaoHb20);
            assertThat(onixFine.getDriverCar()).isSameAs(joaoOnix);
            verify(pendencies, never()).delete(any());
        }

        @Test
        void aLaterDriverOfTheReserveCarNeverInheritsJoaosDebts() throws Exception {
            // 14
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            Pendency joaoDebt = record(joaoHb20.getId(), "Danos/Avarias", "400.00");
            resource.returnReserveCar(joaoHb20.getId(), LocalDate.of(2026, 9, 15));
            DriverCar pedroHb20 = assign(pedro, hb20, null, LocalDate.of(2026, 9, 16));
            Pendency pedroDebt = record(pedroHb20.getId(), "Multa", "50.00");

            assertThat(joaoDebt.getDebtorDriverId()).isEqualTo(5L);
            assertThat(pedroDebt.getDebtorDriverId()).isEqualTo(9L);
            assertThat(confessions.preview(List.of(joaoDebt.getId())).getDriverName()).isEqualTo("João Silva");
            assertThatThrownBy(() -> confessions.preview(List.of(joaoDebt.getId(), pedroDebt.getId())))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("pendenciesdifferentdebtors"));
        }

        @Test
        void oneConfessionGathersJoaosDebtsFromThePrimaryAndBothReserves() throws Exception {
            // 15
            Pendency onixFine = record(100L, "Multa", "300.00");
            DriverCar joaoHb20 = assign(joao, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 10));
            Pendency hb20Shared = record(joaoHb20.getId(), "Manutenção compartilhada", "250.00");
            DriverCar joaoArgo = assign(joao, argo, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 15));
            Pendency argoDamage = record(joaoArgo.getId(), "Danos/Avarias", "400.00");
            resource.returnReserveCar(joaoArgo.getId(), LocalDate.of(2026, 9, 18));

            DebtConfessionPreviewDTO preview = confessions.preview(List.of(onixFine.getId(), hb20Shared.getId(), argoDamage.getId()));

            assertThat(preview.getDriverId()).isEqualTo(5L);
            assertThat(preview.getValorTotal()).isEqualByComparingTo("950.00");
            assertThat(preview.getCarId()).isNull();
            assertThat(preview.getItems())
                .extracting(DebtConfessionPreviewDTO.Item::getOriginCarPlate)
                .containsExactlyInAnyOrder("ONX1A11", "HBV2B22", "ARG3C33");
            assertThat(preview.getItems())
                .extracting(DebtConfessionPreviewDTO.Item::getOriginDriverCarId)
                .containsExactlyInAnyOrder(100L, joaoHb20.getId(), joaoArgo.getId());
        }

        @Test
        void permanentTransferKeepsEveryDebtWithItsDebtorAndOrigin() throws Exception {
            Pendency fine = record(100L, "Multa", "300.00");
            String before = states(fine);

            assign(joao, hb20, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 10));
            DriverCar mariaOnix = assign(maria, onix, null, LocalDate.of(2026, 9, 11));
            Pendency mariaFine = record(mariaOnix.getId(), "Multa", "130.00");

            assertThat(states(fine)).isEqualTo(before);
            assertThat(fine.getDebtorDriverId()).isEqualTo(5L);
            assertThat(mariaFine.getDebtorDriverId()).isEqualTo(8L);
        }

        @Test
        void paymentsKeepWorkingAndClientsCannotSetTheDebtor() throws Exception {
            Pendency fine = record(100L, "Multa", "300.00");
            PendencyPaymentDTO partial = new PendencyPaymentDTO();
            partial.setAmount(new BigDecimal("100.00"));
            pendencyResource.addPaymentToPendency(fine.getId(), partial);
            assertThat(fine.getRemainingAmount()).isEqualByComparingTo("200.00");
            assertThat(fine.getStatus()).isEqualTo(PendencyStatus.PARTIALLY_PAID);
            pendencyResource.payPendency(fine.getId(), null);
            assertThat(fine.getStatus()).isEqualTo(PendencyStatus.PAID);
            assertThat(fine.getDebtorDriverId()).isEqualTo(5L);

            Pendency request = new Pendency();
            request.setName("Multa");
            request.setCost(new BigDecimal("10.00"));
            request.setDebtor(maria);
            assertThat(pendencyResource.createPendency(100L, request).getBody().getDebtorDriverId()).isEqualTo(5L);
        }

        @Test
        void aContractWithPendenciesCannotBeDeleted() throws Exception {
            DriverCar mariaArgo = assign(maria, argo, null, LocalDate.of(2026, 9, 1));
            record(mariaArgo.getId(), "Multa", "10.00");

            assertThatThrownBy(() -> resource.deleteDriverCarById(mariaArgo.getId()))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarhaspendencies"));
        }
    }

    /** concluded = NULL (legacy rows): "not concluded", read the same way from the driver side and the car side. */
    @Nested
    class LegacyNullConcluded {

        @Test
        void statusOfEveryCombination() {
            // 1-4
            assertThat(state(false, false).getStatus()).isEqualTo("ACTIVE");
            assertThat(state(false, true).getStatus()).isEqualTo("SUSPENDED");
            assertThat(state(true, false).getStatus()).isEqualTo("CONCLUDED");
            assertThat(state(null, false).getStatus()).isEqualTo("ACTIVE");
            assertThat(state(null, true).getStatus()).isEqualTo("SUSPENDED");
        }

        @Test
        void aLegacyNullContractOccupiesItsCar() {
            // 5
            contract(110L, pedro, argo, LocalDate.of(2025, 1, 1), false).setConcluded(null);

            assertThatThrownBy(() -> assign(maria, argo, null, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("activedriverexists"));
        }

        @Test
        void aDriverWithALegacyNullContractNeedsAnExplicitChoice() {
            // 6
            DriverCar legacy = contract(110L, pedro, argo, LocalDate.of(2025, 1, 1), false);
            legacy.setConcluded(null);

            assertThatThrownBy(() -> assign(pedro, hb20, null, LocalDate.of(2026, 9, 1)))
                .isInstanceOfSatisfying(DriverAssignmentConflictException.class, failure ->
                    assertThat(failure.getParameters()).containsEntry("conflictingDriverCarId", 110L)
                );
            assertThat(legacy.getConcluded()).isNull();
        }

        @Test
        void permanentTransferConcludesALegacyNullContract() throws Exception {
            // 7
            DriverCar legacy = contract(110L, pedro, argo, LocalDate.of(2025, 1, 1), false);
            legacy.setConcluded(null);

            DriverCar pedroHb20 = assign(pedro, hb20, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 9, 1));

            assertThat(legacy.getConcluded()).isTrue();
            assertThat(legacy.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(operationalOf(pedro)).containsExactly(pedroHb20);
        }

        @Test
        void reserveFromALegacyNullPrimaryThenRestoreKeepsTheSameRow() throws Exception {
            // 8 + 9
            DriverCar legacy = contract(110L, pedro, argo, LocalDate.of(2025, 1, 1), false);
            legacy.setConcluded(null);

            DriverCar reserve = assign(pedro, hb20, DriverAssignmentType.RESERVE, LocalDate.of(2026, 9, 1));
            assertThat(legacy.getStatus()).isEqualTo("SUSPENDED");
            assertThat(legacy.getConcluded()).isFalse();
            assertThat(reserve.getPrimaryDriverCarId()).isEqualTo(110L);
            // Suspended: the car is free for somebody else meanwhile.
            DriverCar mariaArgo = assign(maria, argo, null, LocalDate.of(2026, 9, 2));
            assertThat(resource.returnReserveCar(reserve.getId(), null).getOutcome()).isEqualTo(ReserveReturnResultDTO.Outcome.PRIMARY_CAR_OCCUPIED);
            DriverCar leave = edit(mariaArgo);
            leave.setConcluded(true);
            resource.updateDriverCarById(mariaArgo.getId(), leave);
            resource.restorePrimaryContract(110L);

            assertThat(contracts.get(110L)).isSameAs(legacy);
            assertThat(legacy.getStatus()).isEqualTo("ACTIVE");
            assertThat(contractsOf(pedro)).hasSize(2);
        }

        @Test
        void theServerNeverStoresANullConcludedAgain() throws Exception {
            DriverCar body = newContract(maria, LocalDate.of(2026, 9, 1));
            body.setConcluded(null);
            DriverCar created = resource.createDriverCarByCar(30L, null, body).getBody();
            assertThat(created.getConcluded()).isFalse();

            DriverCar update = edit(created);
            update.setConcluded(null);
            resource.updateDriverCarById(created.getId(), update);
            assertThat(created.getConcluded()).isFalse();
            assertThat(created.getStatus()).isEqualTo("ACTIVE");
        }

        private DriverCar state(Boolean concluded, boolean suspended) {
            DriverCar contract = new DriverCar();
            contract.setConcluded(concluded);
            contract.setSuspended(suspended);
            return contract;
        }
    }

    // ---- helpers ----

    private DriverCar assign(Driver driver, Car car, DriverAssignmentType type, LocalDate start) throws Exception {
        return resource.createDriverCarByCar(car.getId(), type, newContract(driver, start)).getBody();
    }

    private Pendency record(Long driverCarId, String name, String cost) throws Exception {
        Pendency request = new Pendency();
        request.setName(name);
        request.setCost(new BigDecimal(cost));
        request.setDate(LocalDate.of(2026, 9, 12));
        return pendencyResource.createPendency(driverCarId, request).getBody();
    }

    private static String states(Pendency... list) {
        StringBuilder state = new StringBuilder();
        for (Pendency p : list) {
            state
                .append(p.getId())
                .append('|')
                .append(p.getCost())
                .append('|')
                .append(p.getPaidAmount())
                .append('|')
                .append(p.getRemainingAmount())
                .append('|')
                .append(p.getStatus())
                .append('|')
                .append(p.getPaidAt())
                .append('|')
                .append(p.getDriverCar().getId())
                .append('|')
                .append(p.getDebtorDriverId())
                .append(';');
        }
        return state.toString();
    }

    private List<DriverCar> contractsOf(Driver driver) {
        return contracts.values().stream().filter(dc -> isDriver(dc, driver.getId())).collect(Collectors.toList());
    }

    private List<DriverCar> operationalOf(Driver driver) {
        return contractsOf(driver).stream().filter(DriverAssignmentFlowTest::operational).collect(Collectors.toList());
    }

    private static boolean isDriver(DriverCar contract, Object driverId) {
        return contract.getDriver() != null && contract.getDriver().getId() != null && contract.getDriver().getId().equals(driverId);
    }

    private static boolean concluded(DriverCar contract) {
        return Boolean.TRUE.equals(contract.getConcluded());
    }

    private static boolean suspended(DriverCar contract) {
        return Boolean.TRUE.equals(contract.getSuspended());
    }

    private static boolean operational(DriverCar contract) {
        return !concluded(contract) && !suspended(contract);
    }

    private DriverCar newContract(Driver driver, LocalDate start) {
        DriverCar contract = new DriverCar();
        contract.setStartDate(start);
        contract.setConcluded(false);
        contract.setDriver(driver(driver.getId(), driver.getName(), driver.getCpf()));
        return contract;
    }

    private DriverCar edit(DriverCar contract) {
        DriverCar request = new DriverCar();
        request.setId(contract.getId());
        request.setStartDate(contract.getStartDate());
        request.setEndDate(contract.getEndDate());
        request.setConcluded(contract.getConcluded());
        request.setContractNumber(contract.getContractNumber());
        Driver driver = contract.getDriver();
        request.setDriver(driver(driver.getId(), driver.getName(), driver.getCpf()));
        return request;
    }

    private DriverCar contract(Long id, Driver driver, Car car, LocalDate start, boolean concluded) {
        DriverCar contract = new DriverCar();
        contract.setId(id);
        contract.setDriver(driver);
        contract.setCar(car);
        contract.setStartDate(start);
        contract.setConcluded(concluded);
        contracts.put(id, contract);
        return contract;
    }

    private Driver driver(Long id, String name, String cpf) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setName(name);
        driver.setCpf(cpf);
        if (id != null && !knownDrivers.containsKey(id)) {
            knownDrivers.put(id, driver);
        }
        return driver;
    }

    private static Car car(Long id, String plate) {
        Car car = new Car();
        car.setId(id);
        car.setPlate(plate);
        car.setDeleted(false);
        return car;
    }
}
