package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PendencyResourceDebtConfessionTest {

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    PendencyRepository pendencies = mock(PendencyRepository.class);
    DriverCarRepository driverCars = mock(DriverCarRepository.class);
    DriverRepository drivers = mock(DriverRepository.class);
    DebtConfessionService confessions = mock(DebtConfessionService.class);
    MockMvc mvc;

    @BeforeEach
    void setup() {
        PendencyResource resource = new PendencyResource(pendencies, driverCars, drivers, confessions);
        mvc = MockMvcBuilders.standaloneSetup(resource).setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test
    void previewDelegatesTheSelectedIdsAndAnswersTheServiceData() throws Exception {
        DebtConfessionPreviewDTO preview = new DebtConfessionPreviewDTO();
        preview.setDriverCarId(45L);
        preview.setCarPlate("ABC1D23");
        preview.setValorTotal(new BigDecimal("350.00"));
        DebtConfessionPreviewDTO.Item item = new DebtConfessionPreviewDTO.Item();
        item.setPendencyId(10L);
        item.setDate(LocalDate.of(2026, 9, 1));
        item.setValorItem(new BigDecimal("350.00"));
        preview.setItems(List.of(item));
        when(confessions.preview(List.of(10L, 11L))).thenReturn(preview);

        mvc
            .perform(
                post("/api/pendencies/confissao-divida/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"pendencyIds\":[10,11]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverCarId").value(45))
            .andExpect(jsonPath("$.carPlate").value("ABC1D23"))
            .andExpect(jsonPath("$.valorTotal").value(350.00))
            .andExpect(jsonPath("$.items[0].pendencyId").value(10))
            .andExpect(jsonPath("$.items[0].date").value("2026-09-01"));

        verify(confessions).preview(List.of(10L, 11L));
        verifyNoInteractions(pendencies, driverCars, drivers);
    }

    @Test
    void previewWithoutBodyIsDelegatedAsAnEmptySelection() throws Exception {
        when(confessions.preview(isNull())).thenReturn(new DebtConfessionPreviewDTO());

        mvc.perform(post("/api/pendencies/confissao-divida/preview").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        verify(confessions).preview(isNull());
    }

    @Test
    void pendencyJsonExposesItsContractButNeverTheContractEntity() throws Exception {
        DriverCar contract = new DriverCar();
        contract.setId(45L);
        Pendency pendency = new Pendency();
        pendency.setId(10L);
        pendency.setName("Dano");
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setDriverCar(contract);

        String json = mapper.writeValueAsString(pendency);

        assertThat(mapper.readTree(json).get("driverCarId").asLong()).isEqualTo(45L);
        assertThat(mapper.readTree(json).has("driverCar")).isFalse();
    }

    @Test
    void debtorIsExposedReadOnlyAndNeverAcceptedFromClients() throws Exception {
        com.localuz.domain.Driver joao = new com.localuz.domain.Driver();
        joao.setId(5L);
        joao.setName("João");
        Pendency pendency = new Pendency();
        pendency.setId(10L);
        pendency.setDebtor(joao);

        String json = mapper.writeValueAsString(pendency);
        assertThat(mapper.readTree(json).get("debtorDriverId").asLong()).isEqualTo(5L);
        assertThat(mapper.readTree(json).has("debtor")).isFalse();

        Pendency parsed = mapper
            .copy()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue("{\"id\":10,\"debtorDriverId\":8,\"debtor\":{\"id\":8}}", Pendency.class);
        assertThat(parsed.getDebtor()).isNull();
        assertThat(parsed.getDebtorDriverId()).isNull();
    }

    @Test
    void driverCarIdSentByClientsIsIgnored() throws Exception {
        Pendency parsed = mapper.readValue("{\"id\":10,\"name\":\"Dano\",\"driverCarId\":999}", Pendency.class);

        assertThat(parsed.getDriverCar()).isNull();
        assertThat(parsed.getDriverCarId()).isNull();
    }
}
