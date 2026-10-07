package com.localuz.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.PendencyOriginType;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverChargeService;
import com.localuz.service.DriverChargeService.Outcome;
import com.localuz.service.UserService;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import com.localuz.service.dto.FineChargeRequest;
import com.localuz.service.dto.MaintenanceChargeSummaryDTO;
import com.localuz.service.dto.PendencyPaymentDTO;
import com.localuz.service.dto.SharedMaintenanceChargeRequest;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.service.CarService;
import com.localuz.web.rest.DocumentResource;
import com.localuz.web.rest.MaintenanceResource;
import com.localuz.web.rest.PendencyResource;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.IdempotencyConflictException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Real MySQL + production Liquibase: fines and shared maintenance as pendencies of the driver. Upgrade path with
 * legacy rows, the database constraints, and real concurrency (threads with their own transactions) against the
 * production services. Uses FROTTO_TEST_MYSQL_JDBC_URL in a throwaway schema, otherwise Testcontainers; skipped
 * when neither is available.
 */
class DriverChargePersistenceTest {

    private static final String ORIGIN_CHANGESET = "20261007000000";
    private static final String OWNER_A = "charge-owner-a";
    private static final String OWNER_B = "charge-owner-b";
    // Account A: cars Onix / HB20 / Argo; João (Onix -> HB20 reserve -> Argo), Maria and Pedro later on the Onix.
    private static final long ONIX = 92001, HB20 = 92002, ARGO = 92003, CAR_B = 92004;
    private static final long JOAO = 92001, MARIA = 92002, PEDRO = 92003, DRIVER_B = 92004;
    private static final long JOAO_ONIX = 92001, JOAO_HB20_RESERVE = 92002, JOAO_ARGO = 92003, MARIA_ONIX = 92004;
    private static final long PEDRO_ONIX = 92005, B_CONTRACT = 92006, NO_DRIVER_CONTRACT = 92007;
    private static final String EXTERNAL_JDBC_URL = System.getenv("FROTTO_TEST_MYSQL_JDBC_URL");
    private static final boolean USE_EXTERNAL_DATABASE = EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();

    private static MySQLContainer<?> mysql;
    private static String adminUrl;
    private static String jdbcUrl;
    private static String schema;
    private static String user;
    private static String password;
    private static EntityManagerFactory factory;
    private static String legacyPendenciesBefore;
    private static String legacyDocumentsBefore;
    private static String carAccountingBefore;

    private static PendencyRepository pendencies;
    private static DriverChargeService charges;
    private static DebtConfessionService confessions;
    private static DocumentResource documentResource;
    private static PendencyResource pendencyResource;
    private static MaintenanceResource maintenanceResource;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void schemaAndServices() throws Exception {
        if (USE_EXTERNAL_DATABASE) {
            user = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_USER", "root");
            password = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_PASSWORD", "");
            schema = "frotto_charge_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
            // Upgrade path: everything before this etapa, the legacy rows, then the new changeset.
            Liquibase previous = liquibase(connection);
            previous.getDatabaseChangeLog().getChangeSets().removeIf(change -> change.getId().startsWith(ORIGIN_CHANGESET));
            previous.update(new Contexts("test"));
            assertThat(columnExists(connection, "pendency", "origin_type")).isFalse();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO jhi_user (id,login,password_hash,activated,created_by,created_date) VALUES " +
                    "(92001,'" + OWNER_A + "',REPEAT('x',60),true,'test',NOW(6)),(92002,'" + OWNER_B + "',REPEAT('x',60),true,'test',NOW(6))"
                );
                statement.executeUpdate(
                    "INSERT INTO car (id,user_id,plate,model,active) VALUES (92001,92001,'ONX1A11','Onix',true)," +
                    "(92002,92001,'HBV2B22','HB20',true),(92003,92001,'ARG3C33','Argo',true),(92004,92002,'BBB9B99','Gol',true)"
                );
                statement.executeUpdate(
                    "INSERT INTO driver (id,name,cpf) VALUES (92001,'João Silva','11111111111'),(92002,'Maria Souza','22222222222')," +
                    "(92003,'Pedro Lima','33333333333'),(92004,'Motorista B','44444444444')"
                );
                statement.executeUpdate(
                    "INSERT INTO driver_car (id,car_id,driver_id,concluded,start_date,suspended,primary_driver_car_id) VALUES " +
                    "(92001,92001,92001,true,'2026-08-01',false,NULL)," +
                    "(92002,92002,92001,true,'2026-09-01',false,92001)," +
                    "(92003,92003,92001,false,'2026-09-20',false,NULL)," +
                    "(92004,92001,92002,false,'2026-09-21',false,NULL)," +
                    "(92005,92001,92003,true,'2026-07-01',false,NULL)," +
                    "(92006,92004,92004,false,'2026-01-01',false,NULL)," +
                    "(92007,92001,NULL,true,'2024-01-01',false,NULL)"
                );
                statement.executeUpdate(
                    "INSERT INTO maintenance (id,date,local,cost,car_id) VALUES " +
                    "(92101,'2026-09-10','Oficina Centro',1000.00,92001),(92103,'2026-09-05','Oficina Reserva',350.00,92002)," +
                    "(92104,'2026-09-01','Oficina B',500.00,92004),(92105,'2026-09-12','Oficina Centro',1200.00,92001)," +
                    "(92106,'2026-09-25','Oficina Argo',900.00,92003),(92107,'2026-09-14','Oficina Centro',1000.00,92001)," +
                    "(92108,'2026-09-15','Oficina Centro',800.00,92001),(92109,'2026-09-16','Oficina Centro',2000.00,92001)," +
                    "(92110,'2026-09-17','Oficina Centro',1000.00,92001),(92111,'2026-09-17','Oficina Centro',1000.00,92001)," +
                    "(92112,'2026-09-17','Oficina Centro',1000.00,92001),(92113,'2026-09-17','Oficina Centro',1000.00,92001)," +
                    "(92114,'2026-09-17','Oficina Centro',1000.00,92001),(92115,'2026-09-18','Oficina Centro',300.00,92001)"
                );
                // Legacy debts and documents: they must come out of the migration exactly as they went in.
                statement.executeUpdate(
                    "INSERT INTO pendency (id,name,cost,date,status,paid_amount,remaining_amount,driver_car_id,debtor_driver_id,note) VALUES " +
                    "(92901,'Multa AIT 123',195.00,'2026-08-20','OPEN',0,195.00,92001,92001,'Local: Av. Brasil | Obs: ')," +
                    "(92902,'Rateio manutenção: freio',300.00,'2026-08-25','PARTIALLY_PAID',100.00,200.00,92001,92001,'Oficina: X | Obs: ')," +
                    "(92903,'Sem devedor',50.00,'2024-02-01','OPEN',0,50.00,92007,NULL,NULL)"
                );
                statement.executeUpdate(
                    "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES " +
                    "(92901,'MULTA',92001,92001,92001,'FINAL','{\"valor\":195,\"ait\":\"123\"}',NOW(6),NOW(6))," +
                    "(92902,'MANUTENCAO_COMPARTILHADA',92001,92001,92001,'FINAL','{\"valorTotal\":900,\"parteMotoristaValor\":300}',NOW(6),NOW(6))"
                );
            }
            commit(connection);
            legacyPendenciesBefore = legacyPendencies(connection);
            legacyDocumentsBefore = legacyDocuments(connection);
            carAccountingBefore = carAccounting(connection);
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

        // Production services over Spring-managed transactions: every thread gets its own transaction/connection.
        JpaTransactionManager transactionManager = new JpaTransactionManager(factory);
        transactionManager.setJpaDialect(new HibernateJpaDialect());
        transaction = new TransactionTemplate(transactionManager);
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(factory);
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(shared);
        repositories.setEvaluationContextProvider(
            new ExtensionAwareQueryMethodEvaluationContextProvider(List.of(new SecurityEvaluationContextExtension()))
        );
        pendencies = repositories.getRepository(PendencyRepository.class);
        DriverCarRepository driverCars = repositories.getRepository(DriverCarRepository.class);
        DriverDocumentRepository documents = repositories.getRepository(DriverDocumentRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        charges = new DriverChargeService(
            pendencies,
            driverCars,
            repositories.getRepository(MaintenanceRepository.class),
            documents,
            objectMapper,
            transactionManager
        );
        confessions = new DebtConfessionService(pendencies, repositories.getRepository(DebtItemTypeRepository.class));
        documentResource =
            new DocumentResource(
                documents,
                repositories.getRepository(DriverRepository.class),
                repositories.getRepository(CarRepository.class),
                driverCars,
                pendencies,
                mock(FileStorageGateway.class),
                mock(UserService.class),
                objectMapper,
                confessions
            );
        ReflectionTestUtils.setField(documentResource, "applicationName", "localmaisApp");
        pendencyResource = new PendencyResource(pendencies, driverCars, repositories.getRepository(DriverRepository.class), confessions, charges);
        ReflectionTestUtils.setField(pendencyResource, "applicationName", "localmaisApp");
        maintenanceResource =
            new MaintenanceResource(
                repositories.getRepository(MaintenanceRepository.class),
                repositories.getRepository(CarRepository.class),
                mock(CarService.class),
                repositories.getRepository(ServiceRepository.class),
                charges
            );
        ReflectionTestUtils.setField(maintenanceResource, "applicationName", "localmaisApp");
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

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ migration and constraints

    @Test
    void upgradeKeepsEveryLegacyRowAsItWasAndInventsNoOrigin() throws Exception {
        try (Connection connection = connection()) {
            assertThat(legacyPendencies(connection)).isEqualTo(legacyPendenciesBefore);
            assertThat(legacyDocuments(connection)).isEqualTo(legacyDocumentsBefore);
            assertThat(
                countRows(
                    connection,
                    "SELECT COUNT(*) FROM pendency WHERE id IN (92901,92902,92903) AND (origin_type IS NOT NULL OR origin_maintenance_id IS NOT NULL " +
                    "OR origin_document_id IS NOT NULL OR idempotency_key IS NOT NULL OR fine_ait IS NOT NULL)"
                )
            )
                .isZero();
            assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_document WHERE origin_pendency_id IS NOT NULL AND id IN (92901,92902)")).isZero();
            for (String column : List.of("origin_type", "origin_maintenance_id", "origin_document_id", "idempotency_key", "fine_ait", "fine_due_date")) {
                assertThat(nullable(connection, "pendency", column)).as(column).isTrue();
            }
            assertThat(nullable(connection, "driver_document", "origin_pendency_id")).isTrue();
            assertThat(uniqueIndexes(connection, "pendency")).contains("ux_pendency_origin_document", "ux_pendency_idempotency_key");
            assertThat(uniqueIndexes(connection, "pendency")).doesNotContain("idx_pendency_origin_maintenance");
            assertThat(uniqueIndexes(connection, "driver_document")).contains("ux_driver_document_origin_pendency");
        }
    }

    @Test
    void constraintsRejectASecondPendencyOfOneDocumentOrOneKeyButNotSeveralChargesOfOneMaintenance() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate(insertRaw(92950, "origin_document_id", "92901"));
            assertThatThrownBy(() -> statement.executeUpdate(insertRaw(92951, "origin_document_id", "92901")))
                .isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
            statement.executeUpdate(insertRaw(92952, "idempotency_key", "'raw-key-0000000001'"));
            assertThatThrownBy(() -> statement.executeUpdate(insertRaw(92953, "idempotency_key", "'raw-key-0000000001'")))
                .isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
            // Several shares of one maintenance are legitimate; NULL never collides.
            statement.executeUpdate(insertRaw(92954, "origin_maintenance_id", "92115"));
            statement.executeUpdate(insertRaw(92955, "origin_maintenance_id", "92115"));
            statement.executeUpdate(insertRaw(92956, "origin_document_id", "NULL"));
            statement.executeUpdate(insertRaw(92957, "origin_document_id", "NULL"));
            // RESTRICT: a maintenance charged to drivers cannot be deleted, the debts keep their origin.
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM maintenance WHERE id = 92115"))
                .isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
            assertThat(countRows(connection, "SELECT COUNT(*) FROM pendency WHERE id IN (92954,92955) AND origin_maintenance_id = 92115")).isEqualTo(2);
            // A legacy document deleted keeps its debt (SET NULL on origin_document_id).
            statement.executeUpdate(insertRaw(92959, "origin_document_id", "92902"));
            statement.executeUpdate("DELETE FROM driver_document WHERE id = 92902");
            assertThat(countRows(connection, "SELECT COUNT(*) FROM pendency WHERE id = 92959 AND origin_document_id IS NULL")).isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate(insertRaw(92958, "origin_maintenance_id", "99999999")))
                .isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
            connection.rollback();
        }
    }

    // ------------------------------------------------------------------ fines

    @Test
    void fineIsOneDebtOfTheDriverAndARetryReturnsTheSameOne() {
        as(OWNER_A);
        String key = key();
        Outcome<Pendency> first = charges.chargeFine(JOAO_ONIX, fine(key, "195.00"));
        Outcome<Pendency> retry = charges.chargeFine(JOAO_ONIX, fine(key, "195.00"));

        assertThat(first.isCreated()).isTrue();
        assertThat(retry.isCreated()).isFalse();
        assertThat(retry.getValue().getId()).isEqualTo(first.getValue().getId());
        Map<String, Object> row = row(first.getValue().getId());
        assertThat(row)
            .containsEntry("origin_type", "FINE")
            .containsEntry("debtor_driver_id", JOAO)
            .containsEntry("driver_car_id", JOAO_ONIX)
            .containsEntry("fine_ait", "AIT-1")
            .containsEntry("status", "OPEN");
        assertThat(countByKey(key)).isEqualTo(1);
        // The same key with other values is another operation: refused, nothing created.
        assertThatThrownBy(() -> charges.chargeFine(JOAO_ONIX, fine(key, "300.00"))).isInstanceOf(IdempotencyConflictException.class);
        assertThat(countByKey(key)).isEqualTo(1);
    }

    @Test
    void concurrentAttemptsOfTheSameFineCreateExactlyOnePendency() throws Exception {
        String key = key();
        List<Object> results = concurrently(8, OWNER_A, attempt -> charges.chargeFine(JOAO_ONIX, fine(key, "195.00")));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Outcome.class));
        assertThat(results.stream().map(result -> ((Outcome<?>) result).getValue()).map(value -> ((Pendency) value).getId()).distinct()).hasSize(1);
        assertThat(results.stream().filter(result -> ((Outcome<?>) result).isCreated())).hasSize(1);
        assertThat(countByKey(key)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ shared maintenance

    @Test
    void maintenanceKeepsItsCostAndOnlyTheDriverShareBecomesADebt() throws Exception {
        as(OWNER_A);
        Pendency share = charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92105L, "400.00")).getValue();

        Map<String, Object> row = row(share.getId());
        assertThat(row).containsEntry("origin_type", "SHARED_MAINTENANCE").containsEntry("origin_maintenance_id", 92105L);
        assertThat(new BigDecimal(String.valueOf(row.get("cost")))).isEqualByComparingTo("400.00");
        assertThat(row).containsEntry("debtor_driver_id", JOAO);
        try (Connection connection = connection()) {
            assertThat(countRows(connection, "SELECT COUNT(*) FROM maintenance WHERE id = 92105 AND cost = 1200.00")).isEqualTo(1);
        }
        MaintenanceChargeSummaryDTO summary = charges.maintenanceSummary(92105L);
        assertThat(summary.getMaintenanceCost()).isEqualByComparingTo("1200.00");
        assertThat(summary.getAssignedAmount()).isEqualByComparingTo("400.00");
        assertThat(summary.getAvailableAmount()).isEqualByComparingTo("800.00");
    }

    @Test
    void severalDriversShareOneMaintenanceButNeverBeyondItsCostEvenAfterAPayment() {
        as(OWNER_A);
        // A) 300 + 200 of a 2000 maintenance: allowed.
        charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92109L, "300.00"));
        charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92109L, "200.00"));
        assertThat(charges.maintenanceSummary(92109L).getAssignedAmount()).isEqualByComparingTo("500.00");

        // B) 1000: João 600, Pedro 500 refused.
        Pendency joao = charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92101L, "600.00")).getValue();
        assertRejected(() -> charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92101L, "500.00")), "maintenancechargeexceeded");
        // C) João pays his 600: the responsibility stays assigned, Pedro is still refused.
        transaction.execute(status -> pendencyResource.payPendency(joao.getId(), null));
        assertThat(row(joao.getId())).containsEntry("status", "PAID");
        assertRejected(() -> charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92101L, "500.00")), "maintenancechargeexceeded");
        // F) another legitimate operation with a new key, within the limit.
        Outcome<Pendency> pedro = charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92101L, "400.00"));
        assertThat(pedro.isCreated()).isTrue();
        assertThat(row(pedro.getValue().getId())).containsEntry("debtor_driver_id", PEDRO);
        assertThat(charges.maintenanceSummary(92101L).getAssignedAmount()).isEqualByComparingTo("1000.00");
        assertRejected(() -> charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92101L, "0.01")), "maintenancechargeexceeded");
    }

    @Test
    void concurrentChargesOfOneMaintenanceNeverExceedItsCost() throws Exception {
        // D) 1000: João 600 and Pedro 600 at the same time, five rounds on five maintenances.
        for (long maintenance = 92110; maintenance <= 92114; maintenance++) {
            long id = maintenance;
            List<Object> results = concurrently(
                2,
                OWNER_A,
                attempt -> charges.chargeSharedMaintenance(attempt == 0 ? JOAO_ONIX : PEDRO_ONIX, share(key(), id, "600.00"))
            );
            assertThat(results.stream().filter(result -> result instanceof Outcome)).as("winners on %s", id).hasSize(1);
            assertThat(results.stream().filter(result -> result instanceof BadRequestAlertException))
                .allSatisfy(result -> assertThat(((BadRequestAlertException) result).getErrorKey()).isEqualTo("maintenancechargeexceeded"))
                .hasSize(1);
            as(OWNER_A);
            assertThat(charges.maintenanceSummary(id).getAssignedAmount()).isEqualByComparingTo("600.00");
        }
    }

    @Test
    void concurrentRetriesOfTheSameMaintenanceChargeCreateOnePendency() throws Exception {
        // E) the same key from a double click / two tabs at once.
        String key = key();
        List<Object> results = concurrently(6, OWNER_A, attempt -> charges.chargeSharedMaintenance(JOAO_ONIX, share(key, 92107L, "600.00")));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Outcome.class));
        assertThat(results.stream().map(result -> ((Pendency) ((Outcome<?>) result).getValue()).getId()).distinct()).hasSize(1);
        assertThat(countByKey(key)).isEqualTo(1);
        as(OWNER_A);
        assertThat(charges.maintenanceSummary(92107L).getAssignedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void editingTheCostOfAShareKeepsTheMaintenanceLimit() {
        as(OWNER_A);
        Pendency share = charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92108L, "500.00")).getValue();
        charges.chargeSharedMaintenance(PEDRO_ONIX, share(key(), 92108L, "200.00"));

        Pendency tooMuch = edit(share, "700.00");
        assertRejected(() -> transaction.execute(status -> unchecked(() -> pendencyResource.updatePendency(share.getId(), tooMuch))), "maintenancechargeexceeded");
        Pendency fits = edit(share, "600.00");
        transaction.execute(status -> unchecked(() -> pendencyResource.updatePendency(share.getId(), fits)));
        assertThat(new BigDecimal(String.valueOf(row(share.getId()).get("cost")))).isEqualByComparingTo("600.00");
    }

    @Test
    void theMaintenanceMustBeOfTheContractCar() {
        as(OWNER_A);
        assertRejected(() -> charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92106L, "100.00")), "maintenancecarmismatch");
        assertRejected(() -> charges.chargeFine(NO_DRIVER_CONTRACT, fine(key(), "10.00")), "drivercarwithoutdriver");
    }

    // ------------------------------------------------------------------ deleting a maintenance

    @Test
    void aMaintenanceWithoutChargesIsDeletedAsBefore() {
        long id = insertMaintenance(92130, ONIX, "500.00");
        as(OWNER_A);
        transaction.execute(status -> maintenanceResource.deleteMaintenance(id));
        assertThat(count("SELECT COUNT(*) FROM maintenance WHERE id = " + id)).isZero();
    }

    @Test
    void aMaintenanceChargedToADriverCannotBeDeletedAndNothingChanges() {
        long id = insertMaintenance(92131, ONIX, "900.00");
        as(OWNER_A);
        Pendency share = charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), id, "300.00")).getValue();
        PendencyPaymentDTO partial = new PendencyPaymentDTO();
        partial.setAmount(new BigDecimal("100.00"));
        transaction.execute(status -> pendencyResource.addPaymentToPendency(share.getId(), partial));
        Map<String, Object> pendencyBefore = row(share.getId());
        Map<String, Object> maintenanceBefore = single("SELECT * FROM maintenance WHERE id = " + id);

        assertThatThrownBy(() -> transaction.execute(status -> maintenanceResource.deleteMaintenance(id)))
            .isInstanceOfSatisfying(BadRequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo("maintenancehasdrivercharges");
                assertThat(error.getMessage()).isEqualTo("Esta manutenção possui cobranças vinculadas a motorista e não pode ser excluída.");
            });

        assertThat(single("SELECT * FROM maintenance WHERE id = " + id)).isEqualTo(maintenanceBefore);
        assertThat(row(share.getId())).isEqualTo(pendencyBefore);
        assertThat(pendencyBefore)
            .containsEntry("origin_maintenance_id", id)
            .containsEntry("debtor_driver_id", JOAO)
            .containsEntry("status", "PARTIALLY_PAID");
    }

    @Test
    void deletingAndChargingTheSameMaintenanceAtOnceNeverLeavesAnOrphanDebt() throws Exception {
        int deleted = 0;
        int charged = 0;
        for (long id = 92140; id < 92150; id++) {
            long maintenance = insertMaintenance(id, ONIX, "1000.00");
            List<Object> results = concurrently(
                2,
                OWNER_A,
                attempt ->
                    attempt == 0
                        ? transaction.execute(status -> maintenanceResource.deleteMaintenance(maintenance))
                        : charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), maintenance, "400.00"))
            );
            boolean maintenanceExists = count("SELECT COUNT(*) FROM maintenance WHERE id = " + maintenance) == 1;
            long debts = count("SELECT COUNT(*) FROM pendency WHERE origin_maintenance_id = " + maintenance);
            // Either the deletion won (no maintenance, no debt, the charge got 404) or the charge did (both stay).
            if (maintenanceExists) {
                assertThat(debts).isEqualTo(1);
                assertThat(results.get(0)).isInstanceOfSatisfying(
                    BadRequestAlertException.class,
                    error -> assertThat(error.getErrorKey()).isEqualTo("maintenancehasdrivercharges")
                );
                charged++;
            } else {
                assertThat(debts).isZero();
                assertThat(results.get(1)).isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatus().value()).isEqualTo(404));
                deleted++;
            }
        }
        assertThat(deleted + charged).isEqualTo(10);
        // Never a shared-maintenance debt without its maintenance.
        assertThat(
            count(
                "SELECT COUNT(*) FROM pendency p LEFT JOIN maintenance m ON m.id = p.origin_maintenance_id " +
                "WHERE p.origin_type = 'SHARED_MAINTENANCE' AND (p.origin_maintenance_id IS NULL OR m.id IS NULL)"
            )
        )
            .isZero();
    }

    @Test
    void bothSequentialOrdersOfDeletionAndCharge() {
        as(OWNER_A);
        // Deleted first: the charge finds nothing.
        long gone = insertMaintenance(92151, ONIX, "300.00");
        transaction.execute(status -> maintenanceResource.deleteMaintenance(gone));
        assertNotFound(() -> charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), gone, "100.00")));
        // Charged first: the deletion is refused.
        long kept = insertMaintenance(92152, ONIX, "300.00");
        charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), kept, "100.00"));
        assertRejected(() -> transaction.execute(status -> maintenanceResource.deleteMaintenance(kept)), "maintenancehasdrivercharges");
        assertThat(count("SELECT COUNT(*) FROM maintenance WHERE id = " + kept)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ simplified fine and the maintenance options

    @Test
    void aFineWithOnlyValueAndDayIsSavedAndItsDocumentInventsNothing() {
        as(OWNER_A);
        FineChargeRequest minimal = new FineChargeRequest();
        minimal.setIdempotencyKey(key());
        minimal.setAmount(new BigDecimal("130.00"));
        minimal.setInfractionDate(LocalDate.of(2026, 9, 3));

        Pendency fine = charges.chargeFine(JOAO_ONIX, minimal).getValue();

        Map<String, Object> row = row(fine.getId());
        assertThat(row).containsEntry("origin_type", "FINE").containsEntry("debtor_driver_id", JOAO);
        assertThat(row.get("fine_infraction_time")).isNull();
        assertThat(row.get("fine_ait")).isNull();
        String payload = String.valueOf(documentRow(charges.issueDocument(fine.getId()).getValue().getId()).get("payload_json"));
        assertThat(payload).contains("\"dataInfracao\":\"2026-09-03\"", "\"valor\":130.00");
        assertThat(payload).doesNotContain("dataHora", "horaInfracao", "\"ait\"", "\"orgao\"", "\"local\"", "\"enquadramento\"", "\"vencimento\"", "null");
    }

    @Test
    void theChargeableMaintenancesAreThoseOfTheContractCarWithWhatIsLeft() {
        long full = insertMaintenance(92160, ARGO, "300.00");
        long partial = insertMaintenance(92161, ARGO, "500.00");
        as(OWNER_A);
        charges.chargeSharedMaintenance(JOAO_ARGO, share(key(), full, "300.00"));
        charges.chargeSharedMaintenance(JOAO_ARGO, share(key(), partial, "120.00"));

        Map<Long, com.localuz.service.dto.MaintenanceChargeOptionDTO> options = charges
            .chargeableMaintenances(JOAO_ARGO)
            .stream()
            .collect(Collectors.toMap(com.localuz.service.dto.MaintenanceChargeOptionDTO::getId, option -> option));

        // Only the Argo's maintenances (the contract's car): never the Onix or another account's.
        assertThat(options).containsKeys(full, partial, 92106L).doesNotContainKeys(92101L, 92104L);
        assertThat(options.get(full).isChargeable()).isFalse();
        assertThat(options.get(full).getAvailableAmount()).isEqualByComparingTo("0.00");
        assertThat(options.get(partial).getAssignedAmount()).isEqualByComparingTo("120.00");
        assertThat(options.get(partial).getAvailableAmount()).isEqualByComparingTo("380.00");
        assertThat(options.get(partial).getLocal()).isEqualTo("Oficina Reserva");
        assertRejected(() -> charges.chargeSharedMaintenance(JOAO_ARGO, share(key(), full, "0.01")), "maintenancechargeexceeded");

        as(OWNER_B);
        assertNotFound(() -> charges.chargeableMaintenances(JOAO_ARGO));
    }

    // ------------------------------------------------------------------ legacy wizard

    @Test
    void legacyFineAndSharedMaintenanceDocumentsStillOpen() {
        as(OWNER_A);
        com.localuz.service.dto.DocumentDTO fine = transaction.execute(status -> documentResource.getDocumentById(92901L));
        com.localuz.service.dto.DocumentDTO share = transaction.execute(status -> documentResource.getDocumentById(92902L));
        assertThat(fine.getType()).isEqualTo(DocumentType.MULTA);
        assertThat(fine.getStatus()).isEqualTo(DocumentStatus.FINAL);
        assertThat(fine.getPayload()).containsEntry("ait", "123");
        assertThat(share.getType()).isEqualTo(DocumentType.MANUTENCAO_COMPARTILHADA);
        assertThat(share.getPayload()).containsEntry("parteMotoristaValor", 300);
    }

    @Test
    void aNewManualFineOrSharedMaintenanceThroughTheLegacyEndpointCreatesNothing() {
        as(OWNER_A);
        long documentsBefore = count("SELECT COUNT(*) FROM driver_document");
        long pendenciesBefore = countPendencies();
        for (DocumentType type : List.of(DocumentType.MULTA, DocumentType.MANUTENCAO_COMPARTILHADA)) {
            com.localuz.service.dto.DocumentSaveDTO request = new com.localuz.service.dto.DocumentSaveDTO();
            request.setType(type);
            request.setDriverId(JOAO);
            request.setCarId(ONIX);
            request.setStatus(DocumentStatus.FINAL);
            request.setPayload(new java.util.LinkedHashMap<>(Map.of("valor", 195, "parteMotoristaValor", 300)));
            assertRejected(() -> transaction.execute(status -> unchecked(() -> documentResource.createDocument(request))), "documenttypemovedtopendencies");
        }
        assertThat(count("SELECT COUNT(*) FROM driver_document")).isEqualTo(documentsBefore);
        assertThat(countPendencies()).isEqualTo(pendenciesBefore);
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void anotherAccountCanNeitherChargeNorSeeNorIssueAndLearnsNothing() {
        as(OWNER_A);
        String keyOfA = key();
        Pendency fineOfA = charges.chargeFine(JOAO_ONIX, fine(keyOfA, "80.00")).getValue();

        as(OWNER_B);
        assertNotFound(() -> charges.chargeFine(JOAO_ONIX, fine(key(), "80.00")));
        assertNotFound(() -> charges.chargeSharedMaintenance(B_CONTRACT, share(key(), 92101L, "10.00")));
        assertNotFound(() -> charges.maintenanceSummary(92101L));
        assertNotFound(() -> charges.issueDocument(fineOfA.getId()));
        // B reusing A's key on its own contract: refused without revealing A's debt.
        assertThatThrownBy(() -> charges.chargeFine(B_CONTRACT, fine(keyOfA, "80.00"))).isInstanceOf(IdempotencyConflictException.class);
        // Its own maintenance and contract work.
        assertThat(charges.chargeSharedMaintenance(B_CONTRACT, share(key(), 92104L, "100.00")).isCreated()).isTrue();
        as(OWNER_A);
        assertNotFound(() -> charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), 92104L, "10.00")));
    }

    // ------------------------------------------------------------------ documents

    @Test
    void documentIssuedFromAFineIsArchivedOnceAndNeverCreatesAPendency() throws Exception {
        as(OWNER_A);
        Pendency fine = charges.chargeFine(JOAO_ONIX, fine(key(), "195.00")).getValue();
        long pendenciesBefore = countPendencies();

        List<Object> results = concurrently(4, OWNER_A, attempt -> charges.issueDocument(fine.getId()));
        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Outcome.class));
        List<Long> ids = results.stream().map(result -> ((DriverDocument) ((Outcome<?>) result).getValue()).getId()).distinct().collect(Collectors.toList());
        assertThat(ids).hasSize(1);
        as(OWNER_A);
        Outcome<DriverDocument> again = charges.issueDocument(fine.getId());
        assertThat(again.isCreated()).isFalse();
        assertThat(again.getValue().getId()).isEqualTo(ids.get(0));

        Map<String, Object> document = documentRow(ids.get(0));
        assertThat(document).containsEntry("type", "MULTA").containsEntry("status", "FINAL").containsEntry("origin_pendency_id", fine.getId());
        assertThat(String.valueOf(document.get("payload_json")))
            .contains("\"tipo\":\"PENDENCIA\"", "\"pendencyId\":" + fine.getId(), "\"ait\":\"AIT-1\"", "\"dataHora\":\"2026-08-20T10:30\"");
        // G) finalizing it again never runs the legacy pendency creation.
        transaction.execute(status -> documentResource.finalizeDocument(ids.get(0)));
        assertThat(countPendencies()).isEqualTo(pendenciesBefore);
        assertThat(row(fine.getId())).containsEntry("status", "OPEN");
    }

    @Test
    void documentIssuedFromAMaintenanceShareCarriesTheFullCostAndTheShareOnly() {
        as(OWNER_A);
        Pendency share = charges.chargeSharedMaintenance(JOAO_HB20_RESERVE, share(key(), 92103L, "350.00")).getValue();
        long pendenciesBefore = countPendencies();

        DriverDocument document = charges.issueDocument(share.getId()).getValue();

        Map<String, Object> row = documentRow(document.getId());
        assertThat(row).containsEntry("type", "MANUTENCAO_COMPARTILHADA").containsEntry("status", "FINAL").containsEntry("car_id", HB20);
        assertThat(String.valueOf(row.get("payload_json"))).contains("\"valorTotal\":350.00", "\"parteMotoristaValor\":350.00", "\"oficina\":\"Oficina Reserva\"");
        assertThat(countPendencies()).isEqualTo(pendenciesBefore);
    }

    @Test
    void legacyDocumentFinalizedTwiceOrConcurrentlyCreatesExactlyOnePendency() throws Exception {
        long sequential = insertDraftFine(92921);
        long concurrent = insertDraftFine(92922);

        // I) double finalization, one after the other.
        as(OWNER_A);
        transaction.execute(status -> documentResource.finalizeDocument(sequential));
        transaction.execute(status -> documentResource.finalizeDocument(sequential));
        assertThat(pendenciesOfDocument(sequential)).isEqualTo(1);

        // J) four tabs at once.
        List<Object> results = concurrently(4, OWNER_A, attempt -> transaction.execute(status -> documentResource.finalizeDocument(concurrent)));
        assertThat(results).allSatisfy(result -> assertThat(result).isNotInstanceOf(Throwable.class));
        assertThat(pendenciesOfDocument(concurrent)).isEqualTo(1);
        try (Connection connection = connection()) {
            assertThat(
                countRows(connection, "SELECT COUNT(*) FROM pendency WHERE origin_document_id = " + concurrent + " AND origin_type = 'FINE' AND debtor_driver_id = " + JOAO)
            )
                .isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ driver changes and the Confissão

    @Test
    void debtsStayWithJoaoAcrossTransferReserveAndReturnAndMariaInheritsNothing() throws Exception {
        as(OWNER_A);
        // A) fine on the Onix while João drove it (contract later concluded by the transfer to the Argo).
        Pendency fine = charges.chargeFine(JOAO_ONIX, fine(key(), "200.00")).getValue();
        // B) share of the HB20 maintenance during the reserve; the reserve contract is already returned (concluded).
        Pendency share = charges.chargeSharedMaintenance(JOAO_HB20_RESERVE, share(key(), insertMaintenance(92121, HB20, "100.00"), "50.00")).getValue();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            // Later moves of the cars: João's contracts concluded, Maria on the Onix.
            statement.executeUpdate("UPDATE driver_car SET concluded = true WHERE id IN (92001, 92002)");
            commit(connection);
        }
        assertThat(row(fine.getId())).containsEntry("debtor_driver_id", JOAO).containsEntry("driver_car_id", JOAO_ONIX);
        assertThat(row(share.getId())).containsEntry("debtor_driver_id", JOAO).containsEntry("driver_car_id", JOAO_HB20_RESERVE);
        List<Long> mariaDebts = transaction.execute(status ->
            pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(MARIA).stream().map(Pendency::getId).collect(Collectors.toList())
        );
        assertThat(mariaDebts).doesNotContain(fine.getId(), share.getId());
        List<Long> joaoDebts = transaction.execute(status ->
            pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(JOAO).stream().map(Pendency::getId).collect(Collectors.toList())
        );
        assertThat(joaoDebts).contains(fine.getId(), share.getId());
    }

    @Test
    void confessionOfJoaoTakesTheFineTheShareAndOtherDebtsEachWithItsCar() {
        as(OWNER_A);
        // C) fine Onix 200 (100 already paid), share HB20 of a fresh 350 maintenance, damage Argo 400.
        Pendency fine = charges.chargeFine(JOAO_ONIX, fine(key(), "200.00")).getValue();
        PendencyPaymentDTO partial = new PendencyPaymentDTO();
        partial.setAmount(new BigDecimal("100.00"));
        transaction.execute(status -> pendencyResource.addPaymentToPendency(fine.getId(), partial));
        Long maintenance = insertMaintenance(92120, HB20, "350.00");
        Pendency share = charges.chargeSharedMaintenance(JOAO_HB20_RESERVE, share(key(), maintenance, "350.00")).getValue();
        Pendency damage = new Pendency();
        damage.setName("Danos/Avarias");
        damage.setCost(new BigDecimal("400.00"));
        damage.setDate(LocalDate.of(2026, 9, 28));
        Long damageId = transaction.execute(status -> unchecked(() -> pendencyResource.createPendency(JOAO_ARGO, damage)).getBody().getId());

        DebtConfessionPreviewDTO preview = transaction.execute(status -> confessions.preview(List.of(fine.getId(), share.getId(), damageId)));

        assertThat(preview.getDriverId()).isEqualTo(JOAO);
        assertThat(preview.getValorTotal()).isEqualByComparingTo("850.00"); // Q) partial payment: only the 100 left of the fine
        assertThat(preview.getCarId()).isNull();
        Map<Long, DebtConfessionPreviewDTO.Item> items = preview.getItems().stream().collect(Collectors.toMap(DebtConfessionPreviewDTO.Item::getPendencyId, item -> item));
        assertThat(items.get(fine.getId()).getOriginCarPlate()).isEqualTo("ONX1A11");
        assertThat(items.get(fine.getId()).getTypeNameSnapshot()).isEqualTo("Multa de trânsito");
        assertThat(items.get(fine.getId()).getValorItem()).isEqualByComparingTo("100.00");
        assertThat(items.get(share.getId()).getOriginCarPlate()).isEqualTo("HBV2B22");
        assertThat(items.get(share.getId()).getTypeNameSnapshot()).isEqualTo("Manutenção compartilhada");
        assertThat(items.get(damageId).getOriginCarPlate()).isEqualTo("ARG3C33");
        assertThat(items.get(damageId).getTypeNameSnapshot()).isEqualTo("Danos/Avarias");

        // Without the partial payment the three debts are exactly 200 + 350 + 400 = 950.
        assertThat(preview.getItems().stream().map(DebtConfessionPreviewDTO.Item::getCost).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("950.00");

        // 11) another debtor in the selection is still refused.
        Pendency mariaFine = charges.chargeFine(MARIA_ONIX, fine(key(), "50.00")).getValue();
        assertRejected(() -> transaction.execute(status -> confessions.preview(List.of(fine.getId(), mariaFine.getId()))), "pendenciesdifferentdebtors");
    }

    @Test
    void nothingOfTheCarAccountingChanges() throws Exception {
        as(OWNER_A);
        charges.chargeSharedMaintenance(JOAO_ONIX, share(key(), insertMaintenance(92122, ONIX, "100.00"), "1.00"));
        charges.chargeFine(JOAO_ONIX, fine(key(), "10.00"));
        try (Connection connection = connection()) {
            // Maintenance (except the row the constraint test deletes inside a rolled back transaction), expenses, incomes.
            assertThat(carAccounting(connection)).isEqualTo(carAccountingBefore);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static FineChargeRequest fine(String key, String amount) {
        FineChargeRequest request = new FineChargeRequest();
        request.setIdempotencyKey(key);
        request.setAmount(new BigDecimal(amount));
        request.setInfractionDate(LocalDate.of(2026, 8, 20));
        request.setInfractionTime(LocalTime.of(10, 30));
        request.setAit("AIT-1");
        request.setAgency("DETRAN");
        request.setLocation("Av. Brasil, 100");
        request.setClassification("Art. 218");
        request.setDueDate(LocalDate.of(2026, 10, 20));
        return request;
    }

    private static SharedMaintenanceChargeRequest share(String key, Long maintenanceId, String amount) {
        SharedMaintenanceChargeRequest request = new SharedMaintenanceChargeRequest();
        request.setIdempotencyKey(key);
        request.setMaintenanceId(maintenanceId);
        request.setAmount(new BigDecimal(amount));
        request.setNote("Freio trocado por uso indevido");
        return request;
    }

    private static Pendency edit(Pendency pendency, String cost) {
        Pendency edit = new Pendency();
        edit.setId(pendency.getId());
        edit.setName(pendency.getName());
        edit.setDate(pendency.getDate());
        edit.setCost(new BigDecimal(cost));
        return edit;
    }

    private static <T> T unchecked(java.util.concurrent.Callable<T> call) {
        try {
            return call.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key() {
        return "op-" + UUID.randomUUID();
    }

    private static void as(String login) {
        User principal = new User(login, "x", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    /** Runs the task in n threads released at the same instant, each with its own principal and transactions. */
    private static List<Object> concurrently(int n, String login, IntFunction<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int attempt = i;
            futures.add(
                pool.submit(() -> {
                    as(login);
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
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private static void assertRejected(ThrowableAssert.ThrowingCallable call, String errorKey) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BadRequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo(errorKey));
    }

    private static void assertNotFound(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }

    private static long insertDraftFine(long id) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "INSERT INTO driver_document (id,type,driver_id,car_id,jhi_user_id,status,payload_json,created_at,updated_at) VALUES (" +
                id +
                ",'MULTA',92001,92001,92001,'DRAFT','{\"valor\":195,\"ait\":\"999\"}',NOW(6),NOW(6))"
            );
            commit(connection);
        }
        return id;
    }

    private static Long insertMaintenance(long id, long car, String cost) {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO maintenance (id,date,local,cost,car_id) VALUES (" + id + ",'2026-09-06','Oficina Reserva'," + cost + "," + car + ")");
            commit(connection);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return id;
    }

    private static String insertRaw(long id, String column, String value) {
        return (
            "INSERT INTO pendency (id,name,cost,date,status,paid_amount,remaining_amount,driver_car_id," +
            column +
            ") VALUES (" +
            id +
            ",'raw',1.00,'2026-09-01','OPEN',0,1.00,92003," +
            value +
            ")"
        );
    }

    private static Map<String, Object> row(Long pendencyId) {
        return single("SELECT * FROM pendency WHERE id = " + pendencyId);
    }

    private static Map<String, Object> documentRow(Long documentId) {
        return single("SELECT * FROM driver_document WHERE id = " + documentId);
    }

    private static Map<String, Object> single(String sql) {
        try (Connection connection = connection(); ResultSet rows = connection.createStatement().executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            Map<String, Object> values = new java.util.HashMap<>();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                Object value = rows.getObject(column);
                values.put(rows.getMetaData().getColumnLabel(column).toLowerCase(), value instanceof Integer ? ((Integer) value).longValue() : value);
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long countByKey(String key) {
        return count("SELECT COUNT(*) FROM pendency WHERE idempotency_key = '" + key + "'");
    }

    private static long countPendencies() {
        return count("SELECT COUNT(*) FROM pendency");
    }

    private static long pendenciesOfDocument(long documentId) {
        return count("SELECT COUNT(*) FROM pendency WHERE origin_document_id = " + documentId);
    }

    private static long count(String sql) {
        try (Connection connection = connection()) {
            return countRows(connection, sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String legacyPendencies(Connection connection) throws SQLException {
        return rows(
            connection,
            "SELECT id,name,cost,date,status,paid_amount,remaining_amount,IFNULL(paid_at,''),IFNULL(payment_method,''),IFNULL(driver_car_id,'')," +
            "IFNULL(debtor_driver_id,''),IFNULL(note,'') FROM pendency WHERE id IN (92901,92902,92903) ORDER BY id"
        );
    }

    private static String legacyDocuments(Connection connection) throws SQLException {
        return rows(connection, "SELECT id,type,status,driver_id,car_id,payload_json FROM driver_document WHERE id IN (92901,92902) ORDER BY id");
    }

    private static String carAccounting(Connection connection) throws SQLException {
        return (
            rows(connection, "SELECT id,date,local,cost,car_id FROM maintenance WHERE id <> 92115 AND id < 92120 ORDER BY id") +
            "|" +
            rows(connection, "SELECT COUNT(*) FROM car_expense") +
            "|" +
            rows(connection, "SELECT COUNT(*) FROM expense") +
            "|" +
            rows(connection, "SELECT COUNT(*) FROM income")
        );
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

    private static boolean nullable(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            assertThat(columns.next()).as(table + "." + column).isTrue();
            return "YES".equals(columns.getString("IS_NULLABLE"));
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    private static long countRows(Connection connection, String sql) throws SQLException {
        try (ResultSet rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
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
