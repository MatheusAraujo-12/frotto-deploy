package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.*;
import com.localuz.repository.*;
import com.localuz.service.CarService;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InspectionHistoryProtectionTest {
    @Test void deletedVehicleHistoryCannotBeChangedByCreatingAnotherInspection() {
        var inspections = mock(InspectionRepository.class);
        var cars = mock(CarRepository.class);
        var damages = mock(CarBodyDamageRepository.class);
        var resource = new InspectionResource(inspections, cars, mock(TireRepository.class), mock(ExpenseRepository.class), mock(CarService.class), damages);
        Car operational = new Car().id(1L);
        Car deleted = new Car().id(2L); deleted.setDeleted(true);
        CarBodyDamage stored = new CarBodyDamage(); stored.setId(3L); stored.setCar(deleted);
        when(cars.findByCurrentUserAndId(1L)).thenReturn(Optional.of(operational));
        when(damages.findByCurrentUserAndCarBdId(3L)).thenReturn(Optional.of(stored));
        Inspection payload = new Inspection(); payload.setCarBodyDamages(Set.of(stored));
        assertThatThrownBy(() -> resource.createInspection(payload, 1L)).isInstanceOf(BadRequestAlertException.class);
        assertThat(stored.getCar()).isSameAs(deleted);
        verify(inspections, never()).save(any());
    }

    @Test void deletedVehicleCannotReceiveAnInspectionButHistoryRemainsReadable() {
        var inspections = mock(InspectionRepository.class);
        var cars = mock(CarRepository.class);
        var resource = new InspectionResource(inspections, cars, mock(TireRepository.class), mock(ExpenseRepository.class), mock(CarService.class), mock(CarBodyDamageRepository.class));
        Car deleted = new Car().id(2L); deleted.setDeleted(true);
        Inspection historical = new Inspection(); historical.setId(4L); historical.setCar(deleted);
        when(cars.findByCurrentUserAndId(2L)).thenReturn(Optional.of(deleted));
        when(inspections.findByCurrentUserAndInspectionId(4L)).thenReturn(Optional.of(historical));
        assertThatThrownBy(() -> resource.createInspection(new Inspection(), 2L)).isInstanceOf(BadRequestAlertException.class);
        assertThat(resource.getInspectionById(4L)).isSameAs(historical);
        verify(inspections, never()).save(any());
    }
    @Test void maintenanceCannotImportServiceIdsFromDeletedVehicleHistory() {
        var maintenances = mock(MaintenanceRepository.class);
        var cars = mock(CarRepository.class);
        var services = mock(ServiceRepository.class);
        var resource = new MaintenanceResource(maintenances, cars, mock(CarService.class), services);
        Car operational = new Car().id(1L);
        when(cars.findByCurrentUserAndId(1L)).thenReturn(Optional.of(operational));
        com.localuz.domain.Service historical = new com.localuz.domain.Service(); historical.setId(99L);
        Maintenance incoming = new Maintenance(); incoming.setServices(Set.of(historical));
        assertThatThrownBy(() -> resource.createMaintenance(1L, incoming)).isInstanceOf(BadRequestAlertException.class);
        verify(services, never()).saveAll(any());
        verify(maintenances, never()).save(any());
    }

}
