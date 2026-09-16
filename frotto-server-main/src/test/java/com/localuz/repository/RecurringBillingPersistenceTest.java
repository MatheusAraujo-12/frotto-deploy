package com.localuz.repository;

import static org.assertj.core.api.Assertions.*;

import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.MySQLContainer;

/** Real MySQL + production Liquibase migrations + JPA repositories. No application/provider startup. */
class RecurringBillingPersistenceTest {
    private static final String PROVIDER = "MERCADO_PAGO";
    private static final Instant SEPTEMBER = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant OCTOBER = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant NOVEMBER = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant DECEMBER = Instant.parse("2026-12-01T12:00:00Z");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
        .withStartupTimeout(Duration.ofMinutes(4)).withConnectTimeoutSeconds(180);
    /**
     * 5G.8: Testcontainers' NpipeSocketClientProviderStrategy (docker-java 3.2.13, pinned by the
     * Spring Boot 2.7.18 BOM) cannot bootstrap against this machine's Docker Desktop - see
     * docs/billing-final-validation-5g8.md. As a disposable, 100%-local fallback (no production
     * code changed), FROTTO_TEST_MYSQL_JDBC_URL/_USER/_PASSWORD point this test at a MySQL
     * container started by hand with `docker run` instead of through Testcontainers. Unset (the
     * default), this test is unchanged: Testcontainers still owns the container lifecycle.
     */
    private static final String EXTERNAL_JDBC_URL = System.getenv("FROTTO_TEST_MYSQL_JDBC_URL");
    private static final boolean USE_EXTERNAL_DATABASE = EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();
    private static String JDBC_URL;
    private static String JDBC_USER;
    private static String JDBC_PASSWORD;
    private static EntityManagerFactory factory;
    private EntityManager em;
    private BillingInvoiceRepository invoices;
    private PaymentAttemptRepository attempts;
    private Subscription subscription;

    @BeforeAll
    static void schemaAndJpa() throws Exception {
        if (USE_EXTERNAL_DATABASE) {
            JDBC_URL = EXTERNAL_JDBC_URL;
            JDBC_USER = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_USER", "root");
            JDBC_PASSWORD = System.getenv().getOrDefault("FROTTO_TEST_MYSQL_PASSWORD", "");
        } else {
            MYSQL.start();
            JDBC_URL = MYSQL.getJdbcUrl();
            JDBC_USER = MYSQL.getUsername();
            JDBC_PASSWORD = MYSQL.getPassword();
        }
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            Liquibase liquibase = new Liquibase("config/liquibase/master.xml", new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            // Install the preceding schema first, then keep a real legacy subscription across the new migration.
            liquibase.getDatabaseChangeLog().getChangeSets().removeIf(change -> (change.getId().startsWith("20260911000000-") || change.getId().startsWith("20260914000000-") || change.getId().startsWith("20260915010000-")));
            liquibase.update(new Contexts("test"));
            try (java.sql.ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "billing_invoice", null)) {
                assertThat(tables.next()).isFalse();
            }
            connection.createStatement().executeUpdate("INSERT INTO jhi_user (id,login,password_hash,activated,created_by,created_date) " +
                "VALUES (90001,'billing-model-test',REPEAT('x',60),true,'test',NOW(6))");
            connection.createStatement().executeUpdate("INSERT INTO subscription " +
                "(id,jhi_user_id,plan_id,billing_cycle,status,start_date,cancel_at_period_end,contracted_price," +
                "contracted_vehicle_count,source,created_at,updated_at) VALUES " +
                "(90001,90001,(SELECT id FROM plan WHERE code='BRONZE'),'MONTHLY','ACTIVE'," +
                "'2026-09-01 12:00:00',false,100.00,1,'PAYMENT_PROVIDER',NOW(6),NOW(6))");
            connection.commit();
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "subscription", "last_financial_reconciliation_at")) {
                assertThat(columns.next()).isFalse();
            }
            liquibase = new Liquibase("config/liquibase/master.xml", new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            liquibase.update(new Contexts("test"));
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "subscription", "last_financial_reconciliation_at")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt("NULLABLE")).isEqualTo(java.sql.DatabaseMetaData.columnNullable);
                assertThat(columns.getString("COLUMN_DEF")).isNull();
            }
            try (var columns = connection.createStatement().executeQuery("SELECT DATA_TYPE,DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='subscription' AND COLUMN_NAME='last_financial_reconciliation_at'")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getString(1)).isEqualTo("datetime");
                assertThat(columns.getInt(2)).isEqualTo(6);
            }
            try (var rows = connection.createStatement().executeQuery("SELECT last_financial_reconciliation_at FROM subscription WHERE id=90001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getTimestamp(1)).isNull();
            }
            try (var rows = connection.createStatement().executeQuery("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='subscription' AND INDEX_NAME='idx_subscription_financial_reconciliation' ORDER BY SEQ_IN_INDEX")) {
                java.util.List<String> columns = new java.util.ArrayList<>();
                while (rows.next()) columns.add(rows.getString(1));
                assertThat(columns).containsExactly("source", "external_provider", "last_financial_reconciliation_at");
            }
            try (java.sql.ResultSet rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM billing_invoice")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(new DriverManagerDataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD));
        bean.setPackagesToScan("com.localuz.domain");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.jdbc.time_zone", "UTC");
        bean.setJpaProperties(properties);
        bean.afterPropertiesSet();
        factory = bean.getObject();
    }

    @AfterAll
    static void shutdown() {
        if (factory != null) factory.close();
        if (!USE_EXTERNAL_DATABASE) MYSQL.stop();
    }

    @BeforeEach
    void begin() {
        em = factory.createEntityManager();
        em.getTransaction().begin();
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(em);
        invoices = repositories.getRepository(BillingInvoiceRepository.class);
        attempts = repositories.getRepository(PaymentAttemptRepository.class);
        subscription = em.find(Subscription.class, 90001L);
        assertThat(subscription).as("legacy subscription survives the additive migration").isNotNull();
    }

    @AfterEach
    void rollback() {
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
        em.close();
    }

    @Test
    void independentMonthlyInvoicesAndRetriesPreserveExistingSubscription() {
        BillingInvoice september = invoice(SEPTEMBER, OCTOBER, "sep");
        september.setStatus(BillingInvoiceStatus.PAID);
        september.setPaidAt(SEPTEMBER.plusSeconds(5));
        BillingInvoice october = invoice(OCTOBER, NOVEMBER, "oct");
        october.setStatus(BillingInvoiceStatus.PAID);
        BillingInvoice november = invoice(NOVEMBER, DECEMBER, "nov");
        november.setStatus(BillingInvoiceStatus.PAST_DUE);
        attempt(september, "sep-1", PaymentAttemptStatus.APPROVED);
        attempt(october, "oct-1", PaymentAttemptStatus.REJECTED);
        attempt(october, "oct-2", PaymentAttemptStatus.REJECTED);
        attempt(october, "oct-3", PaymentAttemptStatus.APPROVED);
        attempt(november, "nov-1", PaymentAttemptStatus.REJECTED);
        em.flush();
        em.clear();
        assertThat(invoices.findBySubscriptionIdOrderByPeriodStartAsc(subscription.getId()))
            .extracting(BillingInvoice::getStatus)
            .containsExactly(BillingInvoiceStatus.PAID, BillingInvoiceStatus.PAID, BillingInvoiceStatus.PAST_DUE);
        assertThat(attempts.findByBillingInvoiceIdOrderByIdAsc(october.getId()))
            .extracting(PaymentAttempt::getStatus)
            .containsExactly(PaymentAttemptStatus.REJECTED, PaymentAttemptStatus.REJECTED, PaymentAttemptStatus.APPROVED);
        assertThat(em.find(Subscription.class, subscription.getId()).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void approvedAttemptDoesNotAutomaticallySettleEvenItsOwnInvoice() {
        BillingInvoice first = invoice(SEPTEMBER, OCTOBER, "first");
        BillingInvoice second = invoice(OCTOBER, NOVEMBER, "second");
        attempt(first, "approved", PaymentAttemptStatus.APPROVED);
        em.flush();
        em.clear();
        assertThat(em.find(BillingInvoice.class, first.getId()).getStatus()).isEqualTo(BillingInvoiceStatus.PENDING);
        assertThat(em.find(BillingInvoice.class, second.getId()).getStatus()).isEqualTo(BillingInvoiceStatus.PENDING);
        assertThat(em.find(BillingInvoice.class, first.getId()).getPaidAt()).isNull();
    }

    @Test
    void financialCoverageReadsPersistedCompetenciesWithoutWritingSubscription() {
        BillingInvoice october = invoice(OCTOBER, NOVEMBER, "coverage-oct");
        october.setStatus(BillingInvoiceStatus.PAID);
        attempt(october, "coverage-approved", PaymentAttemptStatus.APPROVED).setApprovedAt(OCTOBER);
        BillingInvoice november = invoice(NOVEMBER, DECEMBER, "coverage-nov");
        attempt(november, "coverage-rejected", PaymentAttemptStatus.REJECTED);
        em.flush();
        em.clear();
        var coverage = new com.localuz.service.SubscriptionFinancialCoverageService(invoices, attempts,
            java.time.Clock.fixed(NOVEMBER.plusSeconds(3600), java.time.ZoneOffset.UTC));
        Subscription stored = em.find(Subscription.class, subscription.getId());
        Instant previousEnd = stored.getCurrentPeriodEnd();
        assertThat(coverage.evaluate(stored).covered()).isTrue();
        assertThat(coverage.evaluate(stored).reason())
            .isEqualTo(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.GRACE);
        assertThat(coverage.evaluate(stored, NOVEMBER.plus(Duration.ofHours(72))).covered()).isFalse();
        assertThat(coverage.evaluate(stored, NOVEMBER.plus(Duration.ofHours(72))).commercialState())
            .isEqualTo(com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState.PAST_DUE);
        // This new read query must be scoped to the subscription and must have no pessimistic lock annotation.
        assertThat(attempts.findByBillingInvoiceSubscriptionId(-1L)).isEmpty();
        em.flush();
        em.clear();
        assertThat(em.find(Subscription.class, stored.getId()).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(em.find(Subscription.class, stored.getId()).getCurrentPeriodEnd()).isEqualTo(previousEnd);
        assertThat(em.find(BillingInvoice.class, november.getId()).getStatus()).isEqualTo(BillingInvoiceStatus.PENDING);
    }

    @Test
    void approvedRenewalAfterGraceRestoresOnlyItsOriginalPersistedPeriod() {
        BillingInvoice october = invoice(OCTOBER, NOVEMBER, "recovery-oct");
        october.setStatus(BillingInvoiceStatus.PAID);
        attempt(october, "recovery-old", PaymentAttemptStatus.APPROVED);
        BillingInvoice november = invoice(NOVEMBER, DECEMBER, "recovery-nov");
        november.setStatus(BillingInvoiceStatus.PAID);
        attempt(november, "recovery-new", PaymentAttemptStatus.APPROVED);
        em.flush(); em.clear();
        var coverage = new com.localuz.service.SubscriptionFinancialCoverageService(invoices, attempts);
        var result = coverage.evaluate(subscription, NOVEMBER.plus(Duration.ofDays(4)));
        assertThat(result.covered()).isTrue();
        assertThat(result.coverageEnd()).isEqualTo(DECEMBER);
        assertThat(result.reason()).isEqualTo(com.localuz.service.dto.FinancialCoverageEvaluation.Reason.PAID);
        assertThat(coverage.evaluate(subscription, DECEMBER).covered()).isFalse();
    }

    @Test
    void rejectsDuplicateExternalInvoiceInSameProvider() {
        invoice(SEPTEMBER, OCTOBER, "duplicate");
        assertThatThrownBy(() -> invoice(OCTOBER, NOVEMBER, "duplicate")).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void rejectsDuplicatePaymentAcrossInvoicesInSameProvider() {
        BillingInvoice first = invoice(SEPTEMBER, OCTOBER, "first");
        BillingInvoice second = invoice(OCTOBER, NOVEMBER, "second");
        attempt(first, "duplicate", PaymentAttemptStatus.REJECTED);
        assertThatThrownBy(() -> attempt(second, "duplicate", PaymentAttemptStatus.APPROVED)).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void providerNamespacesAreIndependent() {
        BillingInvoice first = invoice(SEPTEMBER, OCTOBER, "same");
        BillingInvoice other = newInvoice(OCTOBER, NOVEMBER, "same");
        other.setProvider("OTHER_PROVIDER");
        invoices.saveAndFlush(other);
        attempt(first, "same-payment", PaymentAttemptStatus.PENDING);
        PaymentAttempt payment = newAttempt(other, "same-payment", PaymentAttemptStatus.PENDING);
        payment.setProvider("OTHER_PROVIDER");
        attempts.saveAndFlush(payment);
        assertThat(invoices.findByProviderAndExternalAuthorizedPaymentId("OTHER_PROVIDER", "same")).isPresent();
        assertThat(attempts.findByProviderAndExternalPaymentId("OTHER_PROVIDER", "same-payment")).isPresent();
    }

    @Test
    void invoicesWithoutProviderIdUseServicePeriodIdentity() {
        BillingInvoice first = invoice(SEPTEMBER, OCTOBER, null);
        invoice(OCTOBER, NOVEMBER, null);
        assertThat(invoices.findBySubscriptionIdOrderByPeriodStartAsc(subscription.getId())).hasSize(2);
        first.setExternalAuthorizedPaymentId("discovered");
        em.flush();
        em.clear();
        assertThat(invoices.findBySubscriptionIdAndPeriodStartAndPeriodEnd(subscription.getId(), SEPTEMBER, OCTOBER))
            .get().extracting(BillingInvoice::getExternalAuthorizedPaymentId).isEqualTo("discovered");
        assertThatThrownBy(() -> invoice(SEPTEMBER, OCTOBER, null)).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void stableAttemptIdentityAllowsLaterPaymentDiscovery() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, null);
        PaymentAttempt first = newAttempt(invoice, null, PaymentAttemptStatus.PENDING);
        first.setExternalAttemptId("retry-1");
        attempts.saveAndFlush(first);
        PaymentAttempt second = newAttempt(invoice, null, PaymentAttemptStatus.PENDING);
        second.setExternalAttemptId("retry-2");
        attempts.saveAndFlush(second);
        first.setExternalPaymentId("later-payment");
        em.flush();
        em.clear();
        assertThat(attempts.findByProviderAndExternalAttemptId(PROVIDER, "retry-1")).get()
            .extracting(PaymentAttempt::getExternalPaymentId).isEqualTo("later-payment");
        assertThat(attempts.findByBillingInvoiceIdOrderByIdAsc(invoice.getId())).hasSize(2);
        PaymentAttempt duplicate = newAttempt(invoice, null, PaymentAttemptStatus.PENDING);
        duplicate.setExternalAttemptId("retry-1");
        assertThatThrownBy(() -> attempts.saveAndFlush(duplicate)).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void unidentifiedAttemptIsRejected() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, null);
        assertThatThrownBy(() -> attempts.saveAndFlush(newAttempt(invoice, null, PaymentAttemptStatus.PENDING)))
            .isInstanceOf(javax.validation.ConstraintViolationException.class);
    }

    @Test
    void moneyAndIndependentTimestampsRoundTrip() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, "precision");
        BigDecimal amount = new BigDecimal("1234567890123456789.12");
        invoice.setAmount(amount);
        Instant paid = SEPTEMBER.plusSeconds(40);
        Instant providerUpdate = SEPTEMBER.plusSeconds(50);
        invoice.setPaidAt(paid);
        invoice.setProviderUpdatedAt(providerUpdate);
        invoice.setLastReconciledAt(providerUpdate.plusSeconds(1));
        PaymentAttempt payment = attempt(invoice, "precision-payment", PaymentAttemptStatus.APPROVED);
        payment.setAmount(amount);
        payment.setRefundedAmount(new BigDecimal("10.25"));
        payment.setApprovedAt(paid);
        payment.setProviderUpdatedAt(providerUpdate);
        em.flush();
        em.clear();
        BillingInvoice loaded = em.find(BillingInvoice.class, invoice.getId());
        assertThat(loaded.getAmount()).isEqualTo(amount);
        assertThat(loaded.getCurrency()).isEqualTo("BRL");
        assertThat(loaded.getPeriodStart()).isEqualTo(SEPTEMBER);
        assertThat(loaded.getPeriodEnd()).isEqualTo(OCTOBER);
        assertThat(loaded.getDueAt()).isEqualTo(SEPTEMBER);
        assertThat(loaded.getGracePeriodEnd()).isEqualTo(SEPTEMBER.plus(Duration.ofHours(72)));
        assertThat(loaded.getPaidAt()).isEqualTo(paid);
        assertThat(loaded.getProviderUpdatedAt()).isEqualTo(providerUpdate);
        assertThat(loaded.getLastReconciledAt()).isEqualTo(providerUpdate.plusSeconds(1));
        PaymentAttempt loadedPayment = em.find(PaymentAttempt.class, payment.getId());
        assertThat(loadedPayment.getAmount()).isEqualTo(amount);
        assertThat(loadedPayment.getRefundedAmount()).isEqualTo(new BigDecimal("10.25"));
        assertThat(loadedPayment.getApprovedAt()).isEqualTo(paid);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isPositive();
    }

    @Test
    void staleSnapshotCannotOverwriteNewerPersistedVersion() {
        BillingInvoice stale = invoice(SEPTEMBER, OCTOBER, "versioned");
        em.detach(stale);
        BillingInvoice current = em.find(BillingInvoice.class, stale.getId());
        current.setProviderUpdatedAt(SEPTEMBER.plusSeconds(20));
        em.flush();
        em.clear();
        stale.setProviderUpdatedAt(SEPTEMBER.plusSeconds(10));
        assertThatThrownBy(() -> em.merge(stale)).isInstanceOf(javax.persistence.OptimisticLockException.class);
    }

    @Test
    void invalidGraceIsRejectedWithoutInventingStateTransitions() {
        BillingInvoice invoice = newInvoice(SEPTEMBER, OCTOBER, null);
        invoice.setGracePeriodEnd(SEPTEMBER.plus(Duration.ofHours(73)));
        assertThatThrownBy(() -> invoices.saveAndFlush(invoice)).isInstanceOf(javax.validation.ConstraintViolationException.class);
    }

    @Test
    void deletingChildrenNeverCascadesToParents() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, "delete");
        PaymentAttempt payment = attempt(invoice, "delete-payment", PaymentAttemptStatus.PENDING);
        attempts.delete(payment);
        em.flush();
        em.clear();
        assertThat(em.find(BillingInvoice.class, invoice.getId())).isNotNull();
        invoices.deleteById(invoice.getId());
        em.flush();
        em.clear();
        assertThat(em.find(Subscription.class, subscription.getId())).isNotNull();
    }

    @Test
    void foreignKeyProtectsInvoiceWithAttempts() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, "protected");
        attempt(invoice, "protected-payment", PaymentAttemptStatus.PENDING);
        invoices.delete(invoice);
        assertThatThrownBy(() -> em.flush()).isInstanceOf(javax.persistence.PersistenceException.class);
    }

    @Test
    void scheduledCancellationCoexistsWithPaidFutureServicePeriod() {
        subscription.setCancelAtPeriodEnd(true);
        subscription.setCanceledAt(SEPTEMBER.plusSeconds(10));
        subscription.setCurrentPeriodEnd(OCTOBER);
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, "cancel");
        invoice.setStatus(BillingInvoiceStatus.PAID);
        invoice.setPaidAt(SEPTEMBER);
        attempt(invoice, "cancel-payment", PaymentAttemptStatus.APPROVED);
        em.flush();
        em.clear();
        Subscription loaded = em.find(Subscription.class, subscription.getId());
        assertThat(loaded.getCancelAtPeriodEnd()).isTrue();
        assertThat(loaded.getCanceledAt()).isEqualTo(SEPTEMBER.plusSeconds(10));
        assertThat(loaded.getCurrentPeriodEnd()).isEqualTo(OCTOBER);
        assertThat(em.find(BillingInvoice.class, invoice.getId()).getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
    }

    @Test
    void essentialReconciliationQueriesSelectIndependentDates() {
        BillingInvoice invoice = invoice(SEPTEMBER, OCTOBER, "query");
        invoice.setStatus(BillingInvoiceStatus.PAST_DUE);
        em.flush();
        PageRequest page = PageRequest.of(0, 10);
        assertThat(invoices.findByStatusAndDueAtLessThanEqual(BillingInvoiceStatus.PAST_DUE, SEPTEMBER, page)).hasSize(1);
        assertThat(invoices.findByStatusAndGracePeriodEndLessThanEqual(BillingInvoiceStatus.PAST_DUE, SEPTEMBER, page)).isEmpty();
        assertThat(invoices.findByStatusAndGracePeriodEndLessThanEqual(BillingInvoiceStatus.PAST_DUE, OCTOBER, page)).hasSize(1);
        assertThat(invoices.findByLastReconciledAtIsNullOrLastReconciledAtBefore(SEPTEMBER, page)).hasSize(1);
        invoice.setLastReconciledAt(OCTOBER);
        em.flush();
        assertThat(invoices.findByLastReconciledAtIsNullOrLastReconciledAtBefore(SEPTEMBER, page)).isEmpty();
    }

    @Test
    void nullableFinancialDatesPersistAndCanBeEnrichedWithoutInventingPeriods() {
        BillingInvoice first = newInvoice(SEPTEMBER, OCTOBER, "unknown-period-1");
        first.setPeriodStart(null);
        first.setPeriodEnd(null);
        first.setDueAt(null);
        first.setGracePeriodEnd(null);
        invoices.saveAndFlush(first);
        BillingInvoice second = newInvoice(SEPTEMBER, OCTOBER, "unknown-period-2");
        second.setPeriodStart(null);
        second.setPeriodEnd(null);
        second.setDueAt(null);
        second.setGracePeriodEnd(null);
        invoices.saveAndFlush(second);
        em.clear();
        BillingInvoice loaded = em.find(BillingInvoice.class, first.getId());
        assertThat(loaded.getPeriodStart()).isNull();
        assertThat(loaded.getPeriodEnd()).isNull();
        assertThat(loaded.getDueAt()).isNull();
        assertThat(loaded.getGracePeriodEnd()).isNull();
        loaded.setPeriodStart(SEPTEMBER);
        loaded.setPeriodEnd(OCTOBER);
        loaded.setDueAt(SEPTEMBER);
        loaded.setGracePeriodEnd(SEPTEMBER.plus(Duration.ofHours(72)));
        em.flush();
        em.clear();
        assertThat(em.find(BillingInvoice.class, first.getId()).getDueAt()).isEqualTo(SEPTEMBER);
        assertThat(em.find(BillingInvoice.class, second.getId()).getDueAt()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void financialIngestionReplaysAndEnrichesUsingRealRepositories(boolean temporal) {
        subscription.setExternalProvider(PROVIDER);
        subscription.setExternalSubscriptionId("pre-ingest");
        em.flush();
        com.localuz.service.MercadoPagoClient client = org.mockito.Mockito.mock(com.localuz.service.MercadoPagoClient.class);
        org.mockito.Mockito.when(client.getPreapproval("pre-ingest")).thenReturn(
            new com.localuz.service.dto.MercadoPagoPreapproval("pre-ingest", "authorized", "ref", null,
                null, null, null, temporal ? 1 : null, temporal ? "months" : null));
        com.localuz.service.dto.MercadoPagoAuthorizedPayment provisional = new com.localuz.service.dto.MercadoPagoAuthorizedPayment(
            "charge-ingest", "processed", "pre-ingest", "approved", null, new BigDecimal("100.00"), "BRL", SEPTEMBER, SEPTEMBER, null, "ref");
        org.mockito.Mockito.when(client.getAuthorizedPayment("charge-ingest")).thenReturn(provisional);
        com.localuz.service.MercadoPagoFinancialIngestion ingestion = new com.localuz.service.MercadoPagoFinancialIngestion(
            client, new JpaRepositoryFactory(em).getRepository(SubscriptionRepository.class), invoices, attempts,
            new com.localuz.service.MercadoPagoBillingStatusMapper());
        assertThat(ingestion.ingest("subscription_authorized_payment", "charge-ingest")).isTrue();
        BillingInvoice invoice = invoices.findByProviderAndExternalAuthorizedPaymentId(PROVIDER, "charge-ingest").orElseThrow();
        Long attemptId = attempts.findByBillingInvoiceIdOrderByIdAsc(invoice.getId()).get(0).getId();
        java.time.OffsetDateTime debit = java.time.OffsetDateTime.parse("2026-01-31T23:30:00-03:00");
        org.mockito.Mockito.when(client.findAuthorizedPaymentByPaymentId("payment-ingest")).thenReturn(java.util.Optional.of(
            new com.localuz.service.dto.MercadoPagoAuthorizedPayment("charge-ingest", "processed", "pre-ingest", "approved", "payment-ingest",
                new BigDecimal("100.0"), "brl", SEPTEMBER, SEPTEMBER.plusSeconds(20), temporal ? debit.toInstant() : null, "ref", temporal ? debit : null)));
        org.mockito.Mockito.when(client.getPayment("payment-ingest")).thenReturn(new com.localuz.service.dto.MercadoPagoPayment(
            "payment-ingest", "approved", "accredited", new BigDecimal("100.0"), "brl", SEPTEMBER,
            SEPTEMBER.plusSeconds(10), SEPTEMBER.plusSeconds(20), "ref", null));
        ingestion.ingest("payment", "payment-ingest");
        em.clear();
        ingestion.ingest("payment", "payment-ingest");
        em.clear();
        BillingInvoice loaded = invoices.findByProviderAndExternalAuthorizedPaymentId(PROVIDER, "charge-ingest").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(invoice.getId());
        assertThat(loaded.getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
        if (temporal) {
            assertThat(loaded.getPeriodStart()).isEqualTo(debit.toInstant());
            assertThat(loaded.getPeriodEnd()).isEqualTo(Instant.parse("2026-03-01T02:30:00Z"));
            assertThat(loaded.getDueAt()).isEqualTo(debit.toInstant());
            assertThat(loaded.getGracePeriodEnd()).isEqualTo(debit.toInstant().plus(Duration.ofHours(72)));
        } else {
            assertThat(loaded.getPeriodStart()).isNull();
            assertThat(loaded.getDueAt()).isNull();
        }
        assertThat(attempts.findByBillingInvoiceIdOrderByIdAsc(loaded.getId())).singleElement().satisfies(attempt -> {
            assertThat(attempt.getId()).isEqualTo(attemptId);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
            assertThat(attempt.getExternalPaymentId()).isEqualTo("payment-ingest");
        });
        assertThat(em.find(Subscription.class, subscription.getId()).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"refunded,100.00,REFUNDED", "approved,0.10,REFUNDED", "charged_back,0,CHARGEDBACK"})
    void committedReversalSurvivesNewSessionAndOutOfOrderReplay(String status, String refund, String invoiceStatus) throws Exception {
        operationalSetup();
        var client = org.mockito.Mockito.mock(com.localuz.service.MercadoPagoClient.class);
        var charge = new com.localuz.service.dto.MercadoPagoAuthorizedPayment("refund-db", "processed", "reserve-pre", "approved",
            "refund-payment", new BigDecimal("100.00"), "BRL", NOVEMBER, NOVEMBER, NOVEMBER, "ref", NOVEMBER.atOffset(java.time.ZoneOffset.UTC));
        org.mockito.Mockito.when(client.getAuthorizedPayment("refund-db")).thenReturn(charge);
        org.mockito.Mockito.when(client.getPreapproval("reserve-pre")).thenReturn(new com.localuz.service.dto.MercadoPagoPreapproval(
            "reserve-pre", "authorized", "ref", null, SEPTEMBER, DECEMBER, NOVEMBER, 1, "months"));
        Long invoiceId = null;
        Long attemptId = null;
        try {
            for (int phase = 0; phase < 4; phase++) {
                boolean reversal = phase == 1 || phase == 2;
                org.mockito.Mockito.when(client.getPayment("refund-payment")).thenReturn(new com.localuz.service.dto.MercadoPagoPayment(
                    "refund-payment", reversal ? status : "approved", reversal && status.equals("approved") ? "partially_refunded" : "accredited",
                    new BigDecimal("100.00"), "BRL", NOVEMBER, reversal ? null : NOVEMBER,
                    reversal ? NOVEMBER.plusSeconds(10) : NOVEMBER, "ref", reversal ? new BigDecimal(refund) : BigDecimal.ZERO));
                EntityManager local = factory.createEntityManager();
                try {
                    local.getTransaction().begin();
                    var repos = new JpaRepositoryFactory(local);
                    var invoiceRepository = repos.getRepository(BillingInvoiceRepository.class);
                    var attemptRepository = repos.getRepository(PaymentAttemptRepository.class);
                    var ingestion = new com.localuz.service.MercadoPagoFinancialIngestion(client,
                        repos.getRepository(SubscriptionRepository.class), invoiceRepository, attemptRepository,
                        new com.localuz.service.MercadoPagoBillingStatusMapper());
                    assertThat(ingestion.ingest("subscription_authorized_payment", "refund-db")).isTrue();
                    var invoice = invoiceRepository.findByProviderAndExternalAuthorizedPaymentId(PROVIDER, "refund-db").orElseThrow();
                    var stored = attemptRepository.findByBillingInvoiceIdOrderByIdAsc(invoice.getId());
                    assertThat(stored).hasSize(1);
                    if (phase == 0) { invoiceId = invoice.getId(); attemptId = stored.get(0).getId(); }
                    assertThat(invoice.getId()).isEqualTo(invoiceId);
                    assertThat(stored.get(0).getId()).isEqualTo(attemptId);
                    assertThat(stored.get(0).getApprovedAt()).isEqualTo(NOVEMBER);
                    assertThat(invoice.getPaidAt()).isEqualTo(NOVEMBER);
                    assertThat(invoice.getPeriodStart()).isEqualTo(NOVEMBER);
                    assertThat(invoice.getPeriodEnd()).isEqualTo(DECEMBER);
                    assertThat(invoice.getDueAt()).isEqualTo(NOVEMBER);
                    assertThat(invoice.getStatus()).isEqualTo(phase == 0 ? BillingInvoiceStatus.PAID : BillingInvoiceStatus.valueOf(invoiceStatus));
                    var coverage = new com.localuz.service.SubscriptionFinancialCoverageService(invoiceRepository, attemptRepository);
                    assertThat(coverage.evaluate(local.find(Subscription.class, 90001L), NOVEMBER.plusSeconds(3600)).covered()).isEqualTo(phase == 0);
                    if (phase > 0) {
                        assertThat(stored.get(0).getRefundedAmount()).isEqualByComparingTo(refund);
                        assertThat(stored.get(0).getProviderUpdatedAt()).isEqualTo(NOVEMBER.plusSeconds(10));
                    }
                    local.getTransaction().commit();
                } finally {
                    if (local.getTransaction().isActive()) local.getTransaction().rollback();
                    local.close();
                }
            }
        } finally {
            // Clean only this test's committed financial rows in the disposable MySQL database.
            try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
                connection.createStatement().executeUpdate("DELETE a FROM payment_attempt a JOIN billing_invoice i ON i.id=a.billing_invoice_id WHERE i.external_authorized_payment_id='refund-db' AND i.subscription_id=90001");
                connection.createStatement().executeUpdate("DELETE FROM billing_invoice WHERE external_authorized_payment_id='refund-db' AND subscription_id=90001");
            }
            operationalCleanup();
        }
    }

    @Test void reservationSurvivesRestartAndNormalStaleOrmSave() throws Exception {
        operationalSetup();
        EntityManager stale = factory.createEntityManager();
        try {
            stale.getTransaction().begin();
            Subscription loaded = stale.find(Subscription.class, 90001L);
            assertThat(loaded.getLastFinancialReconciliationAt()).isNull();
            var candidate = new com.localuz.service.RecurringBillingReservationService.Candidate(90001L, "reserve-pre");
            assertThat(reservationService().reserve(candidate, NOVEMBER, NOVEMBER.minusSeconds(3600))).isTrue();
            loaded.setContractedVehicleCount(2);
            stale.flush();
            stale.getTransaction().commit();
            var restarted = reservationService();
            assertThat(restarted.reserve(candidate, NOVEMBER.plusSeconds(3599), NOVEMBER.minusSeconds(1))).isFalse();
            assertThat(restarted.candidates(NOVEMBER.minusSeconds(1), NOVEMBER, 90, 10)).isEmpty();
            try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
                 var rows = connection.createStatement().executeQuery("SELECT last_financial_reconciliation_at FROM subscription WHERE id=90001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getTimestamp(1).toLocalDateTime()).isEqualTo(java.time.LocalDateTime.ofInstant(NOVEMBER, java.time.ZoneOffset.UTC));
            }
            assertThat(restarted.reserve(candidate, NOVEMBER.plusSeconds(3600), NOVEMBER)).isTrue();
        } finally {
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            stale.close(); operationalCleanup();
        }
    }

    @Test void exactlyOneConcurrentInstanceReservesTheWindow() throws Exception {
        operationalSetup();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = reservationService();
            var second = reservationService();
            var candidate = new com.localuz.service.RecurringBillingReservationService.Candidate(90001L, "reserve-pre");
            var a = pool.submit(() -> { start.await(); return first.reserve(candidate, NOVEMBER, OCTOBER); });
            var b = pool.submit(() -> { start.await(); return second.reserve(candidate, NOVEMBER, OCTOBER); });
            start.countDown();
            assertThat(java.util.List.of(a.get(20, java.util.concurrent.TimeUnit.SECONDS), b.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); operationalCleanup(); }
    }

    @Test void selectionExcludesOtherSourcesAndIncludesCancelledWithoutCheckout() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            var service = reservationService();
            for (String source : java.util.List.of("ADMIN_GRANT", "GRANDFATHERED")) {
                connection.createStatement().executeUpdate("UPDATE subscription SET source='" + source + "' WHERE id=90001");
                assertThat(service.candidates(NOVEMBER, NOVEMBER, 90, 1)).isEmpty();
            }
            connection.createStatement().executeUpdate("UPDATE subscription SET source='PAYMENT_PROVIDER',status='CANCELED' WHERE id=90001");
            assertThat(service.candidates(NOVEMBER, NOVEMBER, 90, 1)).singleElement().extracting(com.localuz.service.RecurringBillingReservationService.Candidate::id).isEqualTo(90001L);
            connection.createStatement().executeUpdate("UPDATE subscription SET external_provider='OTHER' WHERE id=90001");
            assertThat(service.candidates(NOVEMBER, NOVEMBER, 90, 1)).isEmpty();
            connection.createStatement().executeUpdate("UPDATE subscription SET external_provider='MERCADO_PAGO',external_subscription_id='' WHERE id=90001");
            assertThat(service.candidates(NOVEMBER, NOVEMBER, 90, 1)).isEmpty();
        } finally { operationalCleanup(); }
    }

    // --- 5G.7: cancelled terminal reconciliation horizon ---
    // terminalAt = max(billing_invoice.period_end) + horizonDays, falling back to canceled_at
    // only when no invoice period_end exists. Only gates candidates() (polling); never touches
    // entitlement, history, or the webhook lookup path (findByExternalProviderAndExternalSubscriptionId).

    @Test void cancelledWithRecentInvoicePeriodEndRemainsReconcilable() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            insertTerminalInvoice(connection, "term-recent", OCTOBER);
            assertThat(reservationService().candidates(NOVEMBER, NOVEMBER, 90, 1)).singleElement()
                .extracting(com.localuz.service.RecurringBillingReservationService.Candidate::id).isEqualTo(90001L);
        } finally { cleanupTerminalFixtures(); operationalCleanup(); }
    }

    @Test void cancelledJustBeforeTerminalAtRemainsReconcilable() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            insertTerminalInvoice(connection, "term-before", SEPTEMBER);
            Instant justBeforeTerminalAt = SEPTEMBER.plus(Duration.ofDays(1)).minusSeconds(1);
            assertThat(reservationService().candidates(justBeforeTerminalAt, justBeforeTerminalAt, 1, 1)).singleElement()
                .extracting(com.localuz.service.RecurringBillingReservationService.Candidate::id).isEqualTo(90001L);
        } finally { cleanupTerminalFixtures(); operationalCleanup(); }
    }

    @Test void cancelledExactlyAtTerminalAtIsNotReconciled() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            insertTerminalInvoice(connection, "term-exact", SEPTEMBER);
            Instant terminalAt = SEPTEMBER.plus(Duration.ofDays(1));
            assertThat(reservationService().candidates(terminalAt, terminalAt, 1, 1)).isEmpty();
        } finally { cleanupTerminalFixtures(); operationalCleanup(); }
    }

    @Test void cancelledAfterTerminalAtIsNotReconciled() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            insertTerminalInvoice(connection, "term-after", SEPTEMBER);
            Instant afterTerminalAt = SEPTEMBER.plus(Duration.ofDays(1)).plusSeconds(1);
            assertThat(reservationService().candidates(afterTerminalAt, afterTerminalAt, 1, 1)).isEmpty();
        } finally { cleanupTerminalFixtures(); operationalCleanup(); }
    }

    @Test void cancelledWithoutAnyInvoicePeriodEndFallsBackToCanceledAtAndEventuallyStopsBeingReconciled() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            try (var ps = connection.prepareStatement("UPDATE subscription SET status='CANCELED',canceled_at=? WHERE id=90001")) {
                ps.setTimestamp(1, java.sql.Timestamp.from(SEPTEMBER));
                ps.executeUpdate();
            }
            Instant beforeTerminalAt = SEPTEMBER.plus(Duration.ofDays(1)).minusSeconds(1);
            assertThat(reservationService().candidates(beforeTerminalAt, beforeTerminalAt, 1, 1)).singleElement()
                .extracting(com.localuz.service.RecurringBillingReservationService.Candidate::id).isEqualTo(90001L);
            Instant afterTerminalAt = SEPTEMBER.plus(Duration.ofDays(1)).plusSeconds(1);
            assertThat(reservationService().candidates(afterTerminalAt, afterTerminalAt, 1, 1)).isEmpty();
        } finally { operationalCleanup(); }
    }

    @Test void cancelledWithNeitherInvoicePeriodEndNorCanceledAtIsNeverExcludedByTheTerminalHorizon() throws Exception {
        // No date is fabricated when no reliable anchor exists (see docs/billing-hardening-5g7.md):
        // the subscription simply keeps being a candidate, same as before 5G.7.
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            assertThat(reservationService().candidates(DECEMBER, DECEMBER, 1, 1)).singleElement()
                .extracting(com.localuz.service.RecurringBillingReservationService.Candidate::id).isEqualTo(90001L);
        } finally { operationalCleanup(); }
    }

    @Test void terminalExclusionOnlyAffectsCandidateSelectionNeverTheWebhookLookupPathOrPersistedFields() throws Exception {
        operationalSetup();
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET status='CANCELED',canceled_at=NULL WHERE id=90001");
            insertTerminalInvoice(connection, "term-preserved", SEPTEMBER);
            Instant afterTerminalAt = SEPTEMBER.plus(Duration.ofDays(1)).plusSeconds(1);
            assertThat(reservationService().candidates(afterTerminalAt, afterTerminalAt, 1, 1)).isEmpty();
            // The exact repository method MercadoPagoWebhookProcessor uses to find the subscription
            // for an inbound webhook is untouched by the terminal-horizon query.
            var repository = new JpaRepositoryFactory(org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory))
                .getRepository(SubscriptionRepository.class);
            var found = repository.findByExternalProviderAndExternalSubscriptionId("MERCADO_PAGO", "reserve-pre");
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            try (var rows = connection.createStatement().executeQuery(
                "SELECT status, amount, period_end FROM billing_invoice WHERE external_authorized_payment_id='term-preserved' AND subscription_id=90001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo("PENDING");
                assertThat(rows.getBigDecimal("amount")).isEqualByComparingTo("100.00");
                assertThat(rows.getTimestamp("period_end").toInstant()).isEqualTo(SEPTEMBER);
            }
        } finally { cleanupTerminalFixtures(); operationalCleanup(); }
    }

    private void insertTerminalInvoice(Connection connection, String externalId, Instant periodEnd) throws Exception {
        Instant start = periodEnd.minus(Duration.ofDays(30));
        try (var ps = connection.prepareStatement("INSERT INTO billing_invoice " +
            "(subscription_id,status,amount,currency,period_start,period_end,due_at,grace_period_end,provider,external_authorized_payment_id,created_at,updated_at) " +
            "VALUES (90001,'PENDING',100.00,'BRL',?,?,?,?,'MERCADO_PAGO',?,NOW(6),NOW(6))")) {
            ps.setTimestamp(1, java.sql.Timestamp.from(start));
            ps.setTimestamp(2, java.sql.Timestamp.from(periodEnd));
            ps.setTimestamp(3, java.sql.Timestamp.from(start));
            ps.setTimestamp(4, java.sql.Timestamp.from(start.plus(Duration.ofHours(72))));
            ps.setString(5, externalId);
            ps.executeUpdate();
        }
    }

    private void cleanupTerminalFixtures() throws Exception {
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("DELETE FROM billing_invoice WHERE subscription_id=90001 AND external_authorized_payment_id LIKE 'term-%'");
        }
    }

    @Test void providerFailureHasNoHttpTransactionAndReservationSurvivesOuterRollback() throws Exception {
        operationalSetup();
        try {
            var manager = new org.springframework.orm.jpa.JpaTransactionManager(factory);
            var client = org.mockito.Mockito.mock(com.localuz.service.MercadoPagoClient.class);
            var ingestion = org.mockito.Mockito.mock(com.localuz.service.MercadoPagoFinancialIngestion.class);
            var config = new com.localuz.config.RecurringBillingReconciliationProperties();
            var candidate = new com.localuz.service.RecurringBillingReservationService.Candidate(90001L, "reserve-pre");
            org.mockito.Mockito.when(client.searchAuthorizedPayments(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(call -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    // A new service/connection already sees the committed reservation before HTTP completes.
                    assertThat(reservationService().reserve(candidate, NOVEMBER, NOVEMBER.minusSeconds(3600))).isFalse();
                    throw new com.localuz.service.MercadoPagoException("simulated timeout", true);
                });
            var target = new com.localuz.service.RecurringBillingReconciliationService(client, ingestion, reservationService(), config,
                java.time.Clock.fixed(NOVEMBER, java.time.ZoneOffset.UTC));
            var proxy = new org.springframework.aop.framework.ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var service = (com.localuz.service.RecurringBillingReconciliationService) proxy.getProxy();
            new org.springframework.transaction.support.TransactionTemplate(manager).execute(status -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(service.reconcile(candidate, new com.localuz.service.RecurringReconciliationBudget(10)).outcome())
                    .isEqualTo(com.localuz.service.dto.RecurringReconciliationResult.Outcome.PROVIDER_FAILURE);
                status.setRollbackOnly();
                return null;
            });
            assertThat(reservationService().reserve(candidate, NOVEMBER, NOVEMBER.minusSeconds(3600))).isFalse();
            org.mockito.Mockito.verifyNoInteractions(ingestion);
        } finally { operationalCleanup(); }
    }

    private com.localuz.service.RecurringBillingReservationService reservationService() {
        var manager = new org.springframework.orm.jpa.JpaTransactionManager(factory);
        var shared = org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
        var repository = new JpaRepositoryFactory(shared).getRepository(SubscriptionRepository.class);
        var target = new com.localuz.service.RecurringBillingReservationService(repository);
        var proxy = new org.springframework.aop.framework.ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (com.localuz.service.RecurringBillingReservationService) proxy.getProxy();
    }

    private void operationalSetup() throws Exception {
        // Separate committed connection: these tests exercise real independent reservation transactions.
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET external_provider='MERCADO_PAGO',external_subscription_id='reserve-pre',last_financial_reconciliation_at=NULL WHERE id=90001");
        }
    }

    private void operationalCleanup() throws Exception {
        try (var connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            connection.createStatement().executeUpdate("UPDATE subscription SET external_provider=NULL,external_subscription_id=NULL,last_financial_reconciliation_at=NULL,contracted_vehicle_count=1,source='PAYMENT_PROVIDER',status='ACTIVE',canceled_at=NULL WHERE id=90001");
        }
    }

    private BillingInvoice newInvoice(Instant start, Instant end, String externalId) {
        BillingInvoice invoice = new BillingInvoice();
        invoice.setSubscription(subscription);
        invoice.setProvider(PROVIDER);
        invoice.setExternalAuthorizedPaymentId(externalId);
        invoice.setStatus(BillingInvoiceStatus.PENDING);
        invoice.setAmount(new BigDecimal("100.00"));
        invoice.setCurrency("BRL");
        invoice.setPeriodStart(start);
        invoice.setPeriodEnd(end);
        invoice.setDueAt(start);
        invoice.setGracePeriodEnd(start.plus(Duration.ofHours(72)));
        return invoice;
    }

    private BillingInvoice invoice(Instant start, Instant end, String externalId) {
        return invoices.saveAndFlush(newInvoice(start, end, externalId));
    }

    private PaymentAttempt newAttempt(BillingInvoice invoice, String externalId, PaymentAttemptStatus status) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setBillingInvoice(invoice);
        attempt.setProvider(invoice.getProvider());
        attempt.setExternalPaymentId(externalId);
        attempt.setStatus(status);
        attempt.setAmount(new BigDecimal("100.00"));
        attempt.setCurrency("BRL");
        return attempt;
    }

    private PaymentAttempt attempt(BillingInvoice invoice, String externalId, PaymentAttemptStatus status) {
        return attempts.saveAndFlush(newAttempt(invoice, externalId, status));
    }
}
