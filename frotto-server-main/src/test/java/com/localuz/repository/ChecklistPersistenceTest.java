package com.localuz.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Inspection;
import com.localuz.domain.enumeration.ChecklistType;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.service.CarService;
import com.localuz.service.ChecklistService;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverAssignmentService;
import com.localuz.service.UserService;
import com.localuz.service.dto.DocumentDTO;
import com.localuz.service.dto.DocumentSaveDTO;
import com.localuz.service.dto.ReserveReturnResultDTO;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.web.rest.DocumentResource;
import com.localuz.web.rest.DriverCarResource;
import com.localuz.web.rest.InspectionResource;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.DriverAssignmentConflictException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.query.ExtensionAwareQueryMethodEvaluationContextProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Real MySQL + production Liquibase: Checklist de Entrega/Devolução -> DriverCar -> Inspection. Production services
 * and resources over real transactions (READ COMMITTED for /finalize, as its annotation sets), real concurrency.
 * Uses FROTTO_TEST_MYSQL_JDBC_URL in a throwaway schema, otherwise Testcontainers; skipped when neither is available.
 */
class ChecklistPersistenceTest {

    private static final String CHANGESET = "20261008000000";
    private static final String OWNER_A = "cl-owner-a";
    private static final String OWNER_B = "cl-owner-b";
    private static final long USER_A = 93001, USER_B = 93002;
    /** Car used only to make drivers belong to account A (an old concluded contract each). */
    private static final long HISTORY_CAR = 93999, CAR_OF_B = 93998;
    private static final String EXTERNAL_JDBC_URL = System.getenv("FROTTO_TEST_MYSQL_JDBC_URL");
    private static final boolean USE_EXTERNAL_DATABASE = EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();
    private static final AtomicLong IDS = new AtomicLong(94000);

    private static MySQLContainer<?> mysql;
    private static String adminUrl;
    private static String jdbcUrl;
    private static String schema;
    private static String user;
    private static String password;
    private static EntityManagerFactory factory;
    private static EntityManager shared;
    private static String legacyBefore;

    private static JpaRepositoryFactory repositories;
    private static JpaTransactionManager transactionManager;
    private static TransactionTemplate transaction;
    private static TransactionTemplate finalizeTransaction;
    /** The transaction of the old DriverCar routes (their production @Transactional). */
    private static TransactionTemplate routeTransaction;
    private static DocumentResource documents;
    private static DriverCarResource driverCars;
    private static InspectionResource inspections;
    private static DriverRepository drivers;

    @BeforeAll
    static void schemaAndServices() throws Exception {
        if (USE_EXTERNAL_DATABASE) {
            user = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_USER", "root");
            password = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_PASSWORD", "");
            schema = "frotto_checklist_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            adminUrl = EXTERNAL_JDBC_URL;
            try (Connection connection = DriverManager.getConnection(adminUrl, user, password); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE " + schema);
            }
            jdbcUrl = EXTERNAL_JDBC_URL.replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1" + schema);
        } else {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "No FROTTO_TEST_MYSQL_JDBC_URL and no Docker");
            mysql = new MySQLContainer<>("mysql:8.0.36").withStartupTimeout(Duration.ofMinutes(4));
            mysql.start();
            jdbcUrl = mysql.getJdbcUrl();
            user = mysql.getUsername();
            password = mysql.getPassword();
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            // Upgrade path: the schema before this etapa, historical checklist + manual inspection, then the changeset.
            Liquibase previous = liquibase(connection);
            previous.getDatabaseChangeLog().getChangeSets().removeIf(change -> change.getId().startsWith(CHANGESET));
            previous.update(new Contexts("test"));
            assertThat(columnExists(connection, "inspection", "origin_document_id")).isFalse();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO jhi_user (id,login,password_hash,activated,created_by,created_date) VALUES " +
                    "(93001,'" + OWNER_A + "',REPEAT('x',60),true,'test',NOW(6)),(93002,'" + OWNER_B + "',REPEAT('x',60),true,'test',NOW(6))"
                );
                statement.executeUpdate(
                    "INSERT INTO car (id,user_id,plate,model,active,odometer) VALUES (93999,93001,'HIS9H99','Histórico',true,0)," +
                    "(93998,93002,'BBB9B98','Gol',true,0)"
                );
                // Historical rows: a FINAL checklist of the old wizard and a manual inspection.
                statement.executeUpdate("INSERT INTO driver (id,name,cpf) VALUES (93900,'Motorista Antigo','90000000000')");
                statement.executeUpdate("INSERT INTO driver_car (id,car_id,driver_id,concluded,start_date,suspended) VALUES (93900,93999,93900,true,'2025-01-01',false)");
                statement.executeUpdate(
                    "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES " +
                    "(93900,'ENTREGA_DEVOLUCAO_CHECKLIST',93900,93999,93001,'FINAL','{\"tipo\":\"ENTREGA\",\"km\":\"12.000\",\"dataHora\":\"ontem\"}',NOW(6),NOW(6))"
                );
                statement.executeUpdate("INSERT INTO inspection (id,date,driver_name,odometer,car_id) VALUES (93900,'2025-02-01','Manual',12000,93999)");
            }
            commit(connection);
            legacyBefore = legacyRows(connection);
            liquibase(connection).update(new Contexts("test"));
            commit(connection);
        }

        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(new DriverManagerDataSource(jdbcUrl, user, password));
        bean.setPackagesToScan("com.localuz.domain");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.jdbc.time_zone", "UTC");
        properties.setProperty("hibernate.physical_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy");
        properties.setProperty("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        bean.setJpaProperties(properties);
        bean.afterPropertiesSet();
        factory = bean.getObject();

        transactionManager = new JpaTransactionManager(factory);
        transactionManager.setJpaDialect(new HibernateJpaDialect());
        transaction = new TransactionTemplate(transactionManager);
        finalizeTransaction = new TransactionTemplate(transactionManager);
        finalizeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        routeTransaction = new TransactionTemplate(transactionManager);
        routeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        shared = SharedEntityManagerCreator.createSharedEntityManager(factory);
        repositories = new JpaRepositoryFactory(shared);
        // As in the application: repository calls translate persistence errors (e.g. a UNIQUE violation -> DataIntegrityViolationException).
        org.springframework.beans.factory.support.DefaultListableBeanFactory translators = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        translators.registerSingleton("jpaDialect", new HibernateJpaDialect());
        repositories.addRepositoryProxyPostProcessor(
            new org.springframework.data.repository.core.support.PersistenceExceptionTranslationRepositoryProxyPostProcessor(translators)
        );
        repositories.setEvaluationContextProvider(
            new ExtensionAwareQueryMethodEvaluationContextProvider(List.of(new SecurityEvaluationContextExtension()))
        );
        drivers = repositories.getRepository(DriverRepository.class);
        wire(null, null);
    }

    /** Production wiring; a failing inspection repository / assignment service can be injected for the rollback tests. */
    private static void wire(InspectionRepository inspectionOverride, DriverAssignmentService assignmentOverride) {
        InspectionRepository inspectionRepository = inspectionOverride != null ? inspectionOverride : realInspections();
        DriverCarRepository driverCarRepository = repositories.getRepository(DriverCarRepository.class);
        CarRepository carRepository = repositories.getRepository(CarRepository.class);
        PendencyRepository pendencyRepository = repositories.getRepository(PendencyRepository.class);
        DriverDocumentRepository documentRepository = repositories.getRepository(DriverDocumentRepository.class);
        DriverAssignmentService assignments = assignmentOverride != null
            ? assignmentOverride
            : new DriverAssignmentService(driverCarRepository, drivers, carRepository, pendencyRepository, shared);
        CarService carService = new CarService(inspectionRepository, repositories.getRepository(MaintenanceRepository.class), carRepository);
        ChecklistService checklistService = new ChecklistService(driverCarRepository, documentRepository, inspectionRepository, drivers, assignments, carService);
        UserService userService = mock(UserService.class);
        when(userService.getUserWithAuthorities())
            .thenAnswer(call -> {
                String login = SecurityContextHolder.getContext().getAuthentication().getName();
                return Optional.of(shared.getReference(com.localuz.domain.User.class, OWNER_A.equals(login) ? USER_A : USER_B));
            });
        documents =
            new DocumentResource(
                documentRepository,
                drivers,
                carRepository,
                driverCarRepository,
                pendencyRepository,
                mock(FileStorageGateway.class),
                userService,
                new ObjectMapper(),
                new DebtConfessionService(pendencyRepository, repositories.getRepository(DebtItemTypeRepository.class)),
                checklistService
            );
        ReflectionTestUtils.setField(documents, "applicationName", "localmaisApp");
        driverCars =
            new DriverCarResource(driverCarRepository, carRepository, repositories.getRepository(AddressRepository.class), drivers, assignments);
        ReflectionTestUtils.setField(driverCars, "applicationName", "localmaisApp");
        inspections =
            new InspectionResource(
                inspectionRepository,
                carRepository,
                repositories.getRepository(TireRepository.class),
                repositories.getRepository(ExpenseRepository.class),
                carService,
                repositories.getRepository(CarBodyDamageRepository.class)
            );
        ReflectionTestUtils.setField(inspections, "applicationName", "localmaisApp");
    }

    private static InspectionRepository realInspections() {
        InspectionRepositoryWithBagRelationshipsImpl bags = new InspectionRepositoryWithBagRelationshipsImpl();
        ReflectionTestUtils.setField(bags, "entityManager", shared);
        return repositories.getRepository(InspectionRepository.class, org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments.just(bags));
    }

    @AfterEach
    void restoreWiring() {
        SecurityContextHolder.clearContext();
        wire(null, null);
    }

    @AfterAll
    static void shutdown() throws Exception {
        if (factory != null) {
            factory.close();
        }
        if (mysql != null) {
            mysql.stop();
        }
        if (schema != null) {
            try (Connection connection = DriverManager.getConnection(adminUrl, user, password); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    // ------------------------------------------------------------------ migration and history

    @Test
    void aaAbHistoricalRowsComeOutOfTheMigrationUntouchedAndNothingIsBackfilled() throws Exception {
        try (Connection connection = connection()) {
            assertThat(legacyRows(connection)).isEqualTo(legacyBefore);
            assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = 93900 AND driver_car_id IS NULL AND checklist_type IS NULL AND final_checklist_slot IS NULL")).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM inspection WHERE id = 93900 AND (origin_document_id IS NOT NULL OR driver_car_id IS NOT NULL OR fuel_level IS NOT NULL)")).isZero();
            assertThat(uniqueIndexes(connection, "driver_document")).contains("ux_driver_document_final_checklist");
            assertThat(uniqueIndexes(connection, "inspection")).contains("ux_inspection_origin_document");
        }
        as(OWNER_A);
        DocumentDTO legacy = transaction.execute(status -> documents.getDocumentById(93900L));
        assertThat(legacy.getChecklistType()).isNull();
        assertThat(legacy.getInspectionId()).isNull();
        assertThat(legacy.getPayload()).containsEntry("km", "12.000");
    }

    @Test
    void aaALegacyChecklistDraftStillFinalizesWithoutAnyAutomation() throws Exception {
        long driver = newDriver("Legado Rascunho", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long id = IDS.incrementAndGet();
        exec(
            "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES (" +
            id +
            ",'ENTREGA_DEVOLUCAO_CHECKLIST'," +
            driver +
            "," +
            car +
            ",93001,'DRAFT','{\"tipo\":\"ENTREGA\",\"km\":\"10\"}',NOW(6),NOW(6))"
        );
        as(OWNER_A);
        finalizeAs(id, null);
        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + id + " AND status = 'FINAL' AND driver_car_id IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + id)).isZero();
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + contract).get("concluded")).isEqualTo(false);
    }

    // ------------------------------------------------------------------ Entrega

    @Test
    void aEntregaOfADriverWithoutOpenContractCreatesThePrimaryTheInspectionAndTheFinalDocument() throws Exception {
        long driver = newDriver("João Sem Carro", null);
        long car = newCar(5000);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 15000, "HALF"));

        DocumentDTO result = finalizeAs(draft, null);

        Map<String, Object> contract = row("SELECT * FROM driver_car WHERE id = " + result.getDriverCarId());
        assertThat(contract).containsEntry("car_id", car).containsEntry("driver_id", driver).containsEntry("concluded", false).containsEntry("suspended", false);
        assertThat(contract.get("primary_driver_car_id")).isNull();
        assertThat(contract.get("start_date").toString()).isEqualTo("2026-10-07");
        Map<String, Object> inspection = row("SELECT * FROM inspection WHERE id = " + result.getInspectionId());
        assertThat(inspection)
            .containsEntry("origin_document_id", draft)
            .containsEntry("driver_car_id", result.getDriverCarId())
            .containsEntry("car_id", car)
            .containsEntry("fuel_level", "HALF")
            .containsEntry("driver_name", "João Sem Carro");
        assertThat(((Number) inspection.get("odometer")).doubleValue()).isEqualTo(15000.0);
        // Q/R) nothing financial, nothing guessed.
        assertThat(inspection.get("cost")).isNull();
        assertThat(inspection.get("internal_cleaning")).isNull();
        assertThat(inspection.get("external_cleaning")).isNull();
        assertThat(inspection.get("score")).isNull();
        assertThat(count("SELECT COUNT(*) FROM expense WHERE inspection_id = " + result.getInspectionId())).isZero();
        // Positional tires copied only when compatible (integrity of the inspection form).
        Map<String, Object> leftFront = row("SELECT t.* FROM tire t JOIN inspection i ON i.left_front_id = t.id WHERE i.id = " + result.getInspectionId());
        assertThat(leftFront).containsEntry("model", "Pirelli").containsEntry("integrity", "70-90%");
        Map<String, Object> spare = row("SELECT t.* FROM tire t JOIN inspection i ON i.spare_id = t.id WHERE i.id = " + result.getInspectionId());
        assertThat(spare).containsEntry("model", "Goodyear");
        assertThat(spare.get("integrity")).isNull(); // "BOM" is not an integrity of the form: not converted
        Map<String, Object> document = row("SELECT * FROM driver_document WHERE id = " + draft);
        assertThat(document).containsEntry("status", "FINAL").containsEntry("driver_car_id", result.getDriverCarId()).containsEntry("final_checklist_slot", "ENTREGA");
        // S) the most recent inspection moves the car's odometer.
        assertThat(((Number) row("SELECT odometer FROM car WHERE id = " + car).get("odometer")).doubleValue()).isEqualTo(15000.0);
        // U) empty emergency contacts filled.
        Map<String, Object> driverRow = row("SELECT * FROM driver WHERE id = " + driver);
        assertThat(driverRow).containsEntry("emergency_contact", "11999990000").containsEntry("emergency_contact_second", "11988880000");
    }

    @Test
    void bEntregaWithTheRightContractAlreadyOpenReusesIt() throws Exception {
        long driver = newDriver("Bruno Reuso", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 100, "FULL"));

        DocumentDTO result = finalizeAs(draft, null);

        assertThat(result.getDriverCarId()).isEqualTo(contract);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE driver_id = " + driver + " AND car_id = " + car)).isEqualTo(1);
    }

    @Test
    void cEntregaWithAnotherPrimaryNeedsTheChoiceThenPermanentFollowsTheRule() throws Exception {
        long driver = newDriver("Carla Troca", null);
        long oldCar = newCar(0);
        long newCar = newCar(0);
        long primary = newContract(oldCar, driver, false, false, null);
        long draft = draft(ChecklistType.ENTREGA, driver, newCar, null, payload("2026-10-07", 200, "QUARTER"));

        // Without a choice: the same 409 of the contract screen, and nothing changed (rollback).
        assertThatThrownBy(() -> finalizeAs(draft, null)).isInstanceOf(DriverAssignmentConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'DRAFT'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + newCar)).isZero();
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isZero();

        DocumentDTO result = finalizeAs(draft, DriverAssignmentType.PERMANENT);
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + primary).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + result.getDriverCarId())).containsEntry("car_id", newCar).containsEntry("concluded", false);
        assertThat(row("SELECT primary_driver_car_id FROM driver_car WHERE id = " + result.getDriverCarId()).get("primary_driver_car_id")).isNull();
    }

    @Test
    void dEntregaAsReserveSuspendsThePrimaryAndCreatesTheReservePointingToIt() throws Exception {
        long driver = newDriver("Davi Reserva", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, false, null);
        long draft = draft(ChecklistType.ENTREGA, driver, reserveCar, null, payload("2026-10-07", 300, "THREE_QUARTERS"));

        DocumentDTO result = finalizeAs(draft, DriverAssignmentType.RESERVE);

        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", true).containsEntry("concluded", false);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + result.getDriverCarId()))
            .containsEntry("car_id", reserveCar)
            .containsEntry("primary_driver_car_id", primary);
    }

    @Test
    void eOAEntregaFinalizedAgainChangesNothingAndCreatesNoSecondInspection() throws Exception {
        long driver = newDriver("Eva Retry", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 400, "FULL"));
        DocumentDTO first = finalizeAs(draft, null);
        exec("UPDATE car SET odometer = 999 WHERE id = " + car); // a later change of the car must not be undone by a retry

        DocumentDTO again = finalizeAs(draft, null);

        assertThat(again.getInspectionId()).isEqualTo(first.getInspectionId());
        assertThat(again.getDriverCarId()).isEqualTo(first.getDriverCarId());
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE driver_id = " + driver + " AND car_id = " + car)).isEqualTo(1);
        assertThat(((Number) row("SELECT odometer FROM car WHERE id = " + car).get("odometer")).doubleValue()).isEqualTo(999.0);
    }

    @Test
    void fTwoEntregaDocumentsOfTheSameContractOnlyOneBecomesFinal() throws Exception {
        long driver = newDriver("Fabio Dois", null);
        long car = newCar(0);
        long first = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        long second = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        finalizeAs(first, null);

        assertRejected(() -> finalizeAs(second, null), "checklistalreadyfinalized");
        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + second + " AND status = 'DRAFT'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + second)).isZero();
    }

    // ------------------------------------------------------------------ Devolução

    @Test
    void gwDevolucaoOfThePrimaryConcludesExactlyThatContractAndLeavesTheDriverDataAsItWas() throws Exception {
        long driver = newDriver("Gabi Devolve", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long otherDriver = newDriver("Outro", null);
        long otherContract = newContract(newCar(0), otherDriver, false, false, null);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, car, contract, payload("2026-11-01", 20000, "EMPTY"));

        DocumentDTO result = finalizeAs(draft, null);

        assertThat(row("SELECT * FROM driver_car WHERE id = " + contract)).containsEntry("concluded", true).containsEntry("suspended", false);
        assertThat(row("SELECT end_date FROM driver_car WHERE id = " + contract).get("end_date").toString()).isEqualTo("2026-11-01");
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + otherContract).get("concluded")).isEqualTo(false);
        assertThat(row("SELECT * FROM inspection WHERE id = " + result.getInspectionId())).containsEntry("driver_car_id", contract).containsEntry("fuel_level", "EMPTY");
        // W) a Devolução never touches the driver's registration data.
        Map<String, Object> driverRow = row("SELECT * FROM driver WHERE id = " + driver);
        assertThat(driverRow.get("emergency_contact")).isNull();
        assertThat(driverRow.get("emergency_contact_second")).isNull();
    }

    @Test
    void hDevolucaoOfAReserveConcludesItAndRestoresThePrimaryWhenItsCarIsFree() throws Exception {
        long driver = newDriver("Hugo Volta", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 500, "HALF"));

        DocumentDTO result = finalizeAs(draft, null);

        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", false).containsEntry("concluded", false);
        assertThat(result.getReserveReturn().getOutcome()).isEqualTo(ReserveReturnResultDTO.Outcome.RESTORED);
    }

    @Test
    void iDevolucaoOfAReserveWithThePrimaryCarOccupiedKeepsThePrimarySuspended() throws Exception {
        long driver = newDriver("Iris Ocupado", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        newContract(primaryCar, newDriver("Quem Usa", null), false, false, null);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 500, "HALF"));

        DocumentDTO result = finalizeAs(draft, null);

        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT suspended FROM driver_car WHERE id = " + primary).get("suspended")).isEqualTo(true);
        assertThat(result.getReserveReturn().getOutcome()).isEqualTo(ReserveReturnResultDTO.Outcome.PRIMARY_CAR_OCCUPIED);
    }

    @Test
    void jDevolucaoOfAConcludedContractIsRefusedAtTheDraftAndAtTheFinalization() throws Exception {
        long driver = newDriver("Jonas Encerrado", null);
        long car = newCar(0);
        long concluded = newContract(car, driver, true, false, null);
        assertRejected(() -> draft(ChecklistType.DEVOLUCAO, driver, car, concluded, payload("2026-10-07", 1, "FULL")), "checklistdrivercarconcluded");

        long open = newContract(newCar(0), driver, false, false, null);
        long openCar = (long) row("SELECT car_id FROM driver_car WHERE id = " + open).get("car_id");
        long draft = draft(ChecklistType.DEVOLUCAO, driver, openCar, open, payload("2026-10-07", 1, "FULL"));
        exec("UPDATE driver_car SET concluded = true WHERE id = " + open); // concluded elsewhere after the draft
        assertRejected(() -> finalizeAs(draft, null), "checklistdrivercarconcluded");
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isZero();
    }

    @Test
    void aSuspendedPrimaryIsNeitherDeliveredNorReturnedByAChecklist() throws Exception {
        long driver = newDriver("Sueli Suspensa", null);
        long primaryCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        newContract(newCar(0), driver, false, false, primary);
        assertRejected(() -> draft(ChecklistType.DEVOLUCAO, driver, primaryCar, primary, payload("2026-10-07", 1, "FULL")), "checklistdrivercarsuspended");

        long entrega = draft(ChecklistType.ENTREGA, driver, primaryCar, null, payload("2026-10-07", 1, "FULL"));
        assertRejected(() -> finalizeAs(entrega, null), "checklistdrivercarsuspended");
        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", true).containsEntry("concluded", false);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + entrega)).isZero();
    }

    @Test
    void klmAContractOfAnotherDriverAnotherCarOrAnotherAccountIsRefused() throws Exception {
        long driver = newDriver("Kleber", null);
        long other = newDriver("Lia", null);
        long car = newCar(0);
        long otherCar = newCar(0);
        long contractOfOther = newContract(car, other, false, false, null);
        long contractOtherCar = newContract(otherCar, driver, false, false, null);
        assertRejected(() -> draft(ChecklistType.DEVOLUCAO, driver, car, contractOfOther, payload("2026-10-07", 1, "FULL")), "checklistdrivercardriver");
        assertRejected(() -> draft(ChecklistType.DEVOLUCAO, driver, car, contractOtherCar, payload("2026-10-07", 1, "FULL")), "checklistdrivercarcar");

        long draft = draft(ChecklistType.DEVOLUCAO, driver, otherCar, contractOtherCar, payload("2026-10-07", 1, "FULL"));
        as(OWNER_B);
        assertThatThrownBy(() -> finalizeTransaction.execute(status -> documents.finalizeDocument(draft, null)))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatus().value()).isEqualTo(404));
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + contractOtherCar).get("concluded")).isEqualTo(false);
    }

    // ------------------------------------------------------------------ rules of the document

    @Test
    void nzPatchToFinalIsClosedAndAFinalizedChecklistAndItsInspectionCannotBeDeleted() throws Exception {
        long driver = newDriver("Nina", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 1, "FULL"));
        as(OWNER_A);
        DocumentSaveDTO patch = new DocumentSaveDTO();
        patch.setStatus(com.localuz.domain.enumeration.DocumentStatus.FINAL);
        assertRejected(() -> transaction.execute(status -> documents.updateDocumentDraft(draft, patch)), "checklistfinalizerequired");
        assertRejected(() -> transaction.execute(status -> documents.markDocumentAsSent(draft)), "checklistfinalizerequired");

        DocumentDTO result = finalizeAs(draft, null);
        assertRejected(() -> transaction.execute(status -> documents.deleteDocument(draft)), "checklistfinalcannotbedeleted");
        assertRejected(() -> transaction.execute(status -> inspections.deleteInspection(result.getInspectionId())), "inspectionfromchecklist");
        // The contract it delivered keeps its trace too (RESTRICT).
        assertRejected(() -> transaction.execute(status -> driverCars.deleteDriverCarById(result.getDriverCarId())), "drivercarhaschecklists");
        // The generated inspection is the record of the operation: no manual edit, no expense (backend, not only UI).
        String before = rowsOf("SELECT * FROM inspection WHERE id = " + result.getInspectionId());
        Inspection edit = transaction.execute(status -> inspectionCopy(result.getInspectionId()));
        edit.setComment("revisado");
        edit.setOdometer(1f);
        assertRejected(() -> transaction.execute(status -> unchecked(() -> inspections.updateInspection(edit.getId(), edit))), "inspectionfromchecklistreadonly");
        com.localuz.domain.Expense expense = new com.localuz.domain.Expense();
        expense.setName("Lavagem");
        expense.setCost(new java.math.BigDecimal("50"));
        assertRejected(() -> transaction.execute(status -> unchecked(() -> expenses().createExpense(expense, result.getInspectionId()))), "inspectionfromchecklistreadonly");
        assertThat(rowsOf("SELECT * FROM inspection WHERE id = " + result.getInspectionId())).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM expense WHERE inspection_id = " + result.getInspectionId())).isZero();
    }

    @Test
    void manualInspectionsKeepTheirEditsAndExpenses() throws Exception {
        long car = newCar(0);
        long id = IDS.incrementAndGet();
        exec("INSERT INTO inspection (id,date,driver_name,odometer,car_id) VALUES (" + id + ",'2026-09-01','Manual',100," + car + ")");
        as(OWNER_A);
        Inspection edit = transaction.execute(status -> inspectionCopy(id));
        edit.setComment("revisado");
        transaction.execute(status -> unchecked(() -> inspections.updateInspection(id, edit)));
        com.localuz.domain.Expense expense = new com.localuz.domain.Expense();
        expense.setName("Lavagem");
        expense.setCost(new java.math.BigDecimal("50"));
        transaction.execute(status -> unchecked(() -> expenses().createExpense(expense, id)));

        assertThat(row("SELECT comment FROM inspection WHERE id = " + id)).containsEntry("comment", "revisado");
        assertThat(count("SELECT COUNT(*) FROM expense WHERE inspection_id = " + id)).isEqualTo(1);
    }

    @Test
    void anEntregaDraftCreatesNoContractAndAnAbandonedDraftLeavesNothing() throws Exception {
        long driver = newDriver("Rascunho Abandonado", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isZero();
        assertThat(row("SELECT driver_car_id FROM driver_document WHERE id = " + draft).get("driver_car_id")).isNull();

        as(OWNER_A);
        transaction.execute(status -> documents.deleteDocument(draft));

        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft)).isZero();
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isZero();
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE car_id = " + car)).isZero();
    }

    @Test
    void finalizingAgainNeverMovesASentDocumentBack() throws Exception {
        long driver = newDriver("Enviado", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        finalizeAs(draft, null);
        as(OWNER_A);
        transaction.execute(status -> documents.markDocumentAsSent(draft));
        String before = sideEffects(driver, car) + rowsOf("SELECT status, updated_at FROM driver_document WHERE id = " + draft);

        DocumentDTO again = finalizeAs(draft, null);

        assertThat(again.getStatus()).isEqualTo(com.localuz.domain.enumeration.DocumentStatus.SENT);
        assertThat(sideEffects(driver, car) + rowsOf("SELECT status, updated_at FROM driver_document WHERE id = " + draft)).isEqualTo(before);
        // A legacy document (no checklist) sent: also untouched by a later /finalize.
        long legacy = IDS.incrementAndGet();
        exec(
            "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES (" +
            legacy + ",'RECIBO_ALUGUEL'," + driver + "," + car + ",93001,'SENT','{}',NOW(6),NOW(6))"
        );
        as(OWNER_A);
        assertThat(finalizeTransaction.execute(status -> documents.finalizeDocument(legacy, null)).getBody().getStatus())
            .isEqualTo(com.localuz.domain.enumeration.DocumentStatus.SENT);
        assertThat(row("SELECT status FROM driver_document WHERE id = " + legacy)).containsEntry("status", "SENT");
    }

    @Test
    void theDriverCarRoutesRunInReadCommittedLikeTheFinalization() throws Exception {
        // The race tests run these routes in READ COMMITTED: that is what their production annotation declares.
        for (String name : List.of("createDriverCarByCar", "updateDriverCarById", "returnReserveCar", "restorePrimaryContract")) {
            java.lang.reflect.Method method = java.util.Arrays.stream(DriverCarResource.class.getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
            org.springframework.transaction.annotation.Transactional annotation = method.getAnnotation(
                org.springframework.transaction.annotation.Transactional.class
            );
            assertThat(annotation).as(name).isNotNull();
            assertThat(annotation.isolation()).as(name).isEqualTo(org.springframework.transaction.annotation.Isolation.READ_COMMITTED);
        }
    }

    @Test
    void newChecklistsRequireTypeContractDateNumericKmAndFuel() throws Exception {
        long driver = newDriver("Otto", null);
        long car = newCar(0);
        assertRejected(() -> draft(null, driver, car, null, payload("2026-10-07", 1, "FULL")), "checklisttyperequired");
        assertRejected(() -> draft(ChecklistType.DEVOLUCAO, driver, car, null, payload("2026-10-07", 1, "FULL")), "checklistdrivercarrequired");
        for (Map<String, Object> bad : List.of(payload("07/10/2026", 1, "FULL"), payload("2026-10-07", "12.000 km", "FULL"), payload("2026-10-07", -1, "FULL"), payload("2026-10-07", 1, "meio"))) {
            long draft = draft(ChecklistType.ENTREGA, driver, car, null, bad);
            assertThatThrownBy(() -> finalizeAs(draft, null)).isInstanceOf(BadRequestAlertException.class);
            assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'DRAFT'")).isEqualTo(1);
        }
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isZero();
    }

    @Test
    void pThePdfMarkAndReadingTheDocumentHaveNoSideEffects() throws Exception {
        long driver = newDriver("Paula", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 77, "FULL"));
        finalizeAs(draft, null);
        String before = sideEffects(driver, car);
        as(OWNER_A);
        for (int i = 0; i < 3; i++) {
            transaction.execute(status -> documents.markDocumentPdfGenerated(draft, null));
            transaction.execute(status -> documents.getDocumentById(draft));
        }
        assertThat(sideEffects(driver, car)).isEqualTo(before);
    }

    @Test
    void tAHistoricalChecklistDoesNotMoveTheOdometerBackwards() throws Exception {
        long driver = newDriver("Tiago", null);
        long car = newCar(80000);
        exec("INSERT INTO inspection (id,date,driver_name,odometer,car_id) VALUES (" + IDS.incrementAndGet() + ",'2027-01-10','Manual',80000," + car + ")");
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 15000, "FULL"));

        finalizeAs(draft, null);

        assertThat(((Number) row("SELECT odometer FROM car WHERE id = " + car).get("odometer")).doubleValue()).isEqualTo(80000.0);
    }

    @Test
    void vExistingEmergencyContactsAreNeverOverwritten() throws Exception {
        long driver = newDriver("Vera", "Mãe - 11977770000");
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 1, "FULL"));
        finalizeAs(draft, null);
        Map<String, Object> driverRow = row("SELECT * FROM driver WHERE id = " + driver);
        assertThat(driverRow).containsEntry("emergency_contact", "Mãe - 11977770000").containsEntry("emergency_contact_second", "11999990000");
    }

    // ------------------------------------------------------------------ rollback

    @Test
    void xAFailureCreatingTheInspectionRollsEverythingBack() throws Exception {
        long driver = newDriver("Xavier", null);
        long car = newCar(100);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 5000, "FULL"));
        InspectionRepository real = realInspections();
        InspectionRepository failing = (InspectionRepository) Proxy.newProxyInstance(
            InspectionRepository.class.getClassLoader(),
            new Class<?>[] { InspectionRepository.class },
            (proxy, method, args) -> {
                if (method.getName().equals("save")) {
                    throw new IllegalStateException("simulated failure while saving the inspection");
                }
                try {
                    return method.invoke(real, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
        wire(failing, null);
        String before = sideEffects(driver, car);

        assertThatThrownBy(() -> finalizeAs(draft, null)).hasMessageContaining("simulated failure");

        assertThat(sideEffects(driver, car)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'DRAFT' AND final_checklist_slot IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isZero();
    }

    @Test
    void yAFailureMovingTheContractRollsEverythingBack() throws Exception {
        long driver = newDriver("Yuri", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 500, "HALF"));
        DriverAssignmentService failing = new DriverAssignmentService(
            repositories.getRepository(DriverCarRepository.class),
            drivers,
            repositories.getRepository(CarRepository.class),
            repositories.getRepository(PendencyRepository.class),
            shared
        ) {
            @Override
            public ReserveReturnResultDTO returnReserve(DriverCar contract, LocalDate endDate) {
                super.returnReserve(contract, endDate);
                throw new IllegalStateException("simulated failure after returning the reserve");
            }
        };
        wire(null, failing);

        assertThatThrownBy(() -> finalizeAs(draft, null)).hasMessageContaining("simulated failure");

        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(false);
        assertThat(row("SELECT suspended FROM driver_car WHERE id = " + primary).get("suspended")).isEqualTo(true);
        assertThat(count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'DRAFT'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isZero();
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void acTwoConcurrentFinalizationsOfTheSameDocumentCreateOneInspection() throws Exception {
        long driver = newDriver("Ana Concorre", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));

        List<Object> results = concurrently(4, attempt -> finalizeAs(draft, null));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(DocumentDTO.class));
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isEqualTo(1);
    }

    @Test
    void adTwoConcurrentEntregasOfTheSameDriverAndCarKeepOneContractAndOneFinal() throws Exception {
        long driver = newDriver("Bia Duas", null);
        long car = newCar(0);
        long first = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        long second = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));

        List<Object> results = concurrently(2, attempt -> finalizeAs(attempt == 0 ? first : second, null));

        assertThat(results.stream().filter(result -> result instanceof DocumentDTO)).as(results.toString()).hasSize(1);
        assertThat(results.stream().filter(result -> result instanceof BadRequestAlertException))
            .as(results.toString())
            .allSatisfy(result -> assertThat(((BadRequestAlertException) result).getErrorKey()).isEqualTo("checklistalreadyfinalized"))
            .hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id IN (" + first + "," + second + ")")).isEqualTo(1);
    }

    @Test
    void aeTwoConcurrentDevolucoesOfTheSameContractKeepOneFinal() throws Exception {
        long driver = newDriver("Caio Duas", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long first = draft(ChecklistType.DEVOLUCAO, driver, car, contract, payload("2026-10-30", 10, "FULL"));
        long second = draft(ChecklistType.DEVOLUCAO, driver, car, contract, payload("2026-10-30", 10, "FULL"));

        List<Object> results = concurrently(2, attempt -> finalizeAs(attempt == 0 ? first : second, null));

        assertThat(results.stream().filter(result -> result instanceof DocumentDTO)).as(results.toString()).hasSize(1);
        assertThat(results.stream().filter(result -> result instanceof BadRequestAlertException))
            .as(results.toString()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id IN (" + first + "," + second + ")")).isEqualTo(1);
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + contract).get("concluded")).isEqualTo(true);
    }

    @Test
    void devolucaoConcurrentWithReturnReserveDecidesOnce() throws Exception {
        long driver = newDriver("Duda Corrida", null);
        long primary = newContract(newCar(0), driver, false, true, null);
        long reserveCar = newCar(0);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 10, "FULL"));

        List<Object> results = concurrently(
            2,
            attempt ->
                attempt == 0
                    ? finalizeAs(draft, null)
                    : routeTransaction.execute(status -> driverCars.returnReserveCar(reserve, LocalDate.of(2026, 10, 20)))
        );

        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", false).containsEntry("concluded", false);
        long finals = count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'FINAL'");
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(finals);
        assertThat(results).anySatisfy(result -> assertThat(result).isNotInstanceOf(Throwable.class));
    }

    @Test
    void devolucaoConcurrentWithThePutThatConcludesTheContract() throws Exception {
        long driver = newDriver("Edu Corrida", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, car, contract, payload("2026-10-20", 10, "FULL"));

        concurrently(
            2,
            attempt ->
                attempt == 0
                    ? finalizeAs(draft, null)
                    : routeTransaction.execute(status -> {
                        DriverCar body = new DriverCar();
                        body.setId(contract);
                        body.setDriver(drivers.findById(driver).orElseThrow());
                        body.setStartDate(LocalDate.of(2026, 1, 1));
                        body.setConcluded(true);
                        return driverCars.updateDriverCarById(contract, body).getBody();
                    })
        );

        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + contract).get("concluded")).isEqualTo(true);
        long finals = count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'FINAL'");
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(finals);
    }

    @Test
    void entregaConcurrentWithANormalAssignmentOfTheSameCarLeavesOneOperationalContract() throws Exception {
        long checklistDriver = newDriver("Fred Checklist", null);
        long screenDriver = newDriver("Gil Tela", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, checklistDriver, car, null, payload("2026-10-07", 10, "FULL"));

        concurrently(
            2,
            attempt ->
                attempt == 0
                    ? finalizeAs(draft, null)
                    : routeTransaction.execute(status -> {
                        DriverCar body = new DriverCar();
                        body.setDriver(drivers.findById(screenDriver).orElseThrow());
                        body.setStartDate(LocalDate.of(2026, 10, 7));
                        body.setConcluded(false);
                        return unchecked(() -> driverCars.createDriverCarByCar(car, null, body)).getBody();
                    })
        );

        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car + " AND concluded = false AND suspended = false")).isEqualTo(1);
        long finals = count("SELECT COUNT(*) FROM driver_document WHERE id = " + draft + " AND status = 'FINAL'");
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(finals);
    }

    @Test
    void permanentAndReserveDecidedConcurrentlyLeaveTheDriverWithOneOperationalContract() throws Exception {
        long driver = newDriver("Helo Escolhe", null);
        newContract(newCar(0), driver, false, false, null);
        long permanentCar = newCar(0);
        long reserveCar = newCar(0);
        long permanent = draft(ChecklistType.ENTREGA, driver, permanentCar, null, payload("2026-10-07", 10, "FULL"));
        long reserve = draft(ChecklistType.ENTREGA, driver, reserveCar, null, payload("2026-10-07", 10, "FULL"));

        concurrently(
            2,
            attempt -> attempt == 0 ? finalizeAs(permanent, DriverAssignmentType.PERMANENT) : finalizeAs(reserve, DriverAssignmentType.RESERVE)
        );

        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE driver_id = " + driver + " AND concluded = false AND suspended = false")).isEqualTo(1);
        assertThat(
            count(
                "SELECT COUNT(*) FROM driver_car r JOIN driver_car p ON p.id = r.primary_driver_car_id " +
                "WHERE r.driver_id = " + driver + " AND r.concluded = false AND p.concluded = true"
            )
        )
            .as("an open reserve never points to a concluded primary")
            .isZero();
    }

    // ------------------------------------------------------------------ old DriverCar routes racing a checklist
    // Deterministic races: the old route is paused right after its first read (before any lock), the checklist
    // finalizes and commits meanwhile, then the route goes on. The route must decide on the committed state.

    @Test
    void aPutEditReadBeforeADevolucaoNeverReopensTheReturnedContract() throws Exception {
        long driver = newDriver("Race Put", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, car, contract, payload("2026-10-20", 10, "FULL"));
        RacingRoutes routes = pausedAfterFirstRead("findByCurrentUserAndId", false);

        Object put = race(routes, () -> putEdit(routes.driverCars, contract, driver, 500), () -> finalizeAs(draft, null));

        assertThat(row("SELECT * FROM driver_car WHERE id = " + contract)).containsEntry("concluded", true);
        assertThat(row("SELECT end_date FROM driver_car WHERE id = " + contract).get("end_date").toString()).isEqualTo("2026-10-20");
        assertThat(put).isInstanceOfSatisfying(BadRequestAlertException.class, e -> assertThat(e.getErrorKey()).isEqualTo("drivercarreturnedbychecklist"));
    }

    @Test
    void aPutEditOfThePrimaryReadBeforeTheReserveIsReturnedKeepsThePrimaryRestored() throws Exception {
        long driver = newDriver("Race Primary", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 10, "FULL"));
        RacingRoutes routes = pausedAfterFirstRead("findByCurrentUserAndId", false);

        Object put = race(routes, () -> putEdit(routes.driverCars, primary, driver, 777), () -> finalizeAs(draft, null));

        assertThat(put).isInstanceOf(DriverCar.class);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", false).containsEntry("concluded", false);
        assertThat(((Number) row("SELECT warranty FROM driver_car WHERE id = " + primary).get("warranty")).intValue()).isEqualTo(777);
    }

    @Test
    void aReturnReadBeforeADevolucaoOfTheSameReserveIsRefusedAndKeepsTheChecklistDate() throws Exception {
        long driver = newDriver("Race Return", null);
        long primary = newContract(newCar(0), driver, false, true, null);
        long reserveCar = newCar(0);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 10, "FULL"));
        RacingRoutes routes = pausedAfterFirstRead("findByCurrentUserAndId", false);

        Object returned = race(routes, () -> routes.driverCars.returnReserveCar(reserve, LocalDate.of(2026, 10, 25)), () -> finalizeAs(draft, null));

        assertThat(returned).isInstanceOfSatisfying(BadRequestAlertException.class, e -> assertThat(e.getErrorKey()).isEqualTo("drivercarreservealreadyreturned"));
        assertThat(row("SELECT end_date FROM driver_car WHERE id = " + reserve).get("end_date").toString()).isEqualTo("2026-10-20");
        assertThat(row("SELECT suspended FROM driver_car WHERE id = " + primary).get("suspended")).isEqualTo(false);
    }

    @Test
    void aNewContractReadBeforeAnEntregaOnTheSameCarIsRefused() throws Exception {
        long checklistDriver = newDriver("Race Entrega", null);
        long screenDriver = newDriver("Race Tela", null);
        long car = newCar(0);
        long draft = draft(ChecklistType.ENTREGA, checklistDriver, car, null, payload("2026-10-07", 10, "FULL"));
        RacingRoutes routes = pausedAfterFirstRead("findByCurrentUserAndId", true);

        Object created = race(
            routes,
            () -> {
                DriverCar body = new DriverCar();
                body.setDriver(drivers.findById(screenDriver).orElseThrow());
                body.setStartDate(LocalDate.of(2026, 10, 7));
                body.setConcluded(false);
                return unchecked(() -> routes.driverCars.createDriverCarByCar(car, null, body)).getBody();
            },
            () -> finalizeAs(draft, null)
        );

        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car + " AND concluded = false AND suspended = false")).isEqualTo(1);
        assertThat(created).isInstanceOfSatisfying(BadRequestAlertException.class, e -> assertThat(e.getErrorKey()).isEqualTo("activedriverexists"));
    }

    // ------------------------------------------------------------------ finalization by status

    @Test
    void finalizationByStatusDraftFinalSentAndCanceled() throws Exception {
        long driver = newDriver("Status", null);
        long car = newCar(0);
        long draft = legacyDocument(driver, car, "DRAFT");
        long sent = legacyDocument(driver, car, "SENT");
        long canceled = legacyDocument(driver, car, "CANCELED");
        as(OWNER_A);

        assertThat(finalizeTransaction.execute(status -> documents.finalizeDocument(draft, null)).getBody().getStatus().name()).isEqualTo("FINAL");
        assertThat(finalizeTransaction.execute(status -> documents.finalizeDocument(draft, null)).getBody().getStatus().name()).isEqualTo("FINAL");
        assertThat(finalizeTransaction.execute(status -> documents.finalizeDocument(sent, null)).getBody().getStatus().name()).isEqualTo("SENT");
        assertRejected(() -> finalizeTransaction.execute(status -> documents.finalizeDocument(canceled, null)), "documentcanceled");

        assertThat(row("SELECT status FROM driver_document WHERE id = " + draft)).containsEntry("status", "FINAL");
        assertThat(row("SELECT status FROM driver_document WHERE id = " + sent)).containsEntry("status", "SENT");
        assertThat(row("SELECT status FROM driver_document WHERE id = " + canceled)).containsEntry("status", "CANCELED");
    }

    private static long legacyDocument(long driver, long car, String status) throws SQLException {
        long id = IDS.incrementAndGet();
        exec(
            "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES (" +
            id + ",'RECIBO_ALUGUEL'," + driver + "," + car + ",93001,'" + status + "','{}',NOW(6),NOW(6))"
        );
        return id;
    }

    // ------------------------------------------------------------------ emergency contacts vs the vínculo form

    @Test
    void aVinculoFormOpenedBeforeTheEntregaKeepsTheContactsTheChecklistFilled() throws Exception {
        long driver = newDriver("Contato Corrida", null);
        long car = newCar(0);
        long contract = newContract(car, driver, false, false, null);
        long draft = draft(ChecklistType.ENTREGA, driver, car, null, payload("2026-10-07", 10, "FULL"));
        RacingRoutes routes = pausedAfterFirstRead("findByCurrentUserAndId", false);
        // The form was loaded with empty contacts and the user changed only the warranty.
        Object put = race(routes, () -> putForm(routes.driverCars, contract, formDriver(driver, "", "", List.of("", "")), 300), () -> finalizeAs(draft, null));

        assertThat(put).isInstanceOf(DriverCar.class);
        assertThat(row("SELECT * FROM driver WHERE id = " + driver))
            .containsEntry("emergency_contact", "11999990000")
            .containsEntry("emergency_contact_second", "11988880000");
        assertThat(((Number) row("SELECT warranty FROM driver_car WHERE id = " + contract).get("warranty")).intValue()).isEqualTo(300);
    }

    @Test
    void theVinculoFormStillEditsTheContactsItShowedAndRefusesAnEditOverAChangedOne() throws Exception {
        long driver = newDriver("Contato Edicao", "Ana - 1");
        exec("UPDATE driver SET emergency_contact_second = 'Carlos - 2' WHERE id = " + driver);
        long contract = newContract(newCar(0), driver, false, false, null);
        as(OWNER_A);

        // Legitimate edit of what the form showed: applied (the untouched one stays).
        routeTransaction.execute(status -> putForm(driverCars, contract, formDriver(driver, "Mãe - 3", "Carlos - 2", List.of("Ana - 1", "Carlos - 2")), 0));
        assertThat(row("SELECT * FROM driver WHERE id = " + driver)).containsEntry("emergency_contact", "Mãe - 3").containsEntry("emergency_contact_second", "Carlos - 2");

        // A stale form (it showed "Ana - 1") editing a contact that changed meanwhile: refused, nothing changes.
        assertRejected(
            () -> routeTransaction.execute(status -> putForm(driverCars, contract, formDriver(driver, "Pai - 4", "Carlos - 2", List.of("Ana - 1", "Carlos - 2")), 999)),
            "driveremergencycontactschanged"
        );
        assertThat(row("SELECT emergency_contact FROM driver WHERE id = " + driver)).containsEntry("emergency_contact", "Mãe - 3");
        assertThat(((Number) row("SELECT warranty FROM driver_car WHERE id = " + contract).get("warranty")).intValue()).isZero();

        // A client that does not say what it loaded only fills empty contacts.
        exec("UPDATE driver SET emergency_contact_second = NULL WHERE id = " + driver);
        routeTransaction.execute(status -> putForm(driverCars, contract, formDriver(driver, "", "Tia - 5", null), 0));
        assertThat(row("SELECT * FROM driver WHERE id = " + driver)).containsEntry("emergency_contact", "Mãe - 3").containsEntry("emergency_contact_second", "Tia - 5");
    }

    @Test
    void aNewVinculoFormForAnExistingDriverNeverErasesItsCurrentContacts() throws Exception {
        long driver = newDriver("Contato Novo Vinculo", "Ana - 1");
        long car = newCar(0);
        as(OWNER_A);
        DriverCar body = new DriverCar();
        body.setDriver(formDriver(driver, "", "", List.of("", "")));
        body.setStartDate(LocalDate.of(2026, 1, 1));
        body.setEndDate(LocalDate.of(2026, 2, 1));
        body.setConcluded(true);

        routeTransaction.execute(status -> unchecked(() -> driverCars.createDriverCarByCar(car, null, body)));

        assertThat(row("SELECT emergency_contact FROM driver WHERE id = " + driver)).containsEntry("emergency_contact", "Ana - 1");
        assertThat(count("SELECT COUNT(*) FROM driver_car WHERE car_id = " + car + " AND driver_id = " + driver)).isEqualTo(1);
    }

    /** The driver as the vínculo form sends it (a new object built from JSON): its data, the contacts, what it loaded. */
    private static com.localuz.domain.Driver formDriver(long id, String first, String second, List<String> loaded) {
        Map<String, Object> stored = row("SELECT name, cpf FROM driver WHERE id = " + id);
        com.localuz.domain.Driver driver = new com.localuz.domain.Driver();
        driver.setId(id);
        driver.setName((String) stored.get("name"));
        driver.setCpf((String) stored.get("cpf"));
        driver.setEmergencyContact(first);
        driver.setEmergencyContactSecond(second);
        driver.setLoadedEmergencyContacts(loaded == null ? null : new ArrayList<>(loaded));
        return driver;
    }

    private static DriverCar putForm(DriverCarResource resource, long contract, com.localuz.domain.Driver driver, int warranty) {
        DriverCar body = new DriverCar();
        body.setId(contract);
        body.setDriver(driver);
        body.setStartDate(LocalDate.of(2026, 9, 1));
        body.setConcluded(false);
        body.setWarranty(java.math.BigDecimal.valueOf(warranty));
        return resource.updateDriverCarById(contract, body).getBody();
    }

    // ------------------------------------------------------------------ lock order (deadlocks)
    // /restore of the primary locks the primary's car, then the driver. Returning the reserve (by /return or by a
    // Devolução) also needs the primary's car: in the same order (cars, then driver) they wait, never deadlock.

    @Test
    void returningTheReserveWhileItsPrimaryIsBeingRestoredNeverDeadlocks() throws Exception {
        long driver = newDriver("Lock Return", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);

        List<Object> results = restoreRacing(primaryCar, primary, () -> driverCars.returnReserveCar(reserve, LocalDate.of(2026, 10, 20)));

        // /restore waited for nothing it did not need and was refused by its own rule (the driver is still on the
        // reserve), never chosen as a deadlock victim; the return went on afterwards.
        assertThat(results.get(0)).isInstanceOf(DriverAssignmentConflictException.class);
        assertThat(results).noneSatisfy(result -> assertThat(String.valueOf(result)).containsIgnoringCase("deadlock"));
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT * FROM driver_car WHERE id = " + primary)).containsEntry("suspended", false).containsEntry("concluded", false);
    }

    @Test
    void aDevolucaoOfTheReserveWhileItsPrimaryIsBeingRestoredNeverDeadlocks() throws Exception {
        long driver = newDriver("Lock Devolucao", null);
        long primaryCar = newCar(0);
        long reserveCar = newCar(0);
        long primary = newContract(primaryCar, driver, false, true, null);
        long reserve = newContract(reserveCar, driver, false, false, primary);
        long draft = draft(ChecklistType.DEVOLUCAO, driver, reserveCar, reserve, payload("2026-10-20", 10, "FULL"));

        List<Object> results = restoreRacing(primaryCar, primary, () -> finalizeAs(draft, null));

        // /restore waited for nothing it did not need and was refused by its own rule (the driver is still on the
        // reserve), never chosen as a deadlock victim; the return went on afterwards.
        assertThat(results.get(0)).isInstanceOf(DriverAssignmentConflictException.class);
        assertThat(results).noneSatisfy(result -> assertThat(String.valueOf(result)).containsIgnoringCase("deadlock"));
        assertThat(results.get(1)).isInstanceOf(DocumentDTO.class);
        assertThat(row("SELECT concluded FROM driver_car WHERE id = " + reserve).get("concluded")).isEqualTo(true);
        assertThat(row("SELECT suspended FROM driver_car WHERE id = " + primary).get("suspended")).isEqualTo(false);
        assertThat(count("SELECT COUNT(*) FROM inspection WHERE origin_document_id = " + draft)).isEqualTo(1);
    }

    /**
     * /restore of the primary paused right after it locked the primary's car; meanwhile the other operation runs until
     * it waits on a lock; then /restore goes on. Returns [restore result, other result].
     */
    private static List<Object> restoreRacing(long primaryCar, long primary, Callable<Object> other) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean();
        EntityManager pausing = (EntityManager) Proxy.newProxyInstance(
            EntityManager.class.getClassLoader(),
            new Class<?>[] { EntityManager.class },
            (proxy, called, args) -> {
                Object result;
                try {
                    result = called.invoke(shared, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
                boolean carLocked =
                    called.getName().equals("refresh") &&
                    args != null &&
                    args.length == 2 &&
                    args[0] instanceof com.localuz.domain.Car &&
                    ((com.localuz.domain.Car) args[0]).getId() == primaryCar;
                if (carLocked && paused.compareAndSet(false, true)) {
                    locked.countDown();
                    assertThat(go.await(60, TimeUnit.SECONDS)).isTrue();
                }
                return result;
            }
        );
        DriverCarRepository contracts = repositories.getRepository(DriverCarRepository.class);
        CarRepository cars = repositories.getRepository(CarRepository.class);
        DriverCarResource restoring = new DriverCarResource(
            contracts,
            cars,
            repositories.getRepository(AddressRepository.class),
            drivers,
            new DriverAssignmentService(contracts, drivers, cars, repositories.getRepository(PendencyRepository.class), pausing)
        );
        ReflectionTestUtils.setField(restoring, "applicationName", "localmaisApp");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Object> restore = pool.submit(() -> inRoute(() -> restoring.restorePrimaryContract(primary)));
        assertThat(locked.await(60, TimeUnit.SECONDS)).as("restore locked the primary's car").isTrue();
        Future<Object> racing = pool.submit(() -> inRoute(other));
        for (int i = 0; i < 100 && count("SELECT COUNT(*) FROM performance_schema.data_lock_waits") == 0; i++) {
            Thread.sleep(100);
        }
        assertThat(count("SELECT COUNT(*) FROM performance_schema.data_lock_waits")).as("the other operation waits on a lock").isPositive();
        go.countDown();
        List<Object> results = List.of(restore.get(90, TimeUnit.SECONDS), racing.get(90, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }

    private static Object inRoute(Callable<Object> route) {
        as(OWNER_A);
        try {
            return routeTransaction.execute(status -> unchecked(route));
        } catch (Throwable error) {
            return error;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** A DriverCarResource (and its assignment service) whose repository pauses once after the route's first read. */
    private static final class RacingRoutes {

        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch go = new CountDownLatch(1);
        DriverCarResource driverCars;
    }

    private static RacingRoutes pausedAfterFirstRead(String method, boolean onCarRepository) {
        RacingRoutes routes = new RacingRoutes();
        java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean();
        DriverCarRepository realContracts = repositories.getRepository(DriverCarRepository.class);
        CarRepository realCars = repositories.getRepository(CarRepository.class);
        Object target = onCarRepository ? realCars : realContracts;
        Class<?> type = onCarRepository ? CarRepository.class : DriverCarRepository.class;
        Object pausing = Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (proxy, called, args) -> {
                Object result;
                try {
                    result = called.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
                if (called.getName().equals(method) && paused.compareAndSet(false, true)) {
                    routes.read.countDown();
                    assertThat(routes.go.await(60, TimeUnit.SECONDS)).isTrue();
                }
                return result;
            }
        );
        DriverCarRepository contracts = onCarRepository ? realContracts : (DriverCarRepository) pausing;
        CarRepository cars = onCarRepository ? (CarRepository) pausing : realCars;
        DriverAssignmentService assignments = assignmentService(contracts, cars);
        routes.driverCars = new DriverCarResource(contracts, cars, repositories.getRepository(AddressRepository.class), drivers, assignments);
        ReflectionTestUtils.setField(routes.driverCars, "applicationName", "localmaisApp");
        return routes;
    }

    /** Runs the route (in the transaction of its production annotation) paused after its first read; meanwhile runs the checklist. */
    private static Object race(RacingRoutes routes, Callable<Object> route, Runnable meanwhile) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Object> paused = pool.submit(() -> {
            as(OWNER_A);
            try {
                return routeTransaction.execute(status -> unchecked(route));
            } catch (Throwable error) {
                return error;
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        assertThat(routes.read.await(60, TimeUnit.SECONDS)).as("the route read its data").isTrue();
        meanwhile.run();
        routes.go.countDown();
        Object result = paused.get(90, TimeUnit.SECONDS);
        pool.shutdown();
        return result;
    }

    private static DriverCar putEdit(DriverCarResource resource, long contract, long driver, int warranty) {
        DriverCar body = new DriverCar();
        body.setId(contract);
        body.setDriver(drivers.findById(driver).orElseThrow());
        body.setStartDate(LocalDate.of(2026, 9, 1));
        body.setConcluded(false);
        body.setWarranty(java.math.BigDecimal.valueOf(warranty));
        return resource.updateDriverCarById(contract, body).getBody();
    }

    private static com.localuz.web.rest.ExpenseResource expenses() {
        com.localuz.web.rest.ExpenseResource resource = new com.localuz.web.rest.ExpenseResource(
            repositories.getRepository(ExpenseRepository.class),
            realInspections()
        );
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
        return resource;
    }

    private static DriverAssignmentService assignmentService(DriverCarRepository contracts, CarRepository cars) {
        return new DriverAssignmentService(contracts, drivers, cars, repositories.getRepository(PendencyRepository.class), shared);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> payload(String date, Object km, String fuel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChecklistService.DATE_KEY, date);
        payload.put(ChecklistService.ODOMETER_KEY, km);
        payload.put(ChecklistService.FUEL_KEY, fuel);
        payload.put(
            "emergencyContacts",
            List.of(Map.of("nome", "Ana Souza", "telefone", "11999990000"), Map.of("nome", "Carlos Lima", "telefone", "11988880000"))
        );
        payload.put(
            "tires",
            Map.of(
                "positions",
                List.of(
                    Map.of("posicao", "Dianteiro esquerdo", "marca", "Pirelli", "estado", "70-90%"),
                    Map.of("posicao", "Estepe", "marca", "Goodyear", "estado", "BOM")
                )
            )
        );
        payload.put("avariasTexto", "Risco na porta");
        return payload;
    }

    private static long draft(ChecklistType type, long driver, long car, Long driverCarId, Map<String, Object> payload) {
        as(OWNER_A);
        DocumentSaveDTO request = new DocumentSaveDTO();
        request.setType(DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST);
        request.setDriverId(driver);
        request.setCarId(car);
        request.setChecklistType(type);
        request.setDriverCarId(driverCarId);
        request.setPayload(new LinkedHashMap<>(payload));
        return transaction.execute(status -> unchecked(() -> documents.createDocument(request)).getBody().getId());
    }

    private static DocumentDTO finalizeAs(long documentId, DriverAssignmentType assignment) {
        as(OWNER_A);
        return finalizeTransaction.execute(status -> documents.finalizeDocument(documentId, assignment).getBody());
    }

    private static Inspection inspectionCopy(long id) {
        Inspection stored = shared.find(Inspection.class, id);
        Inspection copy = new Inspection();
        copy.setId(stored.getId());
        copy.setDate(stored.getDate());
        copy.setOdometer(stored.getOdometer());
        copy.setDriverName(stored.getDriverName());
        // The inspection form always sends the five tire slots (a new one for an empty slot).
        copy.setLeftFront(tireOrNew(stored.getLeftFront()));
        copy.setRightFront(tireOrNew(stored.getRightFront()));
        copy.setLeftBack(tireOrNew(stored.getLeftBack()));
        copy.setRightBack(tireOrNew(stored.getRightBack()));
        copy.setSpare(tireOrNew(stored.getSpare()));
        return copy;
    }

    private static com.localuz.domain.Tire tireOrNew(com.localuz.domain.Tire tire) {
        return tire != null ? tire : new com.localuz.domain.Tire();
    }

    /** Driver of account A (an old concluded contract on the history car makes it belong to the account). */
    private static long newDriver(String name, String emergencyContact) throws SQLException {
        long id = IDS.incrementAndGet();
        exec(
            "INSERT INTO driver (id,name,cpf,emergency_contact) VALUES (" +
            id +
            ",'" +
            name +
            "','" +
            String.format("%011d", id) +
            "'," +
            (emergencyContact == null ? "NULL" : "'" + emergencyContact + "'") +
            ")"
        );
        exec("INSERT INTO driver_car (id,car_id,driver_id,concluded,start_date,suspended) VALUES (" + IDS.incrementAndGet() + ",93999," + id + ",true,'2025-01-01',false)");
        return id;
    }

    private static long newCar(double odometer) throws SQLException {
        long id = IDS.incrementAndGet();
        exec("INSERT INTO car (id,user_id,plate,model,active,odometer) VALUES (" + id + ",93001,'C" + id + "','Onix',true," + odometer + ")");
        return id;
    }

    private static long newContract(long car, long driver, boolean concluded, boolean suspended, Long primary) throws SQLException {
        long id = IDS.incrementAndGet();
        exec(
            "INSERT INTO driver_car (id,car_id,driver_id,concluded,start_date,suspended,primary_driver_car_id) VALUES (" +
            id + "," + car + "," + driver + "," + concluded + ",'2026-09-01'," + suspended + "," + (primary == null ? "NULL" : primary) + ")"
        );
        return id;
    }

    private static String sideEffects(long driver, long car) throws SQLException {
        try (Connection connection = connection()) {
            return (
                rows(connection, "SELECT id,concluded,suspended,end_date,primary_driver_car_id FROM driver_car WHERE driver_id = " + driver + " OR car_id = " + car + " ORDER BY id") +
                rows(connection, "SELECT emergency_contact,emergency_contact_second FROM driver WHERE id = " + driver) +
                rows(connection, "SELECT odometer FROM car WHERE id = " + car) +
                rows(connection, "SELECT COUNT(*) FROM inspection WHERE car_id = " + car) +
                rows(connection, "SELECT COUNT(*) FROM expense")
            );
        }
    }

    private static void as(String login) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new User(login, "x", List.of()), null, List.of()));
    }

    private static List<Object> concurrently(int n, IntFunction<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int attempt = i;
            futures.add(
                pool.submit(() -> {
                    as(OWNER_A);
                    ready.countDown();
                    go.await();
                    try {
                        return task.apply(attempt);
                    } catch (Throwable error) {
                        return error;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                })
            );
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(90, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private static <T> T unchecked(Callable<T> call) {
        try {
            return call.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertRejected(ThrowableAssert.ThrowingCallable call, String errorKey) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BadRequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo(errorKey));
    }

    private static Map<String, Object> row(String sql) {
        try (Connection connection = connection(); ResultSet rows = connection.createStatement().executeQuery(sql)) {
            assertThat(rows.next()).as(sql).isTrue();
            Map<String, Object> values = new HashMap<>();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                Object value = rows.getObject(column);
                values.put(rows.getMetaData().getColumnLabel(column).toLowerCase(), value instanceof Integer ? ((Integer) value).longValue() : value);
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long count(String sql) {
        try (Connection connection = connection(); ResultSet rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            commit(connection);
        }
    }

    private static String legacyRows(Connection connection) throws SQLException {
        return (
            rows(connection, "SELECT id,type,status,driver_id,car_id,payload_json FROM driver_document WHERE id = 93900") +
            rows(connection, "SELECT id,date,driver_name,odometer,car_id FROM inspection WHERE id = 93900")
        );
    }

    private static String rowsOf(String sql) throws SQLException {
        try (Connection connection = connection()) {
            return rows(connection, sql);
        }
    }

    private static String rows(Connection connection, String sql) throws SQLException {
        StringBuilder out = new StringBuilder();
        try (ResultSet rows = connection.createStatement().executeQuery(sql)) {
            while (rows.next()) {
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    out.append(rows.getString(column)).append(';');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static List<String> uniqueIndexes(Connection connection, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (indexes.next()) {
                names.add(indexes.getString("INDEX_NAME"));
            }
        }
        return names;
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private static Liquibase liquibase(Connection connection) throws Exception {
        return new Liquibase(
            "config/liquibase/master.xml",
            new ClassLoaderResourceAccessor(),
            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
        );
    }

    private static void commit(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }
}
