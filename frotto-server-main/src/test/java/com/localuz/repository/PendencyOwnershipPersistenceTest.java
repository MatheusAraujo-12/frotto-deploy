package com.localuz.repository;

import static org.assertj.core.api.Assertions.*;

import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.DriverAssignmentService;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.query.ExtensionAwareQueryMethodEvaluationContextProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Real MySQL + production Liquibase migrations: the ownership filter of {@link PendencyRepository#findByCurrentUserAndIdIn}
 * evaluated with the real principal SpEL. Uses FROTTO_TEST_MYSQL_JDBC_URL (see RecurringBillingPersistenceTest) in its
 * own throwaway schema, otherwise Testcontainers; skipped when neither is available.
 */
class PendencyOwnershipPersistenceTest {

    /** Etapa 2.2 changeset (pendency.debtor_driver_id + backfill), applied after the legacy fixture. */
    private static final String DEBTOR_CHANGESET = "20261005000000";
    /** Etapa 2.2.1 changeset (driver_car.suspended + primary_driver_car_id), also applied after the legacy fixture. */
    private static final String RESERVE_CHANGESET = "20261006000000";
    private static final String EXTERNAL_JDBC_URL = System.getenv("FROTTO_TEST_MYSQL_JDBC_URL");
    private static final boolean USE_EXTERNAL_DATABASE = EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();
    private static MySQLContainer<?> mysql;
    private static String adminUrl;
    private static String jdbcUrlValue;
    private static String schema;
    private static String user;
    private static String password;
    private static EntityManagerFactory factory;
    private EntityManager em;
    private PendencyRepository pendencies;
    private DebtItemTypeRepository debtItemTypes;
    private DriverRepository drivers;
    private DriverCarRepository driverCars;
    private CarRepository cars;

    @BeforeAll
    static void schemaAndJpa() throws Exception {
        String jdbcUrl;
        if (USE_EXTERNAL_DATABASE) {
            user = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_USER", "root");
            password = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_PASSWORD", "");
            // A schema of its own, so this test never touches the database the other persistence tests expect empty.
            schema = "frotto_pendency_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
            // Upgrade path: the schema as it is before Etapa 2.2, legacy rows, then the new changeset (backfill).
            Liquibase previous = new Liquibase(
                "config/liquibase/master.xml",
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
            );
            previous.getDatabaseChangeLog().getChangeSets().removeIf(change -> change.getId().startsWith(DEBTOR_CHANGESET) || change.getId().startsWith(RESERVE_CHANGESET));
            previous.update(new Contexts("test"));
            assertThat(columnExists(connection, "pendency", "debtor_driver_id")).isFalse();
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO jhi_user (id,login,password_hash,activated,created_by,created_date) VALUES " +
                    "(91001,'owner-a',REPEAT('x',60),true,'test',NOW(6)),(91002,'owner-b',REPEAT('x',60),true,'test',NOW(6))"
                );
                statement.executeUpdate(
                    "INSERT INTO car (id,user_id,plate,active) VALUES (91001,91001,'AAA1A11',true),(91002,91002,'BBB2B22',true)," +
                    "(91003,91001,'CCC3C33',true),(91004,91001,'DDD4D44',true),(91005,91001,'EEE5E55',true)"
                );
                statement.executeUpdate(
                    "INSERT INTO driver (id,name,cpf) VALUES (91001,'Motorista A','11111111111'),(91002,'Motorista B','22222222222')," +
                    "(91003,'Motorista C','33333333333'),(91004,'Motorista A duplicado','11111111111'),(91005,'Motorista E','55555555555')"
                );
                // Car 91001 was driven by A (contract 91003, concluded) and is driven by C now (contract 91001);
                // 91004 is a legacy duplicate of A's CPF in the same account (contract 91004, concluded).
                statement.executeUpdate(
                    "INSERT INTO driver_car (id,car_id,driver_id,concluded,start_date) VALUES (91001,91001,91003,false,'2026-07-01')," +
                    "(91002,91002,91002,false,'2026-01-01'),(91003,91001,91001,true,'2026-01-01'),(91004,91001,91004,true,'2025-01-01')," +
                    "(91005,91001,NULL,true,'2024-01-01')," +
                    // Legacy row with concluded = NULL: E drives car 91005.
                    "(91006,91005,91005,NULL,'2025-06-01')"
                );
                // Legacy ambiguity on purpose: 91005 has no contract, 91006 points to a contract that no longer exists
                // (pendency.driver_car_id never had a database FK), 91007 was recorded on a contract without driver.
                statement.executeUpdate(
                    "INSERT INTO pendency (id,name,cost,date,status,paid_amount,remaining_amount,driver_car_id) VALUES " +
                    "(91001,'Dano',100.00,'2026-09-01','OPEN',0,100.00,91003)," +
                    "(91002,'Multa',50.00,'2026-09-02','OPEN',0,50.00,91003)," +
                    "(91003,'Dano de outra conta',70.00,'2026-09-03','OPEN',0,70.00,91002)," +
                    "(91004,'Combustível do motorista atual',40.00,'2026-09-04','OPEN',0,40.00,91001)," +
                    "(91005,'Sem contrato',10.00,'2024-02-01','OPEN',0,10.00,NULL)," +
                    "(91006,'Contrato apagado',20.00,'2024-02-02','OPEN',0,20.00,99999)," +
                    "(91007,'Contrato sem motorista',30.00,'2024-02-03','OPEN',0,30.00,91005)"
                );
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            new Liquibase(
                "config/liquibase/master.xml",
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
            )
                .update(new Contexts("test"));
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }

        jdbcUrlValue = jdbcUrl;
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(new DriverManagerDataSource(jdbcUrl, user, password));
        bean.setPackagesToScan("com.localuz.domain");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.jdbc.time_zone", "UTC");
        // Same naming strategies as application.yml: driverCar -> driver_car_id.
        properties.setProperty("hibernate.physical_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy");
        properties.setProperty("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        bean.setJpaProperties(properties);
        bean.afterPropertiesSet();
        factory = bean.getObject();
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

    @BeforeEach
    void begin() {
        em = factory.createEntityManager();
        em.getTransaction().begin();
        JpaRepositoryFactory repositories = repositoriesOf(em);
        pendencies = repositories.getRepository(PendencyRepository.class);
        debtItemTypes = repositories.getRepository(DebtItemTypeRepository.class);
        drivers = repositories.getRepository(DriverRepository.class);
        driverCars = repositories.getRepository(DriverCarRepository.class);
        cars = repositories.getRepository(CarRepository.class);
    }

    private static JpaRepositoryFactory repositoriesOf(EntityManager entityManager) {
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(entityManager);
        repositories.setEvaluationContextProvider(
            new ExtensionAwareQueryMethodEvaluationContextProvider(List.of(new SecurityEvaluationContextExtension()))
        );
        return repositories;
    }

    private static String jdbcUrlOf() {
        return jdbcUrlValue;
    }

    private static int countRows(Connection connection, String sql) throws java.sql.SQLException {
        try (java.sql.ResultSet rows = connection.createStatement().executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws java.sql.SQLException {
        try (java.sql.ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    @AfterEach
    void rollback() {
        SecurityContextHolder.clearContext();
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }

    @Test
    void onlyTheCurrentUsersPendenciesAreReturnedWithTheirContract() {
        loginAs("owner-a");

        List<Pendency> found = pendencies.findByCurrentUserAndIdIn(List.of(91001L, 91002L, 91003L, 99999L));

        assertThat(found.stream().map(Pendency::getId).collect(Collectors.toList())).containsExactlyInAnyOrder(91001L, 91002L);
        assertThat(found).allSatisfy(pendency -> {
            assertThat(pendency.getDriverCarId()).isEqualTo(91003L);
            assertThat(pendency.getDriverCar().getCar().getPlate()).isEqualTo("AAA1A11");
            assertThat(pendency.getDriverCar().getDriver().getName()).isEqualTo("Motorista A");
        });
    }

    @Test
    void sameCarDifferentDriversNeverShareDebtsInAConfession() {
        loginAs("owner-a");
        DebtConfessionService confessions = new DebtConfessionService(pendencies, debtItemTypes);

        // Car 91001: A's pendencies (contract 91003, concluded) and C's (contract 91001, current driver).
        assertThatThrownBy(() -> confessions.loadEligible(List.of(91001L, 91004L)))
            .isInstanceOfSatisfying(BadRequestAlertException.class, failure -> assertThat(failure.getErrorKey()).isEqualTo("pendenciesdifferentdebtors"));

        DebtConfessionPreviewDTO historical = confessions.preview(List.of(91001L, 91002L));
        assertThat(historical.getDriverCarId()).isEqualTo(91003L);
        assertThat(historical.getDriverName()).isEqualTo("Motorista A");
        assertThat(historical.getCarPlate()).isEqualTo("AAA1A11");
        assertThat(historical.getValorTotal()).isEqualByComparingTo("150.00");

        DebtConfessionPreviewDTO current = confessions.preview(List.of(91004L));
        assertThat(current.getDriverCarId()).isEqualTo(91001L);
        assertThat(current.getDriverName()).isEqualTo("Motorista C");
    }

    @Test
    void driverCpfLookupIsScopedToTheAccountAndToleratesDuplicates() {
        DriverRepository drivers = new JpaRepositoryFactory(em) {
            {
                setEvaluationContextProvider(
                    new ExtensionAwareQueryMethodEvaluationContextProvider(List.of(new SecurityEvaluationContextExtension()))
                );
            }
        }
            .getRepository(DriverRepository.class);

        loginAs("owner-a");
        assertThat(drivers.findAllByCurrentUserAndCpf("11111111111")).extracting(Driver::getId).containsExactlyInAnyOrder(91001L, 91004L);
        assertThat(drivers.findAllByCurrentUserAndCpf("22222222222")).isEmpty();

        loginAs("owner-b");
        assertThat(drivers.findAllByCurrentUserAndCpf("11111111111")).isEmpty();
        assertThat(drivers.findAllByCurrentUserAndCpf("22222222222")).extracting(Driver::getId).containsExactly(91002L);
    }

    @Test
    void backfillOnlyFollowsThePendencysOwnContractAndNeverGuesses() throws Exception {
        Map<Long, Long> debtors = new java.util.HashMap<>();
        try (
            Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password);
            java.sql.ResultSet rows = connection.createStatement().executeQuery("SELECT id, debtor_driver_id FROM pendency WHERE id BETWEEN 91001 AND 91007")
        ) {
            while (rows.next()) {
                debtors.put(rows.getLong(1), (Long) rows.getObject(2, Long.class));
            }
        }
        assertThat(debtors)
            .containsEntry(91001L, 91001L)
            .containsEntry(91002L, 91001L)
            .containsEntry(91003L, 91002L)
            .containsEntry(91004L, 91003L)
            .containsEntry(91005L, null)
            .containsEntry(91006L, null)
            .containsEntry(91007L, null);
    }

    @Test
    void finalSchemaHasTheDebtorColumnIndexAndForeignKeyAndKeepsTheOrigin() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password)) {
            assertThat(columnExists(connection, "pendency", "debtor_driver_id")).isTrue();
            assertThat(columnExists(connection, "pendency", "driver_car_id")).isTrue();
            try (
                java.sql.ResultSet fk = connection
                    .createStatement()
                    .executeQuery(
                        "SELECT REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'pendency' AND COLUMN_NAME = 'debtor_driver_id' AND CONSTRAINT_NAME = 'fk_pendency__debtor_driver_id'"
                    )
            ) {
                assertThat(fk.next()).isTrue();
                assertThat(fk.getString(1)).isEqualTo("driver");
            }
            try (
                java.sql.ResultSet index = connection
                    .createStatement()
                    .executeQuery(
                        "SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'pendency' AND INDEX_NAME = 'idx_pendency_debtor_driver'"
                    )
            ) {
                assertThat(index.next()).isTrue();
                assertThat(index.getString(1)).isEqualTo("debtor_driver_id");
            }
            try (java.sql.ResultSet nullable = connection.getMetaData().getColumns(connection.getCatalog(), null, "pendency", "debtor_driver_id")) {
                assertThat(nullable.next()).isTrue();
                assertThat(nullable.getInt("NULLABLE")).isEqualTo(java.sql.DatabaseMetaData.columnNullable);
            }
            // A second run applies nothing more.
            int changeSets = countRows(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG");
            new Liquibase(
                "config/liquibase/master.xml",
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
            )
                .update(new Contexts("test"));
            assertThat(countRows(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG")).isEqualTo(changeSets);
        }
    }

    @Test
    void debtorQueriesFollowTheDebtorNeverTheCurrentDriverOfTheCar() {
        // A5/A6: car 91001 is driven by C now; A's debts were born there in contract 91003.
        loginAs("owner-a");

        assertThat(pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(91001L)).extracting(Pendency::getId).containsExactly(91002L, 91001L);
        assertThat(pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(91003L)).extracting(Pendency::getId).containsExactly(91004L);
        assertThat(pendencies.findOpenByCurrentUserAndDriverIdOrderByDateDesc(91001L)).hasSize(2);
        assertThat(pendencies.findOutstandingTotalByCurrentUserAndDriverId(91001L)).isEqualByComparingTo("150.00");
        assertThat(pendencies.countOpenByCurrentUserAndDriverId(91003L)).isEqualTo(1L);
        // The origin view (per contract) is unchanged.
        assertThat(pendencies.findByCurrentUserAndDriverCarIdOrderByDateDesc(91003L)).extracting(Pendency::getId).containsExactly(91002L, 91001L);
        // Rows without debtor are never attributed to anybody.
        assertThat(pendencies.findByCurrentUserOrderByDateDesc()).extracting(Pendency::getId).contains(91007L);
        loginAs("owner-b");
        assertThat(pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(91001L)).isEmpty();
    }

    @Test
    void transferConcludesTheOldContractAndKeepsTheDebtWithItsDebtorAndOrigin() {
        // B + A2/A3: C moves from car 91001 to car 91003 (same account).
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        Car hb20 = cars.findById(91003L).orElseThrow();
        Driver c = drivers.findById(91003L).orElseThrow();

        assignments.lockForAssignment(hb20, c.getId());
        assignments.prepareAssignment(c.getId(), hb20, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 10, 1));
        DriverCar transferred = new DriverCar();
        transferred.setCar(hb20);
        transferred.setDriver(c);
        transferred.setStartDate(LocalDate.of(2026, 10, 1));
        transferred.setConcluded(false);
        driverCars.saveAndFlush(transferred);
        em.clear();

        assertThat(driverCars.findOpenByCurrentUserAndDriver(91003L)).extracting(DriverCar::getId).containsExactly(transferred.getId());
        DriverCar old = driverCars.findById(91001L).orElseThrow();
        assertThat(old.getConcluded()).isTrue();
        assertThat(old.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        Pendency debt = pendencies.findById(91004L).orElseThrow();
        assertThat(debt.getDriverCarId()).isEqualTo(91001L);
        assertThat(debt.getDebtorDriverId()).isEqualTo(91003L);
        assertThat(debt.getRemainingAmount()).isEqualByComparingTo("40.00");
        assertThat(pendencies.findByCurrentUserAndDriverIdOrderByDateDesc(91003L)).extracting(Pendency::getId).containsExactly(91004L);
    }

    @Test
    void aFailedTransferLeavesTheOldContractActive() {
        // B11: the conclusion is part of the caller's transaction; a failure before commit undoes it.
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        assignments.prepareAssignment(91003L, cars.findById(91003L).orElseThrow(), DriverAssignmentType.PERMANENT, LocalDate.of(2026, 10, 1));
        em.flush();
        em.getTransaction().rollback();

        EntityManager other = factory.createEntityManager();
        try {
            assertThat(other.find(DriverCar.class, 91001L).getConcluded()).isFalse();
        } finally {
            other.close();
        }
    }

    @Test
    void concurrentAssignmentsOfTheSameDriverAreSerialized() {
        // The first transaction holds the driver's row lock; a second one cannot take it until the first ends.
        drivers.findByIdForUpdate(91003L);

        EntityManager second = factory.createEntityManager();
        try {
            second.getTransaction().begin();
            second.createNativeQuery("SET SESSION innodb_lock_wait_timeout = 1").executeUpdate();
            DriverRepository secondDrivers = repositoriesOf(second).getRepository(DriverRepository.class);
            assertThatThrownBy(() -> secondDrivers.findByIdForUpdate(91003L)).isInstanceOf(javax.persistence.PersistenceException.class);
        } finally {
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            second.close();
        }
    }

    // ---- Etapa 2.2.1: primary contract, reserve car, definitive transfer (MySQL, real migrations) ----

    @Test
    void reserveMigrationKeepsEveryExistingContractANormalPrimary() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password)) {
            assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE id BETWEEN 91001 AND 91006")).isEqualTo(6);
            assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE id BETWEEN 91001 AND 91006 AND suspended = false AND primary_driver_car_id IS NULL"))
                .isEqualTo(6);
            try (java.sql.ResultSet column = connection.getMetaData().getColumns(connection.getCatalog(), null, "driver_car", "suspended")) {
                assertThat(column.next()).isTrue();
                assertThat(column.getInt("NULLABLE")).isEqualTo(java.sql.DatabaseMetaData.columnNoNulls);
            }
            assertThat(
                countRows(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'driver_car' " +
                    "AND COLUMN_NAME = 'primary_driver_car_id' AND REFERENCED_TABLE_NAME = 'driver_car' AND CONSTRAINT_NAME = 'fk_driver_car__primary_driver_car_id'"
                )
            )
                .isEqualTo(1);
            assertThat(
                countRows(
                    connection,
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'driver_car' AND INDEX_NAME = 'idx_driver_car_primary'"
                )
            )
                .isEqualTo(1);
        }
    }

    @Test
    void reserveAndReturnKeepTheSamePrimaryRow() {
        // 5/6: C (driver 91003) drives car 91001 in contract 91001; reserve car 91003, then return. Two rows, not three.
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        DriverCar reserve = startReserve(assignments, 91003L, 91003L);

        assertThat(driverCars.findById(91001L).orElseThrow().getStatus()).isEqualTo("SUSPENDED");
        assignments.returnReserve(reserve, LocalDate.of(2026, 10, 5));
        em.flush();
        em.clear();

        List<DriverCar> history = contractsOfDriver(91003L);
        assertThat(history).hasSize(2);
        DriverCar primary = driverCars.findById(91001L).orElseThrow();
        assertThat(primary.getStatus()).isEqualTo("ACTIVE");
        assertThat(primary.getEndDate()).isNull();
        DriverCar returned = driverCars.findById(reserve.getId()).orElseThrow();
        assertThat(returned.getStatus()).isEqualTo("CONCLUDED");
        assertThat(returned.getPrimaryDriverCarId()).isEqualTo(91001L);
        assertThat(pendencies.findById(91004L).orElseThrow().getDebtorDriverId()).isEqualTo(91003L);
        assertThat(pendencies.findById(91004L).orElseThrow().getDriverCarId()).isEqualTo(91001L);
    }

    @Test
    void reserveSwappedForAnotherReserveStillReturnsToTheSamePrimary() {
        // 7: car 91001 (primary) -> 91003 reserve -> 91004 reserve -> back to 91001. Three rows, one primary.
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        DriverCar first = startReserve(assignments, 91003L, 91003L);
        DriverCar second = startReserve(assignments, 91003L, 91004L);
        assignments.returnReserve(second, LocalDate.of(2026, 10, 8));
        em.flush();
        em.clear();

        List<DriverCar> history = contractsOfDriver(91003L);
        assertThat(history).hasSize(3);
        assertThat(history.stream().filter(dc -> !dc.isReserve())).extracting(DriverCar::getId).containsExactly(91001L);
        assertThat(history.stream().filter(dc -> "ACTIVE".equals(dc.getStatus()))).extracting(DriverCar::getId).containsExactly(91001L);
        assertThat(driverCars.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("CONCLUDED");
        assertThat(driverCars.findById(second.getId()).orElseThrow().getPrimaryDriverCarId()).isEqualTo(91001L);
        assertThat(driverCars.findById(first.getId()).orElseThrow().getPrimaryDriverCarId()).isEqualTo(91001L);
    }

    @Test
    void aFailedReserveLeavesNoPrimarySuspendedAlone() {
        // 16: the suspension belongs to the caller's transaction.
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        assignments.lockForAssignment(91003L, 91003L);
        assignments.prepareAssignment(91003L, cars.findById(91003L).orElseThrow(), DriverAssignmentType.RESERVE, LocalDate.of(2026, 10, 1));
        em.flush();
        em.getTransaction().rollback();

        EntityManager other = factory.createEntityManager();
        try {
            DriverCar primary = other.find(DriverCar.class, 91001L);
            assertThat(primary.getSuspended()).isFalse();
            assertThat(primary.getStatus()).isEqualTo("ACTIVE");
        } finally {
            other.close();
        }
    }

    @Test
    void aFailedReturnNeverLeavesTwoActiveContracts() throws Exception {
        // 17: a committed reserve state, then a return that fails before commit: still exactly one operational contract.
        Long reserveId = null;
        EntityManager setup = factory.createEntityManager();
        try {
            setup.getTransaction().begin();
            loginAs("owner-a");
            JpaRepositoryFactory repositories = repositoriesOf(setup);
            DriverCarRepository setupContracts = repositories.getRepository(DriverCarRepository.class);
            DriverAssignmentService assignments = new DriverAssignmentService(
                setupContracts,
                repositories.getRepository(DriverRepository.class),
                repositories.getRepository(CarRepository.class),
                repositories.getRepository(PendencyRepository.class)
            );
            reserveId = startReserve(assignments, setupContracts, repositories.getRepository(CarRepository.class), repositories.getRepository(DriverRepository.class), 91003L, 91003L).getId();
            setup.getTransaction().commit();

            EntityManager failing = factory.createEntityManager();
            try {
                failing.getTransaction().begin();
                JpaRepositoryFactory failingRepositories = repositoriesOf(failing);
                DriverCarRepository failingContracts = failingRepositories.getRepository(DriverCarRepository.class);
                DriverAssignmentService failingAssignments = new DriverAssignmentService(
                    failingContracts,
                    failingRepositories.getRepository(DriverRepository.class),
                    failingRepositories.getRepository(CarRepository.class),
                    failingRepositories.getRepository(PendencyRepository.class)
                );
                failingAssignments.returnReserve(failingContracts.findById(reserveId).orElseThrow(), LocalDate.of(2026, 10, 5));
                failing.flush();
                failing.getTransaction().rollback();
            } finally {
                failing.close();
            }

            try (Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password)) {
                assertThat(
                    countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE driver_id = 91003 AND concluded = false AND suspended = false")
                )
                    .isEqualTo(1);
                assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE id = 91001 AND suspended = true")).isEqualTo(1);
                assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE id = " + reserveId + " AND concluded = false")).isEqualTo(1);
            }
        } finally {
            if (setup.getTransaction().isActive()) {
                setup.getTransaction().rollback();
            }
            setup.close();
            // Undo the committed setup so the shared fixture stays as it was.
            try (Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password); Statement statement = connection.createStatement()) {
                if (reserveId != null) {
                    statement.executeUpdate("DELETE FROM driver_car WHERE id = " + reserveId);
                }
                statement.executeUpdate("UPDATE driver_car SET suspended = false WHERE id = 91001");
            }
        }
    }

    @Test
    void concurrentAssignmentsOfTheSameCarOrTheSameDriverAreSerialized() {
        // 18: the first transaction holds car 91003 and driver 91003; any second assignment touching either waits.
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        assignments.lockForAssignment(91003L, 91003L);

        assertSecondLockTimesOut(91003L, 91001L);
        assertSecondLockTimesOut(91004L, 91003L);
    }

    private void assertSecondLockTimesOut(Long carId, Long driverId) {
        EntityManager second = factory.createEntityManager();
        try {
            second.getTransaction().begin();
            second.createNativeQuery("SET SESSION innodb_lock_wait_timeout = 1").executeUpdate();
            JpaRepositoryFactory repositories = repositoriesOf(second);
            DriverAssignmentService secondAssignments = new DriverAssignmentService(
                repositories.getRepository(DriverCarRepository.class),
                repositories.getRepository(DriverRepository.class),
                repositories.getRepository(CarRepository.class),
                repositories.getRepository(PendencyRepository.class)
            );
            assertThatThrownBy(() -> secondAssignments.lockForAssignment(carId, driverId)).isInstanceOf(javax.persistence.PersistenceException.class);
        } finally {
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            second.close();
        }
    }

    private DriverCar startReserve(DriverAssignmentService assignments, Long driverId, Long reserveCarId) {
        return startReserve(assignments, driverCars, cars, drivers, driverId, reserveCarId);
    }

    /** Same steps as POST /driver-cars/car/{id}?assignment=RESERVE. */
    private static DriverCar startReserve(
        DriverAssignmentService assignments,
        DriverCarRepository contracts,
        CarRepository carRepository,
        DriverRepository driverRepository,
        Long driverId,
        Long reserveCarId
    ) {
        Car reserveCar = carRepository.findById(reserveCarId).orElseThrow();
        assignments.lockForAssignment(reserveCar, driverId);
        assertThat(assignments.isCarOccupied(reserveCarId, null)).isFalse();
        DriverCar primary = assignments.prepareAssignment(driverId, reserveCar, DriverAssignmentType.RESERVE, LocalDate.of(2026, 10, 1));
        DriverCar reserve = new DriverCar();
        reserve.setCar(reserveCar);
        reserve.setDriver(driverRepository.findById(driverId).orElseThrow());
        reserve.setStartDate(LocalDate.of(2026, 10, 1));
        reserve.setConcluded(false);
        reserve.setSuspended(false);
        reserve.setPrimaryDriverCar(primary);
        return contracts.saveAndFlush(reserve);
    }

    private List<DriverCar> contractsOfDriver(Long driverId) {
        return em
            .createQuery("select dc from DriverCar dc where dc.driver.id = :driverId order by dc.id", DriverCar.class)
            .setParameter("driverId", driverId)
            .getResultList();
    }

    // ---- concluded = NULL (legacy): "not concluded", the same for the car side and the driver side ----

    @Test
    void aLegacyNullContractIsOpenForTheDriverAndOccupiesTheCar() throws Exception {
        // 4/5: E (91005) drives car 91005 in contract 91006 with concluded = NULL.
        try (Connection connection = DriverManager.getConnection(jdbcUrlOf(), user, password)) {
            assertThat(countRows(connection, "SELECT COUNT(*) FROM driver_car WHERE id = 91006 AND concluded IS NULL")).isEqualTo(1);
        }
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);

        assertThat(assignments.isCarOccupied(91005L, null)).isTrue();
        assertThat(driverCars.findOperationalOnCar(91005L)).extracting(DriverCar::getId).containsExactly(91006L);
        assertThat(driverCars.findOpenByCurrentUserAndDriver(91005L)).extracting(DriverCar::getId).containsExactly(91006L);
        assertThat(driverCars.countOperationalByCurrentUserAndDriver(91005L, -1L)).isEqualTo(1L);
        assertThat(driverCars.findById(91006L).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(cars.findActiveByCurrentUserAndDriver())
            .filteredOn(row -> row.getCar().getId().equals(91005L))
            .extracting(com.localuz.DTO.CarDriverDto::getDriverName)
            .containsExactly("Motorista E");
    }

    @Test
    void aDriverWithALegacyNullContractNeedsAnExplicitChoice() {
        // 6
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        Car target = cars.findById(91004L).orElseThrow();
        assignments.lockForAssignment(target, 91005L);

        assertThatThrownBy(() -> assignments.prepareAssignment(91005L, target, null, LocalDate.of(2026, 10, 1)))
            .isInstanceOfSatisfying(
                com.localuz.web.rest.errors.DriverAssignmentConflictException.class,
                failure -> assertThat(failure.getParameters()).containsEntry("conflictingDriverCarId", 91006L).containsEntry("conflictingCarPlate", "EEE5E55")
            );
    }

    @Test
    void permanentTransferFromALegacyNullContract() {
        // 7
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        Car target = cars.findById(91004L).orElseThrow();
        assignments.lockForAssignment(target, 91005L);
        assignments.prepareAssignment(91005L, target, DriverAssignmentType.PERMANENT, LocalDate.of(2026, 10, 1));
        em.flush();
        em.clear();

        DriverCar legacy = driverCars.findById(91006L).orElseThrow();
        assertThat(legacy.getConcluded()).isTrue();
        assertThat(legacy.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(assignments.isCarOccupied(91005L, null)).isFalse();
    }

    @Test
    void reserveFromALegacyNullPrimaryThenReturnRestoresTheSameRow() {
        // 8 + 9
        loginAs("owner-a");
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        DriverCar reserve = startReserve(assignments, 91005L, 91004L);
        em.flush();
        em.clear();

        DriverCar suspended = driverCars.findById(91006L).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo("SUSPENDED");
        assertThat(suspended.getConcluded()).isFalse();
        assertThat(assignments.isCarOccupied(91005L, null)).isFalse();

        assignments.returnReserve(driverCars.findById(reserve.getId()).orElseThrow(), LocalDate.of(2026, 10, 5));
        em.flush();
        em.clear();

        assertThat(driverCars.findById(91006L).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        assertThat(contractsOfDriver(91005L)).hasSize(2);
        assertThat(assignments.isCarOccupied(91005L, null)).isTrue();
    }

    @Test
    void concurrentAssignmentOfACarHeldByALegacyNullContractIsSerialized() {
        // 10: whoever takes car 91005 (occupied by the NULL contract) does it under the car lock.
        DriverAssignmentService assignments = new DriverAssignmentService(driverCars, drivers, cars, pendencies);
        assignments.lockForAssignment(91005L, 91005L);

        assertSecondLockTimesOut(91005L, 91001L);
        assertSecondLockTimesOut(91003L, 91005L);
    }

    @Test
    void anotherAccountCannotReachThePendencies() {
        loginAs("owner-b");

        assertThat(pendencies.findByCurrentUserAndIdIn(List.of(91001L, 91002L))).isEmpty();
        assertThat(pendencies.findByCurrentUserAndIdIn(List.of(91003L))).extracting(Pendency::getId).containsExactly(91003L);
    }

    private static void loginAs(String login) {
        User principal = new User(login, "x", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
