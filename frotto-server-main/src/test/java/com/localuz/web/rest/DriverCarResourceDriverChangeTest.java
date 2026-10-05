package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.Address;
import com.localuz.domain.Car;
import com.localuz.domain.DebtItemType;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.AddressRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverAssignmentService;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Etapa 2.1: editing a driver_car never puts another person in it. Car 10 (ABC1D23): João (driver 5) in driver_car
 * 100 and Maria (driver 8) in driver_car 200; pendency 1000 is João's (100), 2000 is Maria's (200).
 */
class DriverCarResourceDriverChangeTest {

    private final Map<Long, DriverCar> contracts = new HashMap<>();
    private final Map<Long, Pendency> ownedPendencies = new HashMap<>();
    private DriverCarRepository driverCars;
    private DriverRepository drivers;
    private AddressRepository addresses;
    private PendencyRepository pendencies;
    private DriverCarResource resource;
    private DebtConfessionService confessions;

    private Driver joao;
    private Driver maria;
    private Car car10;
    private DriverCar contract100;

    @BeforeEach
    void setUp() {
        joao = driver(5L, "João Silva", "11111111111");
        joao.setContact("11 9999-0000");
        Address joaoAddress = new Address();
        joaoAddress.setId(50L);
        joao.setAddress(joaoAddress);
        maria = driver(8L, "Maria Souza", "22222222222");
        car10 = new Car();
        car10.setId(10L);
        car10.setPlate("ABC1D23");
        car10.setDeleted(false);
        contract100 = contract(100L, joao, LocalDate.of(2026, 1, 1), false);
        contract100.setWarranty(new BigDecimal("800.00"));
        contract100.setContractNumber("C-100");
        contract100.setScore(4.5f);

        driverCars = mock(DriverCarRepository.class);
        when(driverCars.findByCurrentUserAndId(anyLong())).thenAnswer(call -> Optional.ofNullable(contracts.get(call.<Long>getArgument(0))));
        when(driverCars.countOperationalOnCar(anyLong(), anyLong()))
            .thenAnswer(call ->
                contracts
                    .values()
                    .stream()
                    .filter(dc -> !Boolean.TRUE.equals(dc.getConcluded()) && !Boolean.TRUE.equals(dc.getSuspended()))
                    .filter(dc -> dc.getCar().getId().equals(call.getArgument(0)) && !dc.getId().equals(call.getArgument(1)))
                    .count()
            );
        when(driverCars.save(any(DriverCar.class)))
            .thenAnswer(call -> {
                DriverCar saved = call.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(200L);
                }
                contracts.put(saved.getId(), saved);
                return saved;
            });
        CarRepository cars = mock(CarRepository.class);
        when(cars.findByCurrentUserAndId(10L)).thenReturn(Optional.of(car10));
        drivers = mock(DriverRepository.class);
        when(drivers.findAllByCurrentUserAndCpf("11111111111")).thenReturn(List.of(joao));
        when(drivers.findAllByCurrentUserAndCpf("22222222222")).thenReturn(List.of(maria));
        when(drivers.findByCpf("22222222222")).thenReturn(Optional.of(maria));
        when(drivers.save(any(Driver.class))).thenAnswer(call -> call.getArgument(0));
        addresses = mock(AddressRepository.class);
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
        pendencies = mock(PendencyRepository.class);
        when(driverCars.findOpenByCurrentUserAndDriver(anyLong()))
            .thenAnswer(call -> contracts.values().stream().filter(dc -> dc.getDriver() != null && dc.getDriver().getId().equals(call.getArgument(0)) && !Boolean.TRUE.equals(dc.getConcluded())).collect(Collectors.toList()));
        resource = new DriverCarResource(driverCars, cars, addresses, drivers, new DriverAssignmentService(driverCars, drivers, cars, pendencies));
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");

        own(pendency(1000L, contract100, "Aluguel atrasado", "500.00"));
        when(pendencies.findByCurrentUserAndIdIn(any()))
            .thenAnswer(call ->
                ((Collection<?>) call.getArgument(0)).stream().map(ownedPendencies::get).filter(Objects::nonNull).collect(Collectors.toList())
            );
        DebtItemTypeRepository types = mock(DebtItemTypeRepository.class);
        when(types.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.<DebtItemType>of());
        confessions = new DebtConfessionService(pendencies, types);
    }

    @Test
    void contractFieldsStayEditableForTheSameDriver() {
        // A
        DriverCar request = editOf(contract100, sameDriverAs(joao));
        request.setWarranty(new BigDecimal("900.00"));
        request.setContractNumber("C-100-A");
        request.setConcluded(true);
        request.setEndDate(LocalDate.of(2026, 3, 31));
        request.setScore(4.0f);

        DriverCar updated = resource.updateDriverCarById(100L, request).getBody();

        assertThat(updated.getDriver().getId()).isEqualTo(5L);
        assertThat(updated.getWarranty()).isEqualByComparingTo("900.00");
        assertThat(updated.getContractNumber()).isEqualTo("C-100-A");
        assertThat(updated.getConcluded()).isTrue();
        assertThat(updated.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void cadastralDataOfTheSameDriverIsUpdatedInPlace() {
        // B: the edit form sends no driver id after the CPF field is touched; the existing driver is kept.
        Driver data = sameDriverAs(joao);
        data.setId(null);
        data.setContact("11 8888-7777");
        data.setEmail("joao@example.com");
        data.setAddress(new Address());

        DriverCar updated = resource.updateDriverCarById(100L, editOf(contract100, data)).getBody();

        assertThat(updated.getDriver().getId()).isEqualTo(5L);
        assertThat(updated.getDriver().getContact()).isEqualTo("11 8888-7777");
        assertThat(updated.getDriver().getAddress().getId()).isEqualTo(50L);
        assertThat(ownedPendencies.get(1000L).getDriverCar().getDriver().getId()).isEqualTo(5L);
    }

    @Test
    void anotherDriverIsRejectedAndNothingChanges() {
        // C + D + K: the contract (pendency, warranty, number, score, period) stays João's, nothing is written.
        DriverCar request = editOf(contract100, sameDriverAs(maria));
        request.setWarranty(BigDecimal.ONE);
        request.setContractNumber("TROCADO");

        assertForbidden(() -> resource.updateDriverCarById(100L, request));

        assertUntouched();
        assertThat(confessions.preview(List.of(1000L)).getDriverName()).isEqualTo("João Silva");
    }

    @Test
    void removingTheDriverOrUsingAForeignDriverIdIsRejected() {
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, null)));
        Driver foreign = driver(999L, "Outra conta", "99999999999");
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, foreign)));

        assertUntouched();
    }

    @Test
    void typingTheCpfOfAnotherDriverIsRejectedWhateverIdIsSent() {
        // G: the form switches to Maria's id when her CPF is typed; a direct call may also keep João's id or send none.
        Driver switched = sameDriverAs(maria);
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, switched)));

        Driver noId = sameDriverAs(joao);
        noId.setId(null);
        noId.setCpf("22222222222");
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, noId)));

        Driver keptId = sameDriverAs(joao);
        keptId.setCpf(" 22222222222 ");
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, keptId)));

        assertUntouched();
    }

    @Test
    void correctingTheCpfToOneNotInUseKeepsTheSamePerson() {
        // G: a typo fixed on the same driver (same id) is a cadastral update, not a change of person.
        Driver corrected = sameDriverAs(joao);
        corrected.setId(null);
        corrected.setCpf("11111111112");

        DriverCar updated = resource.updateDriverCarById(100L, editOf(contract100, corrected)).getBody();

        assertThat(updated.getDriver().getId()).isEqualTo(5L);
        assertThat(updated.getDriver().getCpf()).isEqualTo("11111111112");
        assertThat(contract100.getDriver().getId()).isEqualTo(5L);
    }

    @Test
    void duplicatedCpfsAlreadyInTheAccountNeverBreakTheEditOfTheSamePerson() {
        // Rows created before this rule may share a CPF: an unchanged CPF is not looked up at all...
        Driver twin = driver(6L, "João Gêmeo", "11111111111");
        when(drivers.findAllByCurrentUserAndCpf("11111111111")).thenReturn(List.of(joao, twin));
        Driver phone = sameDriverAs(joao);
        phone.setContact("11 7777-6666");

        assertThat(resource.updateDriverCarById(100L, editOf(contract100, phone)).getBody().getDriver().getContact()).isEqualTo("11 7777-6666");
        verify(drivers, never()).findAllByCurrentUserAndCpf("11111111111");

        // ...while changing to a CPF held by any other driver is still another person.
        when(drivers.findAllByCurrentUserAndCpf("33333333333")).thenReturn(List.of(driver(9L, "A", "33333333333"), driver(10L, "B", "33333333333")));
        Driver changed = sameDriverAs(joao);
        changed.setCpf("33333333333");
        assertForbidden(() -> resource.updateDriverCarById(100L, editOf(contract100, changed)));
    }

    @Test
    void anotherDriverUsesTheSameCarThroughANewContract() throws Exception {
        // F + E: João's contract is concluded, Maria gets a new driver_car; João's history stays his.
        DriverCar conclude = editOf(contract100, sameDriverAs(joao));
        conclude.setConcluded(true);
        conclude.setEndDate(LocalDate.of(2026, 6, 30));
        resource.updateDriverCarById(100L, conclude);

        DriverCar mariaContract = new DriverCar();
        mariaContract.setStartDate(LocalDate.of(2026, 7, 1));
        mariaContract.setConcluded(false);
        mariaContract.setDriver(sameDriverAs(maria));
        DriverCar created = resource.createDriverCarByCar(10L, null, mariaContract).getBody();
        own(pendency(2000L, created, "Multa", "130.00"));

        assertThat(created.getId()).isEqualTo(200L).isNotEqualTo(100L);
        assertThat(created.getDriver().getId()).isEqualTo(8L);
        assertThat(created.getCar()).isSameAs(car10);
        assertThat(contract100.getDriver().getId()).isEqualTo(5L);
        assertThat(ownedPendencies.get(1000L).getDriverCar()).isSameAs(contract100);
        assertThat(confessions.preview(List.of(1000L)).getDriverName()).isEqualTo("João Silva");
        assertThat(confessions.preview(List.of(2000L)).getDriverName()).isEqualTo("Maria Souza");
        assertThatThrownBy(() -> confessions.preview(List.of(1000L, 2000L)))
            .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("pendenciesdifferentdebtors"));

        // Maria's own contract keeps accepting her edits, and still cannot become João.
        resource.updateDriverCarById(200L, editOf(created, sameDriverAs(maria)));
        assertForbidden(() -> resource.updateDriverCarById(200L, editOf(created, sameDriverAs(joao))));
        assertThat(created.getDriver().getId()).isEqualTo(8L);
    }

    @Test
    void legacyContractWithoutDriverKeepsItsPreviousBehaviour() {
        DriverCar orphan = contract(300L, null, LocalDate.of(2025, 1, 1), true);

        DriverCar updated = resource.updateDriverCarById(300L, editOf(orphan, sameDriverAs(maria))).getBody();

        assertThat(updated.getDriver().getId()).isEqualTo(8L);
    }

    private void assertUntouched() {
        assertThat(contract100.getDriver()).isSameAs(joao);
        assertThat(contract100.getWarranty()).isEqualByComparingTo("800.00");
        assertThat(contract100.getContractNumber()).isEqualTo("C-100");
        assertThat(contract100.getScore()).isEqualTo(4.5f);
        assertThat(contract100.getConcluded()).isFalse();
        assertThat(joao.getName()).isEqualTo("João Silva");
        assertThat(joao.getCpf()).isEqualTo("11111111111");
        assertThat(ownedPendencies.get(1000L).getDriverCar()).isSameAs(contract100);
        assertThat(ownedPendencies.get(1000L).getDriverCar().getDriver()).isSameAs(joao);
        verify(driverCars, never()).save(any());
        verify(drivers, never()).save(any());
        verify(addresses, never()).save(any());
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarchangeforbidden"));
    }

    /** Request body as the edit form sends it: the contract fields plus the driver object. */
    private static DriverCar editOf(DriverCar contract, Driver driver) {
        DriverCar request = new DriverCar();
        request.setId(contract.getId());
        request.setStartDate(contract.getStartDate());
        request.setEndDate(contract.getEndDate());
        request.setWarranty(contract.getWarranty());
        request.setScore(contract.getScore());
        request.setDebt(contract.getDebt());
        request.setConcluded(contract.getConcluded());
        request.setContractNumber(contract.getContractNumber());
        request.setDriver(driver);
        return request;
    }

    private static Driver sameDriverAs(Driver source) {
        Driver copy = driver(source.getId(), source.getName(), source.getCpf());
        copy.setContact(source.getContact());
        return copy;
    }

    private DriverCar contract(Long id, Driver driver, LocalDate start, boolean concluded) {
        DriverCar contract = new DriverCar();
        contract.setId(id);
        contract.setDriver(driver);
        contract.setCar(car10);
        contract.setStartDate(start);
        contract.setConcluded(concluded);
        contracts.put(id, contract);
        return contract;
    }

    private void own(Pendency pendency) {
        ownedPendencies.put(pendency.getId(), pendency);
    }

    private static Driver driver(Long id, String name, String cpf) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setName(name);
        driver.setCpf(cpf);
        return driver;
    }

    private static Pendency pendency(Long id, DriverCar contract, String name, String cost) {
        Pendency pendency = new Pendency();
        pendency.setId(id);
        pendency.setDriverCar(contract);
        pendency.setDebtor(contract.getDriver());
        pendency.setName(name);
        pendency.setCost(new BigDecimal(cost));
        pendency.setPaidAmount(BigDecimal.ZERO);
        pendency.setRemainingAmount(new BigDecimal(cost));
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setDate(LocalDate.of(2026, 3, 1));
        return pendency;
    }
}
