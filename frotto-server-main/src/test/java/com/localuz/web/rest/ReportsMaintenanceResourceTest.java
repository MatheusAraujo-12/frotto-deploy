package com.localuz.web.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localuz.domain.Car;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Service;
import com.localuz.repository.CarExpenseRepository;
import com.localuz.repository.CarHistoryRepository;
import com.localuz.repository.CarRepository;
import com.localuz.repository.IncomeRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.MaintenanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportsMaintenanceResourceTest {

    private CarRepository cars;
    private MaintenanceRepository maintenances;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cars = mock(CarRepository.class);
        maintenances = mock(MaintenanceRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new ReportsResource(
            cars, mock(IncomeRepository.class), mock(CarExpenseRepository.class),
            mock(InspectionRepository.class), maintenances, mock(CarHistoryRepository.class)
        )).build();
    }

    @Test
    void specificGroupKeepsExistingRequestAndYearFilter() throws Exception {
        when(cars.findActiveByCurrentUserAndGroup("Grupo A")).thenReturn(List.of(new Car().id(1L).name("Car A")));
        when(maintenances.findByCarIdAndYear(1L, 2026)).thenReturn(List.of());

        mvc.perform(get("/api/reports/maintenance").param("group", "Grupo A").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].carId").value(1));

        verify(cars).findActiveByCurrentUserAndGroup("Grupo A");
        verify(maintenances).findByCarIdAndYear(1L, 2026);
        verifyNoMoreInteractions(cars, maintenances);
    }

    @Test
    void allGroupsOnlyLoadsCarsFromCurrentUserScopeAndKeepsYear() throws Exception {
        when(cars.findActiveByCurrentUser()).thenReturn(List.of(
            new Car().id(1L).name("Car A"), new Car().id(2L).name("Car B"), new Car().id(3L).name("Ungrouped")
        ));
        Maintenance item = new Maintenance().date(LocalDate.of(2026, 6, 1))
            .services(Set.of(new Service().name("Oil").cost(BigDecimal.TEN)));
        when(maintenances.findByCarIdAndYear(1L, 2026)).thenReturn(List.of(item));

        mvc.perform(get("/api/reports/maintenance").param("allGroups", "true").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].maintenances[0].name").value("Oil"));

        verify(cars).findActiveByCurrentUser();
        verify(maintenances).findByCarIdAndYear(1L, 2026);
        verify(maintenances).findByCarIdAndYear(2L, 2026);
        verify(maintenances).findByCarIdAndYear(3L, 2026);
        verifyNoMoreInteractions(cars, maintenances);
    }

    @Test
    void missingOrEmptyGroupDoesNotImplicitlyRequestAllGroups() throws Exception {
        mvc.perform(get("/api/reports/maintenance").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/reports/maintenance").param("group", "").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(cars, maintenances);
    }

    @Test
    void allGroupsStillRequiresYear() throws Exception {
        mvc.perform(get("/api/reports/maintenance").param("allGroups", "true"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(cars, maintenances);
    }
}
