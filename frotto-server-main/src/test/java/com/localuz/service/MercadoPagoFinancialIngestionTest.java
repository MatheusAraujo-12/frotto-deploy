package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import com.localuz.service.dto.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Rewritten for the 5G.9 architecture change: a "payment" webhook is now correlated to its
 * Subscription via point_of_interaction.transaction_data.subscription_id (parsed onto
 * MercadoPagoPayment as subscriptionId/subscriptionSequenceNumber), not by searching for an
 * AuthorizedPayment charge that shares its payment_id - see MercadoPagoFinancialIngestion#ingest
 * and #fetchPaymentSnapshot/#persistFromPayment. The Frotto checkout flow (POST /preapproval with
 * status=pending + back_url/init_point, no card_token_id) never produces a queryable
 * AuthorizedPayment resource (confirmed experimentally in staging - see the 5G.9 report), so that
 * search is not exercised by the "payment" tests below; the "subscription_authorized_payment"
 * event type keeps using the original charge-anchored path unchanged (fetch/persist), still
 * exercised by the handful of tests that use it here in case a future flow starts producing one.
 *
 * Competency (periodStart/periodEnd) is derived from subscriptionSequenceNumber + the
 * subscription's own startDate/frequency (MercadoPagoPaymentInvoiceTemporalEnricher), not from a
 * debit_date - most tests below use snapshot(), which leaves subscriptionSequenceNumber null (no
 * competency claimed), matching the original file's separation between plain financial tests and
 * the dedicated temporal ones (temporalSnapshot()).
 */
class MercadoPagoFinancialIngestionTest {
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private final MercadoPagoClient client = mock(MercadoPagoClient.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final BillingInvoiceRepository invoices = mock(BillingInvoiceRepository.class);
    private final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    private final Map<Long, BillingInvoice> storedInvoices = new LinkedHashMap<>();
    private final Map<Long, PaymentAttempt> storedAttempts = new LinkedHashMap<>();
    private final Subscription subscription = new Subscription();
    private final MercadoPagoFinancialIngestion service = new MercadoPagoFinancialIngestion(
        client, subscriptions, invoices, attempts, new MercadoPagoBillingStatusMapper());

    @BeforeEach void setUp() {
        subscription.setId(1L);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setExternalProvider("MERCADO_PAGO");
        subscription.setExternalSubscriptionId("pre-1");
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setCurrentPeriodEnd(NOW.plusSeconds(100));
        when(subscriptions.findForFinancialIngestion("MERCADO_PAGO", "pre-1")).thenReturn(Optional.of(subscription));
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null));
        when(invoices.findByProviderAndExternalAuthorizedPaymentId(anyString(), anyString())).thenAnswer(c ->
            storedInvoices.values().stream().filter(i -> i.getExternalAuthorizedPaymentId() != null
                && i.getExternalAuthorizedPaymentId().equals(c.getArgument(1))).findFirst());
        when(invoices.findBySubscriptionIdAndPeriodStartAndPeriodEnd(anyLong(), any(), any())).thenAnswer(c ->
            storedInvoices.values().stream().filter(i -> Objects.equals(i.getPeriodStart(), c.getArgument(1))
                && Objects.equals(i.getPeriodEnd(), c.getArgument(2))).findFirst());
        when(invoices.saveAndFlush(any())).thenAnswer(c -> {
            BillingInvoice i = c.getArgument(0);
            if (i.getId() == null) i.setId((long) storedInvoices.size() + 1);
            storedInvoices.put(i.getId(), i);
            return i;
        });
        when(attempts.findByProviderAndExternalAttemptId(anyString(), anyString())).thenAnswer(c ->
            storedAttempts.values().stream().filter(a -> Objects.equals(a.getExternalAttemptId(), c.getArgument(1))).findFirst());
        when(attempts.findByProviderAndExternalPaymentId(anyString(), anyString())).thenAnswer(c ->
            storedAttempts.values().stream().filter(a -> Objects.equals(a.getExternalPaymentId(), c.getArgument(1))).findFirst());
        when(attempts.findByBillingInvoiceIdOrderByIdAsc(anyLong())).thenAnswer(c ->
            storedAttempts.values().stream().filter(a -> a.getBillingInvoice().getId().equals(c.getArgument(0))).collect(java.util.stream.Collectors.toList()));
        when(attempts.saveAndFlush(any())).thenAnswer(c -> {
            PaymentAttempt a = c.getArgument(0);
            if (a.getId() == null) a.setId((long) storedAttempts.size() + 1);
            storedAttempts.put(a.getId(), a);
            return a;
        });
        snapshot("pay-1", "approved", "15.9", "brl", NOW);
    }

    @ParameterizedTest @ValueSource(strings = {"15.9", "15.90", "15.900"})
    void uniqueCorrelationPersistsApprovedPaymentWithEconomicMoneyComparison(String amount) {
        snapshot("pay-1", "approved", amount, "brl", NOW);
        assertThat(service.ingest("payment", "pay-1")).isTrue();
        assertThat(invoice().getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
        assertThat(attempt().getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt().getCurrency()).isEqualTo("BRL");
        assertThat(invoice().getPaidAt()).isEqualTo(NOW.minusSeconds(1));
        verify(client).getPayment("pay-1");
        verify(client).getPreapproval("pre-1");
        verify(subscriptions, never()).save(any());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(NOW.plusSeconds(100));
    }

    @Test void unknownDatesStayNullEvenWhenFinancialDatesExist() {
        service.ingest("payment", "pay-1");
        assertThat(invoice().getPeriodStart()).isNull();
        assertThat(invoice().getPeriodEnd()).isNull();
        assertThat(invoice().getDueAt()).isNull();
        assertThat(invoice().getGracePeriodEnd()).isNull();
        assertThat(invoice().isPeriodValid()).isTrue();
    }

    @Test void missingSubscriptionIdOnThePaymentIsIgnoredWithoutGuessing() {
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "approved", "accredited",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), NOW.minusSeconds(1), NOW, "ref", null, null, null));
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(subscriptions, invoices, attempts);
        verify(client, never()).getPreapproval(anyString());
    }

    @Test void ambiguousPreapprovalConfirmationPropagatesWithoutFinancialMutation() {
        when(client.getPreapproval("pre-1")).thenThrow(new MercadoPagoException("ambiguous", false));
        assertThatThrownBy(() -> service.ingest("payment", "pay-1")).isInstanceOf(MercadoPagoException.class);
        verifyNoInteractions(subscriptions, invoices, attempts);
    }

    @Test void divergentPaymentIdDoesNotAssociate() {
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("other", "approved", "accredited",
            new BigDecimal("15.90"), "BRL", NOW, NOW, NOW, "ref", null, "pre-1", null));
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(subscriptions, invoices, attempts);
    }

    @Test void divergentPreapprovalIdDoesNotAssociate() {
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("other", "authorized", "ref", null));
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(invoices, attempts);
    }

    @Test void conflictingReferencesDoNotAssociate() {
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "other", null));
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(invoices, attempts);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN_GRANT", "GRANDFATHERED"})
    void otherSubscriptionSourcesAreNeverUsed(String source) {
        subscription.setSource(SubscriptionSource.valueOf(source));
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(invoices, attempts);
    }

    @Test void missingSubscriptionIsNotCreatedFromFinancialEvent() {
        when(subscriptions.findForFinancialIngestion(anyString(), anyString())).thenReturn(Optional.empty());
        assertThat(service.ingest("payment", "pay-1")).isFalse();
        verifyNoInteractions(invoices, attempts);
        verify(subscriptions, never()).save(any());
    }

    @Test void mismatchedAmountIsAuditedButInvoiceIsNotPaid() {
        // Same payment id observed twice with a different amount: the invoice keeps its
        // originally-established amount, so the second observation cannot buy itself a PAID status.
        snapshot("pay-1", "pending", "15.90", "BRL", NOW.minusSeconds(5));
        service.ingest("payment", "pay-1");
        snapshot("pay-1", "approved", "16.00", "BRL", NOW);
        service.ingest("payment", "pay-1");
        assertThat(invoice().getStatus()).isNotEqualTo(BillingInvoiceStatus.PAID);
        assertThat(attempt().getAmount()).isEqualByComparingTo("16.00");
        assertThat(invoice().getAmount()).isEqualByComparingTo("15.90");
    }

    @Test void mismatchedCurrencyIsAuditedButInvoiceIsNotPaid() {
        snapshot("pay-1", "pending", "15.90", "BRL", NOW.minusSeconds(5));
        service.ingest("payment", "pay-1");
        snapshot("pay-1", "approved", "15.90", "USD", NOW);
        service.ingest("payment", "pay-1");
        assertThat(invoice().getStatus()).isNotEqualTo(BillingInvoiceStatus.PAID);
        assertThat(attempt().getCurrency()).isEqualTo("USD");
    }

    @Test void replayUsesSameInvoiceAndAttempt() {
        service.ingest("payment", "pay-1");
        service.ingest("payment", "pay-1");
        assertThat(storedInvoices).hasSize(1);
        assertThat(storedAttempts).hasSize(1);
        verify(attempts, times(1)).saveAndFlush(any());
    }

    @Test void secondPaymentWithoutSequenceIsAnIndependentUnperiodizedInvoice() {
        snapshot("pay-1", "rejected", "15.90", "BRL", NOW);
        service.ingest("payment", "pay-1");
        snapshot("pay-2", "approved", "15.90", "BRL", NOW.plusSeconds(5));
        service.ingest("payment", "pay-2");
        assertThat(storedInvoices).hasSize(2);
        assertThat(storedAttempts).hasSize(2);
        assertThat(storedInvoices.get(2L).getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
        assertThat(storedInvoices.get(1L).getStatus()).isEqualTo(BillingInvoiceStatus.PENDING);
    }

    @Test void latePendingCannotRegressApprovedOrOverwriteMoney() {
        service.ingest("payment", "pay-1");
        snapshot("pay-1", "pending", "90.00", "USD", NOW.minusSeconds(5));
        service.ingest("payment", "pay-1");
        assertThat(invoice().getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
        assertThat(attempt().getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt().getAmount()).isEqualByComparingTo("15.9");
        assertThat(attempt().getProviderUpdatedAt()).isEqualTo(NOW);
    }

    @Test void undatedSnapshotCannotOverwriteDatedSnapshot() {
        service.ingest("payment", "pay-1");
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "pending", "accredited",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), null, null, "ref", null, "pre-1", null));
        service.ingest("payment", "pay-1");
        assertThat(attempt().getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt().getProviderUpdatedAt()).isEqualTo(NOW);
    }

    @Test void approvedStatusEmbeddedInChargeDoesNotProvePayment() {
        when(client.getAuthorizedPayment("charge-1")).thenReturn(charge("charge-1", null, NOW));
        service.ingest("subscription_authorized_payment", "charge-1");
        assertThat(invoice().getStatus()).isEqualTo(BillingInvoiceStatus.PROCESSING);
        assertThat(attempt().getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
        verify(client, never()).getPayment(anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "authorized", "in_process", "rejected", "cancelled"})
    void unapprovedAuthoritativePaymentDoesNotSettle(String status) {
        chargeSnapshot("charge-1", "pay-1", status, NOW);
        service.ingest("subscription_authorized_payment", "charge-1");
        verify(client).getAuthorizedPayment("charge-1");
        verify(client).getPayment("pay-1");
        assertThat(invoice().getStatus()).isNotEqualTo(BillingInvoiceStatus.PAID);
        verify(subscriptions, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"refunded", "charged_back"})
    void reversalsAreFinancialOnlyAndOldApprovalCannotRestoreThem(String status) {
        service.ingest("payment", "pay-1");
        snapshot("pay-1", status, "15.90", "BRL", NOW.plusSeconds(5));
        service.ingest("payment", "pay-1");
        snapshot("pay-1", "approved", "15.90", "BRL", NOW);
        service.ingest("payment", "pay-1");
        assertThat(invoice().getStatus()).isEqualTo("refunded".equals(status) ? BillingInvoiceStatus.REFUNDED : BillingInvoiceStatus.CHARGEDBACK);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test void newerIncompleteSnapshotDoesNotEraseKnownAuditDates() {
        service.ingest("payment", "pay-1");
        Instant created = attempt().getProviderCreatedAt();
        Instant approved = attempt().getApprovedAt();
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "refunded", null,
            new BigDecimal("15.90"), "BRL", null, null, NOW.plusSeconds(10), "ref", new BigDecimal("15.900"), "pre-1", null));
        service.ingest("payment", "pay-1");
        assertThat(attempt().getProviderCreatedAt()).isEqualTo(created);
        assertThat(attempt().getAttemptedAt()).isEqualTo(created);
        assertThat(attempt().getApprovedAt()).isEqualTo(approved);
        assertThat(attempt().getRefundedAmount()).isEqualTo(new BigDecimal("15.90"));
    }

    @Test void unexpectedPersistenceFailurePropagates() {
        doThrow(new DataIntegrityViolationException("unexpected")).when(invoices).saveAndFlush(any());
        assertThatThrownBy(() -> service.ingest("payment", "pay-1")).isInstanceOf(DataIntegrityViolationException.class);
        // Resolving which invoice this payment belongs to requires a read (findByProviderAndExternalPaymentId);
        // no attempt is ever written when the invoice itself never persists.
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test void parentIsLockedBeforeFinancialLookupsAndWrites() {
        service.ingest("payment", "pay-1");
        org.mockito.InOrder order = inOrder(subscriptions, invoices, attempts);
        order.verify(subscriptions).findForFinancialIngestion("MERCADO_PAGO", "pre-1");
        order.verify(invoices).saveAndFlush(any());
        assertThat(SubscriptionRepository.class.getMethods()).anySatisfy(method -> {
            assertThat(method.getName()).isEqualTo("findForFinancialIngestion");
            assertThat(method.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
                .isEqualTo(javax.persistence.LockModeType.PESSIMISTIC_WRITE);
        });
    }

    @Test void paymentWebhookBodyCannotOverrideAuthoritativeRejection() throws Exception {
        snapshot("pay-1", "rejected", "15.90", "BRL", NOW);
        MercadoPagoWebhookEventRepository events = mock(MercadoPagoWebhookEventRepository.class);
        MercadoPagoWebhookProcessor processor = new MercadoPagoWebhookProcessor(client, mock(BillingCheckoutRepository.class), subscriptions, events, service);
        MercadoPagoWebhookSignatureValidator validator = mock(MercadoPagoWebhookSignatureValidator.class);
        when(validator.isValid(any(), any(), any())).thenReturn(true);
        com.localuz.config.MercadoPagoProperties properties = new com.localuz.config.MercadoPagoProperties();
        properties.setEnabled(true);
        com.localuz.web.rest.MercadoPagoWebhookResource resource = new com.localuz.web.rest.MercadoPagoWebhookResource(properties, validator,
            new MercadoPagoWebhookDeliveryService(new MercadoPagoWebhookEventService(events), processor));
        com.fasterxml.jackson.databind.ObjectMapper json = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        MercadoPagoWebhookPayload payload = json.readValue(
            "{\"type\":\"payment\",\"data\":{\"id\":\"pay-1\"},\"status\":\"approved\",\"transaction_amount\":999,\"currency_id\":\"USD\"}", MercadoPagoWebhookPayload.class);
        assertThat(resource.receive("sig", "request-1", "pay-1", payload).getStatusCodeValue()).isEqualTo(200);
        assertThat(attempt().getStatus()).isEqualTo(PaymentAttemptStatus.REJECTED);
        assertThat(attempt().getAmount()).isEqualByComparingTo("15.90");
        assertThat(invoice().getStatus()).isNotEqualTo(BillingInvoiceStatus.PAID);
        org.mockito.ArgumentCaptor<com.localuz.domain.MercadoPagoWebhookEvent> saved = org.mockito.ArgumentCaptor.forClass(com.localuz.domain.MercadoPagoWebhookEvent.class);
        verify(events, atLeastOnce()).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProcessingStatus()).isEqualTo(com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus.PROCESSED);
    }

    @Test void preapprovalAuthorizedCreatesNoFinancialEvidence() {
        BillingCheckoutRepository checkouts = mock(BillingCheckoutRepository.class);
        BillingCheckout checkout = new BillingCheckout();
        checkout.setExternalReference("ref");
        when(checkouts.findByProviderSubscriptionId("pre-1")).thenReturn(Optional.of(checkout));
        MercadoPagoWebhookProcessor processor = new MercadoPagoWebhookProcessor(client, checkouts, subscriptions,
            mock(MercadoPagoWebhookEventRepository.class), service);
        processor.process("req", "subscription_preapproval", "pre-1");
        verifyNoInteractions(invoices, attempts);
    }

    @Test void failedFinancialPersistenceDoesNotRecordSuccessfulDelivery() {
        MercadoPagoWebhookEventRepository events = mock(MercadoPagoWebhookEventRepository.class);
        MercadoPagoWebhookProcessor processor = new MercadoPagoWebhookProcessor(client, mock(BillingCheckoutRepository.class), subscriptions, events, service);
        doThrow(new DataIntegrityViolationException("unexpected")).when(attempts).saveAndFlush(any());
        assertThatThrownBy(() -> processor.process("req", "payment", "pay-1")).isInstanceOf(DataIntegrityViolationException.class);
        // E2: the RECEIVED row may be written (and is rolled back with the transaction), but no attempt is ever
        // recorded as concluded when financial persistence fails.
        org.mockito.ArgumentCaptor<com.localuz.domain.MercadoPagoWebhookEvent> saved = org.mockito.ArgumentCaptor.forClass(com.localuz.domain.MercadoPagoWebhookEvent.class);
        verify(events, atMost(1)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(event -> assertThat(event.getProcessingStatus())
            .isEqualTo(com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus.RECEIVED));
    }

    @Test void paymentCompetencyIsDerivedFromSubscriptionSequenceAndStartDate() {
        temporalSnapshot("pay-1", NOW, 1);
        assertThat(service.ingest("payment", "pay-1")).isTrue();
        assertThat(invoice().getPeriodStart()).isEqualTo(Instant.parse("2026-02-01T02:30:00Z"));
        assertThat(invoice().getPeriodEnd()).isEqualTo(Instant.parse("2026-03-01T02:30:00Z"));
        assertThat(invoice().getDueAt()).isEqualTo(invoice().getPeriodStart());
        assertThat(invoice().getGracePeriodEnd()).isEqualTo(invoice().getDueAt().plus(Duration.ofHours(72)));
        verify(subscriptions, never()).save(any());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(NOW.plusSeconds(100));
    }

    @Test void chargeCompetencyIsDerivedFromDebitDate() {
        subscriptionAuthorizedPaymentTemporalSnapshot("2026-01-31T23:30:00-03:00", NOW);
        assertThat(service.ingest("subscription_authorized_payment", "charge-1")).isTrue();
        assertThat(invoice().getPeriodStart()).isEqualTo(Instant.parse("2026-02-01T02:30:00Z"));
        assertThat(invoice().getPeriodEnd()).isEqualTo(Instant.parse("2026-03-01T02:30:00Z"));
    }

    @Test void temporalReplayAndRetryDoNotMoveFrozenDates() {
        temporalSnapshot("pay-1", NOW, 1);
        service.ingest("payment", "pay-1");
        Instant end = invoice().getPeriodEnd();
        Instant grace = invoice().getGracePeriodEnd();
        temporalSnapshot("pay-1", NOW.plusMillis(1), 1);
        service.ingest("payment", "pay-1");
        assertThat(storedInvoices).hasSize(1);
        assertThat(storedAttempts).hasSize(1);
        assertThat(invoice().getPeriodEnd()).isEqualTo(end);
        assertThat(invoice().getGracePeriodEnd()).isEqualTo(grace);
    }

    @Test void competencyConflictIsLoggedWithoutMovingFrozenDatesWhenStartDateChanges() {
        temporalSnapshot("pay-1", NOW, 1);
        service.ingest("payment", "pay-1");
        Instant end = invoice().getPeriodEnd();
        subscription.setStartDate(subscription.getStartDate().plusSeconds(3600));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MercadoPagoFinancialIngestion.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> log = new ch.qos.logback.core.read.ListAppender<>();
        log.start();
        logger.addAppender(log);
        try {
            when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "approved", "accredited",
                new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), NOW.minusSeconds(1), NOW.plusMillis(1), "ref", null, "pre-1", 1));
            service.ingest("payment", "pay-1");
            assertThat(log.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("reason=competency_mismatch"));
        } finally {
            logger.detachAppender(log);
        }
        assertThat(storedInvoices).hasSize(1);
        assertThat(invoice().getPeriodEnd()).isEqualTo(end);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"16.00,BRL", "15.90,USD"})
    void temporalEnrichmentDoesNotOverrideFinancialMismatch(String amount, String currency) {
        // A first, correct-money PENDING observation establishes the invoice's authoritative amount
        // (a fresh invoice always inherits the observing payment's own amount, so a mismatch can only
        // be reproduced against an ALREADY-established invoice, never on first creation); the second
        // observation (same payment id) then tries to buy PAID with mismatched money and must not.
        subscription.setStartDate(Instant.parse("2026-02-01T02:30:00Z"));
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null,
            NOW.minusSeconds(1000), NOW.plusSeconds(1000), NOW, 1, "months"));
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "pending", "accredited",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), null, NOW.minusSeconds(5), "ref", null, "pre-1", 1));
        service.ingest("payment", "pay-1");
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "approved", "accredited",
            new BigDecimal(amount), currency, NOW.minusSeconds(20), NOW.minusSeconds(1), NOW, "ref", null, "pre-1", 1));
        service.ingest("payment", "pay-1");
        assertThat(invoice().getPeriodEnd()).isNotNull();
        assertThat(invoice().getStatus()).isNotEqualTo(BillingInvoiceStatus.PAID);
        assertThat(invoice().getPaidAt()).isNull();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"approved,15.90,BRL,true", "approved,16.00,BRL,false", "approved,15.90,USD,false", "rejected,15.90,BRL,false"})
    void financialCoverageConsumesAuthoritativeIngestionWithoutNewProviderCalls(String status, String amount, String currency, boolean covered) {
        // Anchored at NOW itself (not the other temporal tests' fixed 2026-02 anchor) so the
        // resulting NOW..NOW+1month period actually contains the coverage clock (NOW+1h).
        subscription.setStartDate(NOW);
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null,
            NOW.minusSeconds(1000), NOW.plusSeconds(1000), NOW, 1, "months"));
        // Same reasoning as temporalEnrichmentDoesNotOverrideFinancialMismatch: establish the
        // invoice's amount/currency first, so the parametrized mismatch cases are reachable at all.
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", "pending", "accredited",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), null, NOW.minusSeconds(5), "ref", null, "pre-1", 1));
        service.ingest("payment", "pay-1");
        when(client.getPayment("pay-1")).thenReturn(new MercadoPagoPayment("pay-1", status, "accredited",
            new BigDecimal(amount), currency, NOW.minusSeconds(20), "approved".equals(status) ? NOW.minusSeconds(1) : null, NOW, "ref", null, "pre-1", 1));
        service.ingest("payment", "pay-1");
        when(invoices.findBySubscriptionIdOrderByPeriodStartAsc(1L)).thenReturn(new ArrayList<>(storedInvoices.values()));
        when(attempts.findByBillingInvoiceSubscriptionId(1L)).thenReturn(new ArrayList<>(storedAttempts.values()));
        clearInvocations(client, subscriptions);
        var coverage = new SubscriptionFinancialCoverageService(invoices, attempts,
            java.time.Clock.fixed(NOW.plusSeconds(3600), java.time.ZoneOffset.UTC));
        assertThat(coverage.evaluate(subscription).covered()).isEqualTo(covered);
        verifyNoInteractions(client, subscriptions);
    }

    /** Anchors subscription.startDate so subscriptionSequenceNumber=1 yields the exact same
     * 2026-02-01T02:30:00Z / 2026-03-01T02:30:00Z period the original debit_date-based fixture used. */
    private void temporalSnapshot(String paymentId, Instant updated, int sequence) {
        subscription.setStartDate(Instant.parse("2026-02-01T02:30:00Z"));
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null,
            NOW.minusSeconds(1000), NOW.plusSeconds(1000), NOW, 1, "months"));
        when(client.getPayment(paymentId)).thenReturn(new MercadoPagoPayment(paymentId, "approved", "accredited",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(20), NOW.minusSeconds(1), updated, "ref", null, "pre-1", sequence));
    }

    private void subscriptionAuthorizedPaymentTemporalSnapshot(String debit, Instant updated) {
        java.time.OffsetDateTime offset = java.time.OffsetDateTime.parse(debit);
        MercadoPagoAuthorizedPayment charge = new MercadoPagoAuthorizedPayment("charge-1", "processed", "pre-1", "approved", "pay-1",
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(500), updated, offset.toInstant(), "ref", offset);
        when(client.getAuthorizedPayment("charge-1")).thenReturn(charge);
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null,
            NOW.minusSeconds(1000), NOW.plusSeconds(1000), NOW, 1, "months"));
    }

    private void snapshot(String paymentId, String status, String amount, String currency, Instant updated) {
        when(client.getPayment(paymentId)).thenReturn(new MercadoPagoPayment(paymentId, status, "accredited", new BigDecimal(amount),
            currency, NOW.minusSeconds(20), "approved".equals(status) ? NOW.minusSeconds(1) : null, updated, "ref", null, "pre-1", null));
    }

    private void chargeSnapshot(String chargeId, String paymentId, String status, Instant updated) {
        MercadoPagoAuthorizedPayment authorizedPayment = charge(chargeId, paymentId, updated);
        when(client.getAuthorizedPayment(chargeId)).thenReturn(authorizedPayment);
        when(client.getPayment(paymentId)).thenReturn(new MercadoPagoPayment(paymentId, status, "accredited", new BigDecimal("15.90"),
            "BRL", NOW.minusSeconds(20), "approved".equals(status) ? NOW.minusSeconds(1) : null, updated, "ref", null, "pre-1", null));
    }

    private MercadoPagoAuthorizedPayment charge(String id, String paymentId, Instant updated) {
        return new MercadoPagoAuthorizedPayment(id, "processed", "pre-1", "approved", paymentId,
            new BigDecimal("15.90"), "BRL", NOW.minusSeconds(30), updated, NOW.minusSeconds(10), "ref");
    }

    private BillingInvoice invoice() { return storedInvoices.values().iterator().next(); }
    private PaymentAttempt attempt() { return storedAttempts.values().iterator().next(); }
}
