package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.localuz.domain.Car;
import com.localuz.domain.DebtItemType;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Pendency;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.UserService;
import com.localuz.service.dto.DocumentDTO;
import com.localuz.service.dto.DocumentSaveDTO;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.DebtConfessionOutdatedException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Confissão de Dívida originated from pendencies (Etapa 2). Real {@link DebtConfessionService}; repositories answer
 * like the ownership-filtered queries (only the current user's rows).
 *
 * Car 10 (ABC1D23) history: João (driver 5) in driver_car 100 (Jan-Mar, concluded) and again in driver_car 300
 * (May-Jun, concluded); Maria (driver 8) in driver_car 200 (since Jul, active = current driver of the car).
 */
class DocumentResourceDebtConfessionTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<Long, Pendency> ownedPendencies = new HashMap<>();
    private final Map<Long, DriverDocument> storedDocuments = new HashMap<>();
    private final List<DriverCar> driverCars = new ArrayList<>();

    private PendencyRepository pendencies;
    private DriverDocumentRepository documents;
    private DocumentResource resource;

    private Driver joao;
    private Driver maria;
    private Car car10;
    private Car car11;
    private DriverCar contract100;
    private DriverCar contract200;
    private DriverCar contract300;

    @BeforeEach
    void setUp() {
        joao = driver(5L, "João Silva", "111.111.111-11");
        maria = driver(8L, "Maria Souza", "222.222.222-22");
        car10 = car(10L, "ABC1D23");
        car11 = car(11L, "XYZ9Z99");
        contract100 = contract(100L, joao, car10, LocalDate.of(2026, 1, 1), true);
        contract300 = contract(300L, joao, car10, LocalDate.of(2026, 5, 1), true);
        contract200 = contract(200L, maria, car10, LocalDate.of(2026, 7, 1), false);
        contract100.setContractNumber("C-100");

        own(pendency(1000L, contract100, "Aluguel atrasado", "500.00", "0", "500.00", PendencyStatus.OPEN, LocalDate.of(2026, 3, 1)));
        own(pendency(1001L, contract100, "Danos/Avarias", "300.00", "100.00", "200.00", PendencyStatus.PARTIALLY_PAID, LocalDate.of(2026, 3, 15)));
        own(pendency(2000L, contract200, "Aluguel atrasado", "450.00", "0", "450.00", PendencyStatus.OPEN, LocalDate.of(2026, 8, 1)));
        own(pendency(2001L, contract200, "Multa", "130.00", "0", "130.00", PendencyStatus.OPEN, LocalDate.of(2026, 8, 5)));
        own(pendency(3000L, contract300, "Combustível", "90.00", "0", "90.00", PendencyStatus.OPEN, LocalDate.of(2026, 6, 1)));
        // Pendency 9000 belongs to another fleet owner: the ownership query never returns it.

        pendencies = mock(PendencyRepository.class);
        when(pendencies.findByCurrentUserAndIdIn(any()))
            .thenAnswer(call ->
                ((Collection<?>) call.getArgument(0)).stream()
                    .map(ownedPendencies::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList())
            );
        when(pendencies.save(any(Pendency.class))).thenAnswer(call -> call.getArgument(0));

        DebtItemTypeRepository types = mock(DebtItemTypeRepository.class);
        when(types.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(type(1L, "Danos/Avarias"), type(2L, "Outros")));

        documents = mock(DriverDocumentRepository.class);
        when(documents.save(any(DriverDocument.class)))
            .thenAnswer(call -> {
                DriverDocument saved = call.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(70L + storedDocuments.size());
                }
                storedDocuments.put(saved.getId(), saved);
                return saved;
            });
        when(documents.findByCurrentUserAndId(anyLong())).thenAnswer(call -> Optional.ofNullable(storedDocuments.get(call.<Long>getArgument(0))));

        DriverRepository drivers = mock(DriverRepository.class);
        when(drivers.findByCurrentUserAndId(5L)).thenReturn(Optional.of(joao));
        when(drivers.findByCurrentUserAndId(8L)).thenReturn(Optional.of(maria));
        CarRepository cars = mock(CarRepository.class);
        when(cars.findByCurrentUserAndId(10L)).thenReturn(Optional.of(car10));
        when(cars.findByCurrentUserAndId(11L)).thenReturn(Optional.of(car11));

        DriverCarRepository driverCarRepository = mock(DriverCarRepository.class);
        when(driverCarRepository.findActiveByCurrentUserAndDriverAndCar(anyLong(), anyLong()))
            .thenAnswer(call -> contractsOf(call.getArgument(0), call.getArgument(1), true));
        when(driverCarRepository.findByCurrentUserAndDriverAndCarOrderByStartDateDesc(anyLong(), anyLong()))
            .thenAnswer(call -> contractsOf(call.getArgument(0), call.getArgument(1), false));

        UserService users = mock(UserService.class);
        when(users.getUserWithAuthorities()).thenReturn(Optional.of(new User()));

        resource =
            new DocumentResource(
                documents,
                drivers,
                cars,
                driverCarRepository,
                pendencies,
                mock(FileStorageGateway.class),
                users,
                objectMapper,
                new DebtConfessionService(pendencies, types)
            );
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
    }

    @Nested
    class Create {

        @Test
        void storesTheServerRebuiltOriginWithItemsSnapshotAndTotals() {
            DocumentDTO created = createOrigin(5L, 10L, 100L, List.of(1001L, 1000L), Map.of(1000L, "500.00", 1001L, "200.00"));

            assertThat(created.getStatus()).isEqualTo(DocumentStatus.DRAFT);
            assertThat(created.getDriverId()).isEqualTo(5L);
            assertThat(created.getCarId()).isEqualTo(10L);
            Map<String, Object> payload = stored(created);
            assertThat(payload.get("driverName")).isEqualTo("João Silva");
            assertThat(payload.get("driverCpf")).isEqualTo("111.111.111-11");
            assertThat(payload.get("carPlate")).isEqualTo("ABC1D23");
            assertThat(terms(payload))
                .containsExactly(
                    entry("formaPagamento", "PARCELADO"),
                    entry("prazoPagamento", "2027-03-10"),
                    entry("parcelasQtd", 2),
                    entry("primeiroVencimento", "2026-11-10"),
                    entry("observacao", "Acordo em duas vezes")
                );
            assertThat(payload).doesNotContainKeys("testemunha1Nome", "testemunha2Nome", "formaPagamento");
            assertThat(money(payload.get("valorTotal"))).isEqualByComparingTo("700.00");

            List<Map<String, Object>> items = items(payload);
            assertThat(items).extracting(item -> ((Number) item.get("sourcePendencyId")).longValue()).containsExactly(1000L, 1001L);
            assertThat(money(items.get(1).get("valorItem"))).isEqualByComparingTo("200.00");
            assertThat(items.get(1).get("typeNameSnapshot")).isEqualTo("Danos/Avarias");

            Map<String, Object> origin = origin(payload);
            assertThat(origin.get("tipo")).isEqualTo("PENDENCIAS");
            assertThat(origin.get("versao")).isEqualTo(2);
            assertThat(((Number) origin.get("driverCarId")).longValue()).isEqualTo(100L);
            assertThat(((Number) origin.get("driverId")).longValue()).isEqualTo(5L);
            assertThat(((Number) origin.get("carId")).longValue()).isEqualTo(10L);
            assertThat(origin.get("contractNumber")).isEqualTo("C-100");
            assertThat(((List<?>) origin.get("pendencyIds")).stream().map(id -> ((Number) id).longValue())).containsExactly(1000L, 1001L);
            List<Map<String, Object>> snapshots = list(origin.get("pendencias"));
            assertThat(snapshots.get(1))
                .containsEntry("id", 1001)
                .containsEntry("status", "PARTIALLY_PAID")
                .containsEntry("date", "2026-03-15");
            assertThat(money(snapshots.get(1).get("paidAmount"))).isEqualByComparingTo("100.00");
            assertThat(money(origin.get("valorTotal"))).isEqualByComparingTo("700.00");
            assertThat(origin.get("snapshotEm")).isNotNull();
        }

        @Test
        void onlyAConfissaoDeDividaCanHaveAnOriginAndItStartsAsDraft() {
            DocumentSaveDTO multa = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            multa.setType(DocumentType.MULTA);
            assertRejected(() -> create(multa), "confessionorigininvalid");

            DocumentSaveDTO finalDirectly = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            finalDirectly.setStatus(DocumentStatus.FINAL);
            assertRejected(() -> create(finalDirectly), "confessionmustbedraft");

            DocumentSaveDTO unknownOrigin = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            origin(unknownOrigin.getPayload()).put("tipo", "MANUAL");
            assertRejected(() -> create(unknownOrigin), "confessionorigininvalid");

            assertThat(storedDocuments).isEmpty();
        }

        @Test
        void driverIsRequiredSoTheCarAloneNeverDefinesTheDebtor() {
            // P: only the car is informed; the server never infers the driver (and never the car's current driver).
            assertRejected(() -> create(originRequest(null, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"))), "driverrequired");
            assertRejected(() -> create(originRequest(5L, null, 100L, List.of(1000L), Map.of(1000L, "500.00"))), "confessionoriginmismatch");
        }
    }

    @Nested
    class Identity {

        @Test
        void driverOtherThanTheContractDriverIsRejected() {
            // F: Maria uses car 10 today, but pendency 1000 is João's (driver_car 100).
            assertRejected(() -> createOrigin(8L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
        }

        @Test
        void carOtherThanTheContractCarIsRejected() {
            // G
            assertRejected(() -> createOrigin(5L, 11L, 100L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
        }

        @Test
        void tamperedDriverCarIdIsRejected() {
            // H: claim Maria's contract for João's pendency, or João's other contract.
            assertRejected(() -> createOrigin(5L, 10L, 200L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
            assertRejected(() -> createOrigin(8L, 10L, 200L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
            assertRejected(() -> createOrigin(5L, 10L, 300L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
        }

        @Test
        void claimsInsideTheOriginMustAlsoMatch() {
            DocumentSaveDTO request = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            origin(request.getPayload()).put("driverId", 8);
            assertRejected(() -> create(request), "confessionoriginmismatch");

            DocumentSaveDTO carClaim = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            origin(carClaim.getPayload()).put("carId", 11);
            assertRejected(() -> create(carClaim), "confessionoriginmismatch");
        }

        @Test
        void pendencyOfAnotherAccountStaysUnreachable() {
            // I
            assertThatThrownBy(() -> createOrigin(5L, 10L, 100L, List.of(1000L, 9000L), Map.of(1000L, "500.00", 9000L, "1.00")))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThat(storedDocuments).isEmpty();
        }

        @Test
        void sameCarDifferentDriversNeverMix() {
            // M: car 10 is shared by driver_car 100 (João) and 200 (Maria).
            assertRejected(
                () -> createOrigin(5L, 10L, 100L, List.of(1000L, 2000L), Map.of(1000L, "500.00", 2000L, "450.00")),
                "pendenciesdifferentdebtors"
            );
            assertRejected(() -> createOrigin(5L, 10L, 100L, List.of(2000L), Map.of(2000L, "450.00")), "confessionoriginmismatch");
            // And the inverse.
            assertRejected(() -> createOrigin(8L, 10L, 200L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
            assertRejected(
                () -> createOrigin(8L, 10L, 200L, List.of(2000L, 1001L), Map.of(2000L, "450.00", 1001L, "200.00")),
                "pendenciesdifferentdebtors"
            );
            assertThat(storedDocuments).isEmpty();

            DocumentDTO maria = createOrigin(8L, 10L, 200L, List.of(2000L, 2001L), Map.of(2000L, "450.00", 2001L, "130.00"));
            assertThat(stored(maria).get("driverName")).isEqualTo("Maria Souza");
            assertThat(items(stored(maria))).extracting(item -> ((Number) item.get("sourcePendencyId")).longValue()).containsExactly(2000L, 2001L);
        }

        @Test
        void historicalConfessionStaysWithTheHistoricalDriver() {
            // N: João's contract 100 ended, Maria drives car 10 now.
            DocumentDTO created = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            DocumentDTO finalized = resource.finalizeDocument(created.getId()).getBody();

            assertThat(finalized.getStatus()).isEqualTo(DocumentStatus.FINAL);
            assertThat(finalized.getDriverId()).isEqualTo(5L);
            assertThat(finalized.getDriverName()).isEqualTo("João Silva");
            assertThat(stored(finalized).get("driverName")).isEqualTo("João Silva");
            assertThat(((Number) origin(stored(finalized)).get("driverId")).longValue()).isEqualTo(5L);
            assertThat(ownedPendencies.get(1000L).getDriverCar()).isSameAs(contract100);
            // The car's current driver cannot take it over.
            assertRejected(() -> createOrigin(8L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")), "confessionoriginmismatch");
        }

        @Test
        void sameDriverTwoContractsOfTheSameCarAreOneConfession() {
            // Etapa 2.2 (14/15): the debtor is João in both contracts 100 and 300 of car 10.
            DocumentDTO created = createOrigin(5L, 10L, null, List.of(1000L, 3000L), Map.of(1000L, "500.00", 3000L, "90.00"));

            Map<String, Object> origin = origin(stored(created));
            assertThat(created.getCarId()).isEqualTo(10L);
            assertThat(origin.get("driverCarId")).isNull();
            assertThat(((Number) origin.get("carId")).longValue()).isEqualTo(10L);
            assertThat(money(stored(created).get("valorTotal"))).isEqualByComparingTo("590.00");
            assertThat(list(origin.get("pendencias"))).extracting(p -> ((Number) p.get("driverCarId")).longValue()).containsExactly(100L, 300L);
            // A single-contract claim does not fit two contracts.
            assertRejected(() -> createOrigin(5L, 10L, 100L, List.of(1000L, 3000L), Map.of(1000L, "500.00", 3000L, "90.00")), "confessionoriginmismatch");
        }

        @Test
        void sameDriverDifferentCarsAreOneConfessionWithEachItemsOrigin() {
            // Etapa 2.2 (16/19/20): João's debts born in car 10 (Onix) and car 11 (HB20).
            DriverCar contract400 = contract(400L, joao, car11, LocalDate.of(2026, 9, 1), false);
            own(pendency(4000L, contract400, "Danos/Avarias", "400.00", "0", "400.00", PendencyStatus.OPEN, LocalDate.of(2026, 9, 10)));

            DocumentDTO created = createOrigin(5L, null, null, List.of(1000L, 1001L, 4000L), Map.of(1000L, "500.00", 1001L, "200.00", 4000L, "400.00"));

            Map<String, Object> payload = stored(created);
            assertThat(created.getCarId()).isNull();
            assertThat(created.getDriverId()).isEqualTo(5L);
            assertThat(payload.get("carPlate")).isNull();
            assertThat(money(payload.get("valorTotal"))).isEqualByComparingTo("1100.00");
            assertThat(payload.get("origemDaDivida"))
                .isEqualTo(
                    "Pendências em aberto registradas em nome do motorista, referentes aos contratos e veículos discriminados nos itens abaixo."
                );
            Map<String, Object> origin = origin(payload);
            assertThat(origin.get("carId")).isNull();
            assertThat(origin.get("driverCarId")).isNull();
            List<Map<String, Object>> snapshots = list(origin.get("pendencias"));
            assertThat(snapshots).extracting(p -> p.get("carPlate")).containsExactly("ABC1D23", "ABC1D23", "XYZ9Z99");
            assertThat(snapshots).extracting(p -> p.get("contractNumber")).containsExactly("C-100", "C-100", null);
            assertThat(resource.finalizeDocument(created.getId()).getBody().getStatus()).isEqualTo(DocumentStatus.FINAL);
            verify(pendencies, never()).save(any());

            // Several cars: the document cannot pretend to belong to one of them.
            assertRejected(
                () -> createOrigin(5L, 10L, null, List.of(1000L, 4000L), Map.of(1000L, "500.00", 4000L, "400.00")),
                "confessionoriginmismatch"
            );
        }

        @Test
        void theDebtorStaysFrozenWhateverHappensToTheContractOrTheCar() {
            // Even if the contract's driver were replaced (no API allows it since Etapa 2.1), the debt stays João's.
            contract100.setDriver(maria);

            DocumentDTO created = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));

            assertThat(stored(created).get("driverName")).isEqualTo("João Silva");
            assertRejected(() -> createOrigin(8L, 10L, 100L, List.of(1001L), Map.of(1001L, "200.00")), "confessionoriginmismatch");
        }
    }

    @Nested
    class Tampering {

        @Test
        void serverValuesReplaceEverythingIdentifyingOrDescriptive() {
            // K
            DocumentSaveDTO request = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            Map<String, Object> payload = request.getPayload();
            payload.put("driverName", "Maria Souza");
            payload.put("driverCpf", "222.222.222-22");
            payload.put("carPlate", "ZZZ0Z00");
            payload.put("carModel", "Outro");
            payload.put("tipoItem", "Legado");
            payload.put("valorItem", 1);
            Map<String, Object> item = items(payload).get(0);
            item.put("descricaoItem", "Dívida de R$ 1.000.000,00");
            item.put("typeNameSnapshot", "Multa de trânsito");
            item.put("typeId", 99);
            Map<String, Object> clientOrigin = origin(payload);
            clientOrigin.put("pendencias", List.of(Map.of("id", 1000, "remainingAmount", 1, "status", "PAID")));
            clientOrigin.put("contractNumber", "FALSO");
            clientOrigin.put("versao", 99);

            Map<String, Object> saved = stored(create(request).getBody());

            assertThat(saved.get("driverName")).isEqualTo("João Silva");
            assertThat(saved.get("driverCpf")).isEqualTo("111.111.111-11");
            assertThat(saved.get("carPlate")).isEqualTo("ABC1D23");
            assertThat(saved).doesNotContainKeys("tipoItem", "valorItem");
            Map<String, Object> savedItem = items(saved).get(0);
            assertThat(savedItem.get("descricaoItem")).isEqualTo("Aluguel atrasado (01/03/2026)");
            assertThat(savedItem.get("typeNameSnapshot")).isEqualTo("Outros");
            assertThat(savedItem.get("typeId")).isEqualTo(2);
            Map<String, Object> savedOrigin = origin(saved);
            assertThat(savedOrigin.get("contractNumber")).isEqualTo("C-100");
            assertThat(savedOrigin.get("versao")).isEqualTo(2);
            assertThat(list(savedOrigin.get("pendencias")).get(0)).containsEntry("status", "OPEN");
            assertThat(money(list(savedOrigin.get("pendencias")).get(0).get("remainingAmount"))).isEqualByComparingTo("500.00");
        }

        @Test
        void valuesThatDifferFromTheDatabaseAreRejected() {
            assertOutdated(() -> createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "1.00")));
            DocumentSaveDTO total = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            total.getPayload().put("valorTotal", 1);
            assertOutdated(() -> create(total));
            assertThat(storedDocuments).isEmpty();
        }

        @Test
        void floatingPointTotalsOfTheBrowserAreAccepted() {
            DocumentSaveDTO request = originRequest(5L, 10L, 100L, List.of(1000L, 1001L), Map.of(1000L, "500.00", 1001L, "200.00"));
            request.getPayload().put("valorTotal", 700.0000000001d);
            assertThat(create(request).getBody().getId()).isNotNull();
        }

        @Test
        void itemsMustBeExactlyTheSelectedPendencies() {
            DocumentSaveDTO otherSource = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            items(otherSource.getPayload()).get(0).put("sourcePendencyId", 2000);
            assertRejected(() -> create(otherSource), "confessionoriginmismatch");

            DocumentSaveDTO extraItem = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            items(extraItem.getPayload()).add(new LinkedHashMap<>(Map.of("sourcePendencyId", 1001, "valorItem", 200)));
            assertRejected(() -> create(extraItem), "confessionoriginmismatch");

            DocumentSaveDTO manualItem = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            items(manualItem.getPayload()).add(new LinkedHashMap<>(Map.of("typeNameSnapshot", "Outros", "valorItem", 50)));
            assertRejected(() -> create(manualItem), "confessionorigininvalid");

            DocumentSaveDTO duplicated = originRequest(5L, 10L, 100L, List.of(1000L, 1000L), Map.of(1000L, "500.00"));
            assertRejected(() -> create(duplicated), "pendencyidsduplicated");

            DocumentSaveDTO noItems = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            noItems.getPayload().remove("itensDaDivida");
            assertRejected(() -> create(noItems), "confessionorigininvalid");
        }

        @Test
        void manualConfessionCannotPretendToComeFromPendencies() {
            DocumentSaveDTO manual = manualRequest("250.00");
            items(manual.getPayload()).get(0).put("sourcePendencyId", 1000);
            assertRejected(() -> create(manual), "sourcependencywithoutorigin");
        }
    }

    @Nested
    class Patch {

        @Test
        void contextOriginAndPendenciesCannotChange() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            String before = storedDocuments.get(id).getPayloadJson();

            DocumentSaveDTO driver = new DocumentSaveDTO();
            driver.setDriverId(8L);
            assertRejected(() -> resource.updateDocumentDraft(id, driver), "confessionoriginimmutable");
            DocumentSaveDTO car = new DocumentSaveDTO();
            car.setCarId(11L);
            assertRejected(() -> resource.updateDocumentDraft(id, car), "confessionoriginimmutable");
            DocumentSaveDTO type = new DocumentSaveDTO();
            type.setType(DocumentType.MULTA);
            assertRejected(() -> resource.updateDocumentDraft(id, type), "confessionoriginimmutable");
            DocumentSaveDTO status = new DocumentSaveDTO();
            status.setStatus(DocumentStatus.FINAL);
            assertRejected(() -> resource.updateDocumentDraft(id, status), "confessionfinalizerequired");

            assertRejected(() -> resource.updateDocumentDraft(id, payloadOnly(originRequest(5L, 10L, 100L, List.of(1000L, 1001L), Map.of(1000L, "500.00", 1001L, "200.00")))), "confessionoriginimmutable");
            assertRejected(() -> resource.updateDocumentDraft(id, payloadOnly(originRequest(5L, 10L, 300L, List.of(3000L), Map.of(3000L, "90.00")))), "confessionoriginimmutable");
            assertRejected(() -> resource.updateDocumentDraft(id, payloadOnly(manualRequest("500.00"))), "confessionoriginimmutable");

            DriverDocument document = storedDocuments.get(id);
            assertThat(document.getPayloadJson()).isEqualTo(before);
            assertThat(document.getDriver()).isSameAs(joao);
            assertThat(document.getCar()).isSameAs(car10);
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        }

        @Test
        void sameSelectionUpdatesUserFieldsAndIsRebuiltFromTheDatabase() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            DocumentSaveDTO patch = payloadOnly(originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")));
            terms(patch.getPayload()).put("parcelasQtd", 5);
            patch.getPayload().put("driverName", "Maria Souza");
            items(patch.getPayload()).get(0).put("descricaoItem", "outra coisa");

            Map<String, Object> payload = stored(resource.updateDocumentDraft(id, patch).getBody());

            assertThat(terms(payload).get("parcelasQtd")).isEqualTo(5);
            assertThat(payload.get("driverName")).isEqualTo("João Silva");
            assertThat(items(payload).get(0).get("descricaoItem")).isEqualTo("Aluguel atrasado (01/03/2026)");
        }

        @Test
        void staleValuesInAPatchAreRejected() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            ownedPendencies.get(1000L).setPaidAmount(new BigDecimal("200.00"));
            ownedPendencies.get(1000L).setRemainingAmount(new BigDecimal("300.00"));
            ownedPendencies.get(1000L).setStatus(PendencyStatus.PARTIALLY_PAID);

            assertOutdated(() -> resource.updateDocumentDraft(id, payloadOnly(originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")))));
            // Refreshed with the current balance shown to the user: accepted.
            Map<String, Object> refreshed = stored(
                resource.updateDocumentDraft(id, payloadOnly(originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "300.00")))).getBody()
            );
            assertThat(money(refreshed.get("valorTotal"))).isEqualByComparingTo("300.00");
        }

        @Test
        void manualDraftCannotBeTurnedIntoAnOriginConfession() {
            Long id = create(manualRequest("250.00")).getBody().getId();
            assertRejected(
                () -> resource.updateDocumentDraft(id, payloadOnly(originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")))),
                "confessionoriginimmutable"
            );
        }
    }

    @Nested
    class Finalize {

        @Test
        void neverCreatesNorChangesAnyPendency() {
            // A + B
            Map<Long, String> before = pendencyStates();
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L, 1001L), Map.of(1000L, "500.00", 1001L, "200.00")).getId();

            DocumentDTO finalized = resource.finalizeDocument(id).getBody();

            assertThat(finalized.getStatus()).isEqualTo(DocumentStatus.FINAL);
            verify(pendencies, atLeastOnce()).findByCurrentUserAndIdIn(any());
            verifyNoMoreInteractions(pendencies);
            assertThat(pendencyStates()).isEqualTo(before);
        }

        @Test
        void pendencyPaidAfterTheDraftBlocksTheFinalizationAtomically() {
            // D
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L, 1001L), Map.of(1000L, "500.00", 1001L, "200.00")).getId();
            String payloadBefore = storedDocuments.get(id).getPayloadJson();
            clearInvocations(documents);
            Pendency paid = ownedPendencies.get(1001L);
            paid.setPaidAmount(new BigDecimal("300.00"));
            paid.setRemainingAmount(BigDecimal.ZERO);
            paid.setStatus(PendencyStatus.PAID);
            paid.setPaidAt(Instant.now());

            assertOutdated(() -> resource.finalizeDocument(id));

            assertThat(storedDocuments.get(id).getStatus()).isEqualTo(DocumentStatus.DRAFT);
            assertThat(storedDocuments.get(id).getPayloadJson()).isEqualTo(payloadBefore);
            verify(documents, never()).save(any());
            verify(pendencies, never()).save(any());
        }

        @Test
        void balanceChangedAfterTheDraftIsDetected() {
            // E
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            ownedPendencies.get(1000L).setPaidAmount(new BigDecimal("200.00"));
            ownedPendencies.get(1000L).setRemainingAmount(new BigDecimal("300.00"));
            ownedPendencies.get(1000L).setStatus(PendencyStatus.PARTIALLY_PAID);

            assertOutdated(() -> resource.finalizeDocument(id));
            assertThat(storedDocuments.get(id).getStatus()).isEqualTo(DocumentStatus.DRAFT);
        }

        @Test
        void costEditedWithTheSameBalanceIsStillDetected() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1001L), Map.of(1001L, "200.00")).getId();
            ownedPendencies.get(1001L).setCost(new BigDecimal("250.00"));
            ownedPendencies.get(1001L).setPaidAmount(new BigDecimal("50.00"));

            assertOutdated(() -> resource.finalizeDocument(id));
        }

        @Test
        void deletedPendencyBlocksTheFinalization() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L, 1001L), Map.of(1000L, "500.00", 1001L, "200.00")).getId();
            ownedPendencies.remove(1001L);

            assertOutdated(() -> resource.finalizeDocument(id));
        }

        @Test
        void debtorRenamedAfterTheDraftBlocksButAReplacedContractDriverDoesNotMoveTheDebt() {
            Long replaced = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            Long renamed = createOrigin(5L, 10L, 100L, List.of(1001L), Map.of(1001L, "200.00")).getId();

            joao.setName("Outra Pessoa");
            assertOutdated(() -> resource.finalizeDocument(renamed));
            joao.setName("João Silva");

            // Etapa 2.2: the debtor is frozen on the pendency; a contract driver change (impossible through the API) does not move it.
            contract100.setDriver(maria);
            DocumentDTO finalized = resource.finalizeDocument(replaced).getBody();
            assertThat(finalized.getStatus()).isEqualTo(DocumentStatus.FINAL);
            assertThat(finalized.getDriverId()).isEqualTo(5L);
        }

        @Test
        void tamperedStoredSnapshotIsDetected() throws Exception {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            DriverDocument document = storedDocuments.get(id);
            Map<String, Object> payload = objectMapper.readValue(document.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            items(payload).get(0).put("valorItem", 1);
            document.setPayloadJson(objectMapper.writeValueAsString(payload));

            assertOutdated(() -> resource.finalizeDocument(id));
        }

        @Test
        void alreadyFinalConfessionIsNotRevalidatedAgain() {
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            resource.finalizeDocument(id);
            // Paying the debt after the confession is the normal course: the document stays finalized.
            ownedPendencies.get(1000L).setStatus(PendencyStatus.PAID);
            ownedPendencies.get(1000L).setRemainingAmount(BigDecimal.ZERO);

            assertThat(resource.finalizeDocument(id).getBody().getStatus()).isEqualTo(DocumentStatus.FINAL);
            verify(pendencies, never()).save(any());
        }
    }

    @Nested
    class Legacy {

        @Test
        void manualConfessionKeepsCreatingTheConsolidatedPendencyOnTheDocumentDriversContract() {
            // C + P: Maria drives car 10 now; the manual confession of João goes to João's latest contract (300),
            // exactly as before this change.
            DocumentDTO created = create(manualRequest("250.00")).getBody();
            Map<String, Object> payload = stored(created);
            assertThat(payload).doesNotContainKey("origem");
            assertThat(payload.get("driverName")).isEqualTo("Nome digitado");

            resource.finalizeDocument(created.getId());

            ArgumentCaptor<Pendency> captor = ArgumentCaptor.forClass(Pendency.class);
            verify(pendencies).save(captor.capture());
            Pendency consolidated = captor.getValue();
            assertThat(consolidated.getName()).isEqualTo("Confissão de dívida");
            assertThat(consolidated.getCost()).isEqualByComparingTo("250.00");
            assertThat(consolidated.getStatus()).isEqualTo(PendencyStatus.OPEN);
            assertThat(consolidated.getDriverCar()).isSameAs(contract300);
            assertThat(consolidated.getDriverCar().getDriver()).isSameAs(joao);
            assertThat(consolidated.getDebtor()).isSameAs(joao);
        }

        @Test
        void storedLegacyPayloadsKeepWorking() throws Exception {
            // L: pre-itensDaDivida format and a document without payload.
            DriverDocument legacy = new DriverDocument();
            legacy.setType(DocumentType.CONFISSAO_DIVIDA);
            legacy.setStatus(DocumentStatus.DRAFT);
            legacy.setDriver(joao);
            legacy.setCar(car10);
            legacy.setPayloadJson("{\"tipoItem\":\"Danos\",\"valorTotal\":\"1.234,50\",\"origemDaDivida\":\"Batida\"}");
            Long legacyId = documents.save(legacy).getId();
            DriverDocument empty = new DriverDocument();
            empty.setType(DocumentType.CONFISSAO_DIVIDA);
            empty.setStatus(DocumentStatus.DRAFT);
            empty.setDriver(joao);
            Long emptyId = documents.save(empty).getId();

            DocumentSaveDTO edit = new DocumentSaveDTO();
            edit.setPayload(new LinkedHashMap<>(Map.of("tipoItem", "Danos", "valorTotal", 1234.5, "observacoes", "editado")));
            assertThat(stored(resource.updateDocumentDraft(legacyId, edit).getBody())).containsEntry("observacoes", "editado");
            assertThat(resource.finalizeDocument(legacyId).getBody().getStatus()).isEqualTo(DocumentStatus.FINAL);
            assertThat(resource.finalizeDocument(emptyId).getBody().getStatus()).isEqualTo(DocumentStatus.FINAL);

            ArgumentCaptor<Pendency> captor = ArgumentCaptor.forClass(Pendency.class);
            verify(pendencies).save(captor.capture());
            assertThat(captor.getValue().getCost()).isEqualByComparingTo("1234.50");
        }

        @Test
        void legacyDraftsStillAcceptStatusAndContextChangesThroughPatch() {
            Long id = create(manualRequest("250.00")).getBody().getId();
            DocumentSaveDTO patch = new DocumentSaveDTO();
            patch.setDriverId(8L);
            patch.setStatus(DocumentStatus.FINAL);

            DocumentDTO updated = resource.updateDocumentDraft(id, patch).getBody();

            assertThat(updated.getDriverId()).isEqualTo(8L);
            assertThat(updated.getStatus()).isEqualTo(DocumentStatus.FINAL);
        }
    }

    /** Payment terms of the agreement: required on new confessions, stored normalized, never touching pendencies. */
    @Nested
    class PaymentTerms {

        private DocumentSaveDTO withTerms(Map<String, Object> terms) {
            DocumentSaveDTO request = originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"));
            if (terms == null) {
                request.getPayload().remove("condicoesPagamento");
            } else {
                request.getPayload().put("condicoesPagamento", terms);
            }
            return request;
        }

        private Map<String, Object> termsOf(Object... keyValues) {
            Map<String, Object> terms = new LinkedHashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                terms.put((String) keyValues[i], keyValues[i + 1]);
            }
            return terms;
        }

        @Test
        void pixWithDeadlineAndObservationIsStoredAsSent() {
            DocumentDTO created = create(withTerms(termsOf("formaPagamento", "PIX", "prazoPagamento", "2026-10-20", "observacao", "  Pagamento via PIX.  ")))
                .getBody();

            assertThat(terms(stored(created)))
                .containsExactly(entry("formaPagamento", "PIX"), entry("prazoPagamento", "2026-10-20"), entry("observacao", "Pagamento via PIX."));
        }

        @Test
        void onlyTheKeysThatApplyAreKeptAndAnEmptyObservationIsDropped() {
            DocumentDTO created = create(
                withTerms(
                    termsOf(
                        "formaPagamento",
                        "DINHEIRO",
                        "prazoPagamento",
                        "2026-10-20",
                        "parcelasQtd",
                        4,
                        "primeiroVencimento",
                        "2026-10-01",
                        "observacao",
                        "   ",
                        "testemunha1Nome",
                        "X"
                    )
                )
            )
                .getBody();

            assertThat(terms(stored(created))).containsExactly(entry("formaPagamento", "DINHEIRO"), entry("prazoPagamento", "2026-10-20"));
        }

        @Test
        void invalidOrMissingTermsAreRejectedAndNothingIsStored() {
            List<Map<String, Object>> invalid = new ArrayList<>();
            invalid.add(termsOf("prazoPagamento", "2026-10-20"));
            invalid.add(termsOf("formaPagamento", "A_VISTA", "prazoPagamento", "2026-10-20"));
            invalid.add(termsOf("formaPagamento", "PIX"));
            invalid.add(termsOf("formaPagamento", "PIX", "prazoPagamento", "20/10/2026"));
            invalid.add(termsOf("formaPagamento", "PIX", "prazoPagamento", "2026-02-30"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "primeiroVencimento", "2026-11-10"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", 1, "primeiroVencimento", "2026-11-10"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", 2.5, "primeiroVencimento", "2026-11-10"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", "3", "primeiroVencimento", "2026-11-10"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", 121, "primeiroVencimento", "2026-11-10"));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", 3));
            invalid.add(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2026-11-10", "parcelasQtd", 3, "primeiroVencimento", "2026-12-10"));
            invalid.add(termsOf("formaPagamento", "PIX", "prazoPagamento", "2026-10-20", "observacao", "x".repeat(1001)));
            invalid.add(termsOf("formaPagamento", "PIX", "prazoPagamento", "2026-10-20", "observacao", 12));

            assertRejected(() -> create(withTerms(null)), "confessiontermsinvalid");
            invalid.forEach(terms -> assertRejected(() -> create(withTerms(terms)), "confessiontermsinvalid"));
            assertThat(storedDocuments).isEmpty();
        }

        @Test
        void installmentsAreOnlyConditionsOfTheAgreementAndNeverTouchThePendencies() {
            Map<Long, String> before = pendencyStates();
            Long id = create(
                withTerms(termsOf("formaPagamento", "PARCELADO", "prazoPagamento", "2027-03-10", "parcelasQtd", 5, "primeiroVencimento", "2026-11-10"))
            )
                .getBody()
                .getId();
            resource.finalizeDocument(id);

            assertThat(terms(storedById(id)))
                .containsExactly(
                    entry("formaPagamento", "PARCELADO"),
                    entry("prazoPagamento", "2027-03-10"),
                    entry("parcelasQtd", 5),
                    entry("primeiroVencimento", "2026-11-10")
                );
            assertThat(pendencyStates()).isEqualTo(before);
            verify(pendencies, never()).save(any());
        }

        @Test
        void aDraftWithTermsKeepsThemButADraftSavedBeforeTheTermsCanStillBeEdited() throws Exception {
            Long withTermsId = create(originRequest(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00"))).getBody().getId();
            assertRejected(() -> resource.updateDocumentDraft(withTermsId, payloadOnly(withTerms(null))), "confessiontermsinvalid");

            // Draft of a confession from pendencies stored before the terms existed.
            DriverDocument legacy = storedDocuments.get(withTermsId);
            Map<String, Object> legacyPayload = storedById(withTermsId);
            legacyPayload.remove("condicoesPagamento");
            legacyPayload.put("formaPagamento", "A_VISTA");
            legacy.setPayloadJson(objectMapper.writeValueAsString(legacyPayload));

            DocumentSaveDTO edit = payloadOnly(withTerms(null));
            edit.getPayload().put("observacoes", "editado");
            Map<String, Object> edited = stored(resource.updateDocumentDraft(withTermsId, edit).getBody());
            assertThat(edited).containsEntry("observacoes", "editado").doesNotContainKey("condicoesPagamento");
            // Terms sent on that old draft are validated all the same.
            assertRejected(
                () -> resource.updateDocumentDraft(withTermsId, payloadOnly(withTerms(termsOf("formaPagamento", "PIX")))),
                "confessiontermsinvalid"
            );
        }
    }

    /** Etapa 2.1: editing João's contract can never hand his confessions over to another driver. */
    @Nested
    class DriverCarEdits {

        private DriverCarResource driverCarResource;

        @BeforeEach
        void driverCarResource() {
            DriverCarRepository repository = mock(DriverCarRepository.class);
            when(repository.findByCurrentUserAndId(anyLong()))
                .thenAnswer(call -> driverCars.stream().filter(dc -> dc.getId().equals(call.getArgument(0))).findFirst());
            when(repository.save(any(DriverCar.class))).thenAnswer(call -> call.getArgument(0));
            DriverRepository lookup = mock(DriverRepository.class);
            when(lookup.findAllByCurrentUserAndCpf(joao.getCpf())).thenReturn(List.of(joao));
            when(lookup.findAllByCurrentUserAndCpf(maria.getCpf())).thenReturn(List.of(maria));
            // Like a JPA merge: saving driver 5 updates the managed row the pendencies' debtor points to.
            when(lookup.save(any(Driver.class)))
                .thenAnswer(call -> {
                    Driver saved = call.getArgument(0);
                    if (joao.getId().equals(saved.getId())) {
                        joao.setName(saved.getName());
                        joao.setCpf(saved.getCpf());
                        return joao;
                    }
                    return saved;
                });
            driverCarResource = new DriverCarResource(repository, mock(CarRepository.class), mock(com.localuz.repository.AddressRepository.class), lookup, mock(com.localuz.service.DriverAssignmentService.class));
            ReflectionTestUtils.setField(driverCarResource, "applicationName", "localmaisApp");
        }

        @Test
        void finalConfessionOfJoaoStaysJoaoAfterARejectedDriverChange() {
            // H
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            resource.finalizeDocument(id);

            assertDriverChangeForbidden(() -> driverCarResource.updateDriverCarById(100L, editOf(contract100, maria.getId(), maria.getCpf())));
            assertDriverChangeForbidden(() -> driverCarResource.updateDriverCarById(100L, editOf(contract100, null, maria.getCpf())));

            assertThat(contract100.getDriver()).isSameAs(joao);
            assertThat(ownedPendencies.get(1000L).getDriverCar().getDriver()).isSameAs(joao);
            DriverDocument document = storedDocuments.get(id);
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.FINAL);
            assertThat(document.getDriver()).isSameAs(joao);
            assertThat(stored(resource.getDocumentById(id)).get("driverName")).isEqualTo("João Silva");
        }

        @Test
        void draftConfessionOfJoaoStaysCoherent() {
            // I: the rejected change leaves the draft finalizable as João's.
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();

            assertDriverChangeForbidden(() -> driverCarResource.updateDriverCarById(100L, editOf(contract100, maria.getId(), maria.getCpf())));
            DocumentDTO finalized = resource.finalizeDocument(id).getBody();

            assertThat(finalized.getStatus()).isEqualTo(DocumentStatus.FINAL);
            assertThat(finalized.getDriverId()).isEqualTo(5L);
            assertThat(stored(finalized).get("driverName")).isEqualTo("João Silva");
        }

        @Test
        void allowedCadastralUpdateOfJoaoStillAsksToRefreshHisDraft() {
            // I: renaming João (same person, same id) is allowed; the draft shows the old name, so finalize asks to refresh.
            Long id = createOrigin(5L, 10L, 100L, List.of(1000L), Map.of(1000L, "500.00")).getId();
            DriverCar rename = editOf(contract100, joao.getId(), joao.getCpf());
            rename.getDriver().setName("João Silva Santos");
            driverCarResource.updateDriverCarById(100L, rename);

            assertThat(contract100.getDriver().getId()).isEqualTo(5L);
            assertOutdated(() -> resource.finalizeDocument(id));
        }

        private DriverCar editOf(DriverCar contract, Long driverId, String cpf) {
            Driver driver = new Driver();
            driver.setId(driverId);
            driver.setName(driverId != null && driverId.equals(maria.getId()) ? maria.getName() : joao.getName());
            driver.setCpf(cpf);
            DriverCar request = new DriverCar();
            request.setId(contract.getId());
            request.setStartDate(contract.getStartDate());
            request.setConcluded(contract.getConcluded());
            request.setDriver(driver);
            return request;
        }

        private void assertDriverChangeForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
            assertThatThrownBy(call)
                .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("drivercarchangeforbidden"));
        }
    }

    // ---- helpers ----

    private ResponseEntity<DocumentDTO> create(DocumentSaveDTO request) {
        try {
            return resource.createDocument(request);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentDTO createOrigin(Long driverId, Long carId, Long driverCarId, List<Long> ids, Map<Long, String> values) {
        return create(originRequest(driverId, carId, driverCarId, ids, values)).getBody();
    }

    private DocumentSaveDTO originRequest(Long driverId, Long carId, Long driverCarId, List<Long> ids, Map<Long, String> values) {
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("tipo", "PENDENCIAS");
        origin.put("driverId", driverId);
        if (driverCarId != null) {
            origin.put("driverCarId", driverCarId);
        }
        origin.put("pendencyIds", new ArrayList<>(ids));
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, String> value : values.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourcePendencyId", value.getKey());
            item.put("valorItem", Double.valueOf(value.getValue()));
            items.add(item);
            total = total.add(new BigDecimal(value.getValue()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origem", origin);
        payload.put("itensDaDivida", items);
        payload.put("valorTotal", total.doubleValue());
        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("formaPagamento", "PARCELADO");
        terms.put("prazoPagamento", "2027-03-10");
        terms.put("parcelasQtd", 2);
        terms.put("primeiroVencimento", "2026-11-10");
        terms.put("observacao", "Acordo em duas vezes");
        payload.put("condicoesPagamento", terms);
        DocumentSaveDTO request = new DocumentSaveDTO();
        request.setType(DocumentType.CONFISSAO_DIVIDA);
        request.setDriverId(driverId);
        request.setCarId(carId);
        request.setStatus(DocumentStatus.DRAFT);
        request.setPayload(payload);
        return request;
    }

    private DocumentSaveDTO manualRequest(String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("typeId", 2);
        item.put("typeNameSnapshot", "Outros");
        item.put("descricaoItem", "Acordo");
        item.put("valorItem", Double.valueOf(value));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itensDaDivida", new ArrayList<>(List.of(item)));
        payload.put("valorTotal", Double.valueOf(value));
        payload.put("driverName", "Nome digitado");
        payload.put("origemDaDivida", "Acordo verbal");
        DocumentSaveDTO request = new DocumentSaveDTO();
        request.setType(DocumentType.CONFISSAO_DIVIDA);
        request.setDriverId(5L);
        request.setCarId(10L);
        request.setStatus(DocumentStatus.DRAFT);
        request.setPayload(payload);
        return request;
    }

    private static DocumentSaveDTO payloadOnly(DocumentSaveDTO request) {
        DocumentSaveDTO patch = new DocumentSaveDTO();
        patch.setPayload(request.getPayload());
        return patch;
    }

    private Map<String, Object> storedById(Long id) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(id);
        return stored(dto);
    }

    private Map<String, Object> stored(DocumentDTO dto) {
        try {
            return objectMapper.readValue(storedDocuments.get(dto.getId()).getPayloadJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> terms(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("condicoesPagamento");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> origin(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("origem");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("itensDaDivida");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private Map<Long, String> pendencyStates() {
        Map<Long, String> states = new HashMap<>();
        ownedPendencies.forEach((id, p) ->
            states.put(
                id,
                p.getCost() + "|" + p.getPaidAmount() + "|" + p.getRemainingAmount() + "|" + p.getStatus() + "|" + p.getPaidAt() + "|" +
                p.getPaymentMethod() + "|" + p.getDriverCar().getId() + "|" + p.getName() + "|" + p.getDate()
            )
        );
        return states;
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String errorKey) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo(errorKey));
    }

    private static void assertOutdated(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(
                DebtConfessionOutdatedException.class,
                failure -> assertThat(failure.getStatus().getStatusCode()).isEqualTo(409)
            );
    }

    private List<DriverCar> contractsOf(Long driverId, Long carId, boolean activeOnly) {
        return driverCars
            .stream()
            .filter(dc -> dc.getDriver().getId().equals(driverId) && dc.getCar().getId().equals(carId))
            .filter(dc -> !activeOnly || !Boolean.TRUE.equals(dc.getConcluded()))
            .sorted(Comparator.comparing(DriverCar::getStartDate).reversed())
            .collect(Collectors.toList());
    }

    private void own(Pendency pendency) {
        ownedPendencies.put(pendency.getId(), pendency);
    }

    private DriverCar contract(Long id, Driver driver, Car car, LocalDate start, boolean concluded) {
        DriverCar contract = new DriverCar();
        contract.setId(id);
        contract.setDriver(driver);
        contract.setCar(car);
        contract.setStartDate(start);
        contract.setConcluded(concluded);
        driverCars.add(contract);
        return contract;
    }

    private static Driver driver(Long id, String name, String cpf) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setName(name);
        driver.setCpf(cpf);
        return driver;
    }

    private static Car car(Long id, String plate) {
        Car car = new Car();
        car.setId(id);
        car.setPlate(plate);
        car.setModel("Onix");
        car.setDeleted(false);
        return car;
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
        pendency.setCost(new BigDecimal(cost));
        pendency.setPaidAmount(new BigDecimal(paid));
        pendency.setRemainingAmount(new BigDecimal(remaining));
        pendency.setStatus(status);
        pendency.setDate(date);
        return pendency;
    }
}
