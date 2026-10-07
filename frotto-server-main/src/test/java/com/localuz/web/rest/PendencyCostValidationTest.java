package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverChargeService;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** A new common pendency is a debt: its value must be greater than zero. Legacy zero rows stay readable/editable. */
class PendencyCostValidationTest {

    private final PendencyRepository pendencies = mock(PendencyRepository.class);
    private final DriverCarRepository driverCars = mock(DriverCarRepository.class);
    private PendencyResource resource;

    @BeforeEach
    void setUp() {
        Driver joao = new Driver();
        joao.setId(5L);
        Car car = new Car();
        car.setId(10L);
        DriverCar contract = new DriverCar();
        contract.setId(100L);
        contract.setDriver(joao);
        contract.setCar(car);
        when(driverCars.findByCurrentUserAndId(100L)).thenReturn(Optional.of(contract));
        when(pendencies.save(any(Pendency.class))).thenAnswer(call -> {
            Pendency saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
        resource =
            new PendencyResource(pendencies, driverCars, mock(DriverRepository.class), mock(DebtConfessionService.class), mock(DriverChargeService.class));
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
    }

    private static Pendency request(String cost) {
        Pendency pendency = new Pendency();
        pendency.setName("Dano");
        pendency.setDate(LocalDate.of(2026, 10, 7));
        pendency.setCost(cost == null ? null : new BigDecimal(cost));
        return pendency;
    }

    @Test
    void cAndD_aNewPendencyOfZeroNegativeOrNoValueIsRefusedAndNothingIsSaved() {
        for (String cost : new String[] { "0", "0.00", "-0.01", "-150.00", null }) {
            assertThatThrownBy(() -> resource.createPendency(100L, request(cost)))
                .as("cost %s", cost)
                .isInstanceOfSatisfying(BadRequestAlertException.class, error -> {
                    assertThat(error.getErrorKey()).isEqualTo("costinvalid");
                    assertThat(error.getMessage()).isEqualTo("O valor da pendência deve ser maior que zero.");
                });
        }
        verify(pendencies, never()).save(any());
    }

    @Test
    void b_aNewPendencyWithAValueIsSavedAsBefore() throws Exception {
        Pendency created = resource.createPendency(100L, request("150.00")).getBody();

        assertThat(created.getCost()).isEqualByComparingTo("150.00");
        assertThat(created.getRemainingAmount()).isEqualByComparingTo("150.00");
        assertThat(created.getStatus()).isEqualTo(PendencyStatus.OPEN);
        assertThat(created.getDebtorDriverId()).isEqualTo(5L);
    }

    @Test
    void e_aLegacyZeroPendencyStaysReadableAndEditable() throws Exception {
        Pendency legacy = request("0.00");
        legacy.setId(7L);
        legacy.setStatus(PendencyStatus.PAID);
        legacy.setPaidAmount(BigDecimal.ZERO);
        legacy.setRemainingAmount(BigDecimal.ZERO);
        legacy.setDriverCar(driverCars.findByCurrentUserAndId(100L).orElseThrow());
        when(pendencies.findByCurrentUserAndPendencyId(7L)).thenReturn(Optional.of(legacy));

        assertThat(resource.getPendencyById(7L).getCost()).isEqualByComparingTo("0.00");

        Pendency edit = request("0.00");
        edit.setId(7L);
        edit.setNote("conferido");
        Pendency updated = resource.updatePendency(7L, edit).getBody();
        assertThat(updated.getNote()).isEqualTo("conferido");
        assertThat(updated.getCost()).isEqualByComparingTo("0.00");
    }
}
