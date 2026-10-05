package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.Car;
import com.localuz.domain.DebtItemType;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DebtConfessionServiceTest {

    PendencyRepository pendencies = mock(PendencyRepository.class);
    DebtItemTypeRepository types = mock(DebtItemTypeRepository.class);
    DebtConfessionService service = new DebtConfessionService(pendencies, types);

    Driver driver = new Driver();
    Car car = new Car();
    DriverCar contract = new DriverCar();
    Car otherCar = new Car();
    DriverCar otherContract = new DriverCar();

    @BeforeEach
    void setup() {
        driver.setId(7L);
        driver.setName("Maria Souza");
        driver.setCpf("123.456.789-00");
        car.setId(3L);
        car.setPlate("ABC1D23");
        car.setModel("Onix");
        car.setDeleted(false);
        contract.setId(45L);
        contract.setCar(car);
        contract.setDriver(driver);
        contract.setContractNumber(" C-12 ");
        contract.setStartDate(LocalDate.of(2026, 8, 1));
        contract.setConcluded(false);
        otherCar.setId(4L);
        otherCar.setDeleted(false);
        otherContract.setId(46L);
        otherContract.setCar(otherCar);
        otherContract.setDriver(driver);
        when(types.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(type(1L, "Multa contratual"), type(2L, "Outros"), type(3L, "Danos/Avarias")));
    }

    @Test
    void previewUsesOnlyDatabaseValuesOfOneContract() {
        Pendency late = pendency(10L, contract, "Aluguel atrasado", "500.00", "150.00", "350.00", PendencyStatus.PARTIALLY_PAID, LocalDate.of(2026, 9, 1));
        late.setNote("Semana 35");
        Pendency damage = pendency(11L, contract, "danos/avarias", "200.00", "0", "200.00", PendencyStatus.OPEN, LocalDate.of(2026, 8, 20));
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 11L))).thenReturn(List.of(late, damage));

        DebtConfessionPreviewDTO preview = service.preview(List.of(10L, 11L));

        assertThat(preview.getDriverCarId()).isEqualTo(45L);
        assertThat(preview.getContractNumber()).isEqualTo("C-12");
        assertThat(preview.getContractStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(preview.getContractConcluded()).isFalse();
        assertThat(preview.getDriverId()).isEqualTo(7L);
        assertThat(preview.getDriverName()).isEqualTo("Maria Souza");
        assertThat(preview.getDriverCpf()).isEqualTo("123.456.789-00");
        assertThat(preview.getCarId()).isEqualTo(3L);
        assertThat(preview.getCarPlate()).isEqualTo("ABC1D23");
        assertThat(preview.getCarModel()).isEqualTo("Onix");
        assertThat(preview.getOrigemDaDivida())
            .isEqualTo("Pendências em aberto registradas no contrato nº C-12 iniciado em 01/08/2026, veículo placa ABC1D23.");
        assertThat(preview.getValorTotal()).isEqualByComparingTo("550.00");

        // Oldest first, whatever the request order.
        assertThat(preview.getItems()).extracting(DebtConfessionPreviewDTO.Item::getPendencyId).containsExactly(11L, 10L);
        DebtConfessionPreviewDTO.Item first = preview.getItems().get(0);
        assertThat(first.getTypeId()).isEqualTo(3L);
        assertThat(first.getTypeNameSnapshot()).isEqualTo("Danos/Avarias");
        assertThat(first.getDescricaoItem()).isEqualTo("danos/avarias (20/08/2026)");
        assertThat(first.getValorItem()).isEqualByComparingTo("200.00");

        DebtConfessionPreviewDTO.Item partial = preview.getItems().get(1);
        assertThat(partial.getStatus()).isEqualTo(PendencyStatus.PARTIALLY_PAID);
        assertThat(partial.getCost()).isEqualByComparingTo("500.00");
        assertThat(partial.getPaidAmount()).isEqualByComparingTo("150.00");
        assertThat(partial.getRemainingAmount()).isEqualByComparingTo("350.00");
        assertThat(partial.getValorItem()).isEqualByComparingTo("350.00");
        assertThat(partial.getTypeId()).isEqualTo(2L);
        assertThat(partial.getTypeNameSnapshot()).isEqualTo("Outros");
        assertThat(partial.getDescricaoItem())
            .isEqualTo("Aluguel atrasado (01/09/2026) - saldo remanescente; valor original R$ 500,00, já pago R$ 150,00. Semana 35");
    }

    @Test
    void typeIsNeverGuessedBySubstring() {
        Pendency fine = pendency(10L, contract, "Multa", "100", null, null, PendencyStatus.OPEN, null);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(fine));

        DebtConfessionPreviewDTO.Item item = service.preview(List.of(10L)).getItems().get(0);

        assertThat(item.getTypeId()).isEqualTo(2L);
        assertThat(item.getTypeNameSnapshot()).isEqualTo("Outros");
        assertThat(item.getDescricaoItem()).isEqualTo("Multa");
    }

    @Test
    void withoutAnOutrosTypeTheSnapshotStillFallsBackToOutros() {
        when(types.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(Collections.emptyList());
        Pendency other = pendency(10L, contract, null, "80", null, null, null, null);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(other));

        DebtConfessionPreviewDTO.Item item = service.preview(List.of(10L)).getItems().get(0);

        assertThat(item.getTypeId()).isNull();
        assertThat(item.getTypeNameSnapshot()).isEqualTo("Outros");
        assertThat(item.getDescricaoItem()).isEqualTo("Pendência");
    }

    @Test
    void legacyRowsWithoutBalanceUseCostMinusPaidLikeThePaymentFlow() {
        Pendency legacy = pendency(10L, contract, "Dano", "300.00", "100.00", null, null, null);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(legacy));

        assertThat(service.preview(List.of(10L)).getValorTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void minimalContractDataProducesASimpleOrigin() {
        contract.setContractNumber(null);
        contract.setStartDate(null);
        car.setPlate(" ");
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null)));

        DebtConfessionPreviewDTO preview = service.preview(List.of(10L));

        assertThat(preview.getContractNumber()).isNull();
        assertThat(preview.getOrigemDaDivida()).isEqualTo("Pendências em aberto registradas no contrato.");
    }

    @Test
    void idsOfOtherAccountsOrMissingIdsAreNotFoundWithoutSayingWhich() {
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 999L)))
            .thenReturn(List.of(pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null)));

        assertThatThrownBy(() -> service.preview(List.of(10L, 999L)))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                assertThat(failure.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(failure.getReason()).doesNotContain("999");
            });
    }

    @Test
    void sameDebtorAcrossContractsAndCarsIsOneConfessionWithEachItemsOrigin() {
        otherCar.setPlate("HB20X99");
        otherCar.setModel("HB20");
        otherContract.setContractNumber("C-46");
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 20L)))
            .thenReturn(
                List.of(
                    pendency(10L, contract, "Multa", "300.00", "0", "300.00", PendencyStatus.OPEN, LocalDate.of(2026, 8, 1)),
                    pendency(20L, otherContract, "Danos/Avarias", "400.00", "0", "400.00", PendencyStatus.OPEN, LocalDate.of(2026, 9, 1))
                )
            );

        DebtConfessionPreviewDTO preview = service.preview(List.of(10L, 20L));

        assertThat(preview.getDriverId()).isEqualTo(7L);
        assertThat(preview.getDriverName()).isEqualTo("Maria Souza");
        assertThat(preview.getValorTotal()).isEqualByComparingTo("700.00");
        // No single contract or car: nothing is presumed at the top...
        assertThat(preview.getDriverCarId()).isNull();
        assertThat(preview.getContractNumber()).isNull();
        assertThat(preview.getCarId()).isNull();
        assertThat(preview.getCarPlate()).isNull();
        assertThat(preview.getOrigemDaDivida())
            .isEqualTo(
                "Pendências em aberto registradas em nome do motorista, referentes aos contratos e veículos discriminados nos itens abaixo."
            );
        // ...each item keeps where it was born.
        DebtConfessionPreviewDTO.Item onix = preview.getItems().get(0);
        assertThat(onix.getOriginDriverCarId()).isEqualTo(45L);
        assertThat(onix.getOriginContractNumber()).isEqualTo("C-12");
        assertThat(onix.getOriginCarId()).isEqualTo(3L);
        assertThat(onix.getOriginCarPlate()).isEqualTo("ABC1D23");
        assertThat(onix.getOriginCarModel()).isEqualTo("Onix");
        DebtConfessionPreviewDTO.Item hb20 = preview.getItems().get(1);
        assertThat(hb20.getOriginDriverCarId()).isEqualTo(46L);
        assertThat(hb20.getOriginContractNumber()).isEqualTo("C-46");
        assertThat(hb20.getOriginCarPlate()).isEqualTo("HB20X99");
        assertThat(hb20.getValorItem()).isEqualByComparingTo("400.00");
    }

    @Test
    void sameDebtorTwoContractsOfTheSameCarKeepsTheCarButNotTheContract() {
        DriverCar again = new DriverCar();
        again.setId(47L);
        again.setCar(car);
        again.setDriver(driver);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 30L)))
            .thenReturn(
                List.of(
                    pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null),
                    pendency(30L, again, "Dano", "15", "0", "15", PendencyStatus.OPEN, null)
                )
            );

        DebtConfessionPreviewDTO preview = service.preview(List.of(10L, 30L));

        assertThat(preview.getCarId()).isEqualTo(3L);
        assertThat(preview.getCarPlate()).isEqualTo("ABC1D23");
        assertThat(preview.getDriverCarId()).isNull();
        assertThat(preview.getValorTotal()).isEqualByComparingTo("25");
    }

    @Test
    void pendenciesOfDifferentDebtorsAreRejectedEvenOnTheSameContract() {
        Driver other = new Driver();
        other.setId(8L);
        Pendency foreignDebtor = pendency(20L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null);
        foreignDebtor.setDebtor(other);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 20L)))
            .thenReturn(List.of(pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null), foreignDebtor));

        assertRejected(List.of(10L, 20L), "pendenciesdifferentdebtors");
    }

    @Test
    void theDebtorIsThePendencysFrozenDebtorNeverTheContractsCurrentDriver() {
        Driver replacement = new Driver();
        replacement.setId(99L);
        replacement.setName("Outra Pessoa");
        Pendency pendency = pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null);
        contract.setDriver(replacement);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(pendency));

        DebtConfessionPreviewDTO preview = service.preview(List.of(10L));

        assertThat(preview.getDriverId()).isEqualTo(7L);
        assertThat(preview.getDriverName()).isEqualTo("Maria Souza");
    }

    @Test
    void paidOrZeroBalancePendenciesAreRejected() {
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 11L)))
            .thenReturn(
                List.of(
                    pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null),
                    pendency(11L, contract, "Dano", "10", "10", "0", PendencyStatus.PAID, null)
                )
            );
        assertRejected(List.of(10L, 11L), "pendencynotopen");

        when(pendencies.findByCurrentUserAndIdIn(List.of(12L)))
            .thenReturn(List.of(pendency(12L, contract, "Dano", "0", "0", "0", PendencyStatus.OPEN, null)));
        assertRejected(List.of(12L), "pendencynotopen");
    }

    @Test
    void deletedVehicleIsRejected() {
        car.setDeleted(true);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L)))
            .thenReturn(List.of(pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null)));

        assertRejected(List.of(10L), "VEHICLE_DELETED");
    }

    @Test
    void pendencyWithoutDebtorIsRejected() {
        // Legacy rows recorded on a contract without driver have no debtor: never guessed.
        Pendency orphan = pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null);
        orphan.setDebtor(null);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(orphan));

        assertRejected(List.of(10L), "pendencywithoutdebtor");
    }

    @Test
    void anyDeletedOriginVehicleIsRejected() {
        otherCar.setDeleted(true);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L, 20L)))
            .thenReturn(
                List.of(
                    pendency(10L, contract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null),
                    pendency(20L, otherContract, "Dano", "10", "0", "10", PendencyStatus.OPEN, null)
                )
            );

        assertRejected(List.of(10L, 20L), "VEHICLE_DELETED");
    }

    @Test
    void invalidSelectionsAreRejectedBeforeQuerying() {
        assertRejected(null, "pendencyidsrequired");
        assertRejected(Collections.emptyList(), "pendencyidsrequired");
        assertRejected(Arrays.asList(10L, null), "pendencyidinvalid");
        assertRejected(List.of(10L, 10L), "pendencyidsduplicated");
        List<Long> tooMany = LongStream.rangeClosed(1, DebtConfessionService.MAX_PENDENCIES + 1).boxed().collect(Collectors.toList());
        assertRejected(tooMany, "toomanypendencies");

        verify(pendencies, never()).findByCurrentUserAndIdIn(any());
    }

    @Test
    void theMaximumSelectionIsAccepted() {
        List<Long> ids = LongStream.rangeClosed(1, DebtConfessionService.MAX_PENDENCIES).boxed().collect(Collectors.toList());
        List<Pendency> found = new ArrayList<>();
        ids.forEach(id -> found.add(pendency(id, contract, "Dano", "1", "0", "1", PendencyStatus.OPEN, null)));
        when(pendencies.findByCurrentUserAndIdIn(ids)).thenReturn(found);

        assertThat(service.preview(ids).getValorTotal()).isEqualByComparingTo("50");
    }

    @Test
    void previewNeverWritesPendencies() {
        Pendency late = pendency(10L, contract, "Aluguel atrasado", "500.00", "150.00", "350.00", PendencyStatus.PARTIALLY_PAID, null);
        when(pendencies.findByCurrentUserAndIdIn(List.of(10L))).thenReturn(List.of(late));

        service.preview(List.of(10L));

        verify(pendencies).findByCurrentUserAndIdIn(List.of(10L));
        verifyNoMoreInteractions(pendencies);
        assertThat(late.getStatus()).isEqualTo(PendencyStatus.PARTIALLY_PAID);
        assertThat(late.getPaidAmount()).isEqualByComparingTo("150.00");
        assertThat(late.getRemainingAmount()).isEqualByComparingTo("350.00");
        assertThat(late.getPaidAt()).isNull();
        assertThat(late.getPaymentMethod()).isNull();
        assertThat(late.getDriverCar()).isSameAs(contract);
    }

    private void assertRejected(List<Long> ids, String errorKey) {
        assertThatThrownBy(() -> service.preview(ids))
            .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo(errorKey));
    }

    private static DebtItemType type(Long id, String name) {
        DebtItemType type = new DebtItemType();
        type.setId(id);
        type.setName(name);
        type.setActive(true);
        return type;
    }

    private static Pendency pendency(
        Long id,
        DriverCar driverCar,
        String name,
        String cost,
        String paid,
        String remaining,
        PendencyStatus status,
        LocalDate date
    ) {
        Pendency pendency = new Pendency();
        pendency.setId(id);
        pendency.setDriverCar(driverCar);
        pendency.setDebtor(driverCar.getDriver());
        pendency.setName(name);
        pendency.setCost(cost == null ? null : new BigDecimal(cost));
        pendency.setPaidAmount(paid == null ? null : new BigDecimal(paid));
        pendency.setRemainingAmount(remaining == null ? null : new BigDecimal(remaining));
        pendency.setStatus(status);
        pendency.setDate(date);
        return pendency;
    }
}
