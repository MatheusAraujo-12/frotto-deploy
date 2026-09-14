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
    private static EntityManagerFactory factory;
    private EntityManager em;
    private BillingInvoiceRepository invoices;
    private PaymentAttemptRepository attempts;
    private Subscription subscription;

    @BeforeAll
    static void schemaAndJpa() throws Exception {
        MYSQL.start();
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            Liquibase liquibase = new Liquibase("config/liquibase/master.xml", new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            // Install the preceding schema first, then keep a real legacy subscription across the new migration.
            liquibase.getDatabaseChangeLog().getChangeSets().removeIf(change -> (change.getId().startsWith("20260911000000-") || change.getId().startsWith("20260914000000-")));
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
            liquibase = new Liquibase("config/liquibase/master.xml", new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));
            liquibase.update(new Contexts("test"));
            try (java.sql.ResultSet rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM billing_invoice")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
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
        MYSQL.stop();
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

    @Test
    void financialIngestionReplaysAndEnrichesUsingRealRepositories() {
        subscription.setExternalProvider(PROVIDER);
        subscription.setExternalSubscriptionId("pre-ingest");
        em.flush();
        com.localuz.service.MercadoPagoClient client = org.mockito.Mockito.mock(com.localuz.service.MercadoPagoClient.class);
        org.mockito.Mockito.when(client.getPreapproval("pre-ingest")).thenReturn(
            new com.localuz.service.dto.MercadoPagoPreapproval("pre-ingest", "authorized", "ref", null));
        com.localuz.service.dto.MercadoPagoAuthorizedPayment provisional = new com.localuz.service.dto.MercadoPagoAuthorizedPayment(
            "charge-ingest", "processed", "pre-ingest", "approved", null, new BigDecimal("100.00"), "BRL", SEPTEMBER, SEPTEMBER, null, "ref");
        org.mockito.Mockito.when(client.getAuthorizedPayment("charge-ingest")).thenReturn(provisional);
        com.localuz.service.MercadoPagoFinancialIngestion ingestion = new com.localuz.service.MercadoPagoFinancialIngestion(
            client, new JpaRepositoryFactory(em).getRepository(SubscriptionRepository.class), invoices, attempts,
            new com.localuz.service.MercadoPagoBillingStatusMapper());
        assertThat(ingestion.ingest("subscription_authorized_payment", "charge-ingest")).isTrue();
        BillingInvoice invoice = invoices.findByProviderAndExternalAuthorizedPaymentId(PROVIDER, "charge-ingest").orElseThrow();
        Long attemptId = attempts.findByBillingInvoiceIdOrderByIdAsc(invoice.getId()).get(0).getId();
        org.mockito.Mockito.when(client.findAuthorizedPaymentByPaymentId("payment-ingest")).thenReturn(java.util.Optional.of(
            new com.localuz.service.dto.MercadoPagoAuthorizedPayment("charge-ingest", "processed", "pre-ingest", "approved", "payment-ingest",
                new BigDecimal("100.0"), "brl", SEPTEMBER, SEPTEMBER.plusSeconds(20), null, "ref")));
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
        assertThat(loaded.getPeriodStart()).isNull();
        assertThat(loaded.getDueAt()).isNull();
        assertThat(attempts.findByBillingInvoiceIdOrderByIdAsc(loaded.getId())).singleElement().satisfies(attempt -> {
            assertThat(attempt.getId()).isEqualTo(attemptId);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
            assertThat(attempt.getExternalPaymentId()).isEqualTo("payment-ingest");
        });
        assertThat(em.find(Subscription.class, subscription.getId()).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
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
