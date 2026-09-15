package com.localuz.service;

import static com.localuz.service.FinancialCoverageFixture.*;
import static com.localuz.service.dto.FinancialCoverageEvaluation.Reason.*;
import static com.localuz.service.dto.RecurringReconciliationResult.Outcome.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localuz.config.*;
import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import com.localuz.service.dto.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real scheduler, ingestion, temporal enrichment and coverage; only I/O boundaries are mocked. */
class RecurringBillingLifecycleTest {
    private final FinancialCoverageFixture f = new FinancialCoverageFixture();
    private final MercadoPagoClient client = mock(MercadoPagoClient.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final RecurringBillingReservationService reservations = mock(RecurringBillingReservationService.class);
    private final RecurringBillingReconciliationProperties config = new RecurringBillingReconciliationProperties();
    private final MercadoPagoFinancialIngestion ingestion = new MercadoPagoFinancialIngestion(client, subscriptions,
        f.invoices, f.attempts, new MercadoPagoBillingStatusMapper());
    private final RecurringBillingReservationService.Candidate candidate = new RecurringBillingReservationService.Candidate(1L, "pre-1");
    private final RecurringBillingReconciliationService reconciliation = new RecurringBillingReconciliationService(
        client, ingestion, reservations, config, f.clock);

    @BeforeEach void setup() {
        f.subscription.setExternalSubscriptionId("pre-1");
        when(subscriptions.findForFinancialIngestion("MERCADO_PAGO", "pre-1")).thenReturn(Optional.of(f.subscription));
        when(reservations.reserve(any(), any(), any())).thenReturn(true);
        when(reservations.candidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(f.invoices.findByProviderAndExternalAuthorizedPaymentId(anyString(), anyString())).thenAnswer(c ->
            f.history.stream().filter(i -> Objects.equals(i.getExternalAuthorizedPaymentId(), c.getArgument(1))).findFirst());
        when(f.invoices.saveAndFlush(any())).thenAnswer(c -> {
            BillingInvoice i = c.getArgument(0);
            if (i.getId() == null) { i.setId((long) f.history.size() + 1); f.history.add(i); }
            return i;
        });
        when(f.attempts.findByProviderAndExternalAttemptId(anyString(), anyString())).thenAnswer(c ->
            f.payments.stream().filter(a -> Objects.equals(a.getExternalAttemptId(), c.getArgument(1))).findFirst());
        when(f.attempts.findByProviderAndExternalPaymentId(anyString(), anyString())).thenAnswer(c ->
            f.payments.stream().filter(a -> Objects.equals(a.getExternalPaymentId(), c.getArgument(1))).findFirst());
        when(f.attempts.findByBillingInvoiceIdOrderByIdAsc(anyLong())).thenAnswer(c ->
            f.payments.stream().filter(a -> a.getBillingInvoice().getId().equals(c.getArgument(0))).toList());
        when(f.attempts.saveAndFlush(any())).thenAnswer(c -> {
            PaymentAttempt a = c.getArgument(0);
            if (a.getId() == null) { a.setId((long) f.payments.size() + 1); f.payments.add(a); }
            return a;
        });
        when(client.getPreapproval("pre-1")).thenReturn(new MercadoPagoPreapproval("pre-1", "authorized", "ref", null,
            OCT, DEC, NOV, 1, "months"));
        discover("charge-1");
        snapshot("charge-1", NOV, "approved", NOV);
    }

    @Test void schedulerRecoversMissingFirstWebhookAndEffectivePaidPlan() {
        config.setEnabled(true);
        MercadoPagoProperties provider = new MercadoPagoProperties();
        provider.setEnabled(true); provider.setAccessToken("fake-test-token");
        new RecurringBillingReconciliationScheduler(config, provider, reservations, reconciliation, f.clock).reconcileSubscriptions();
        assertThat(f.history).singleElement().satisfies(i -> {
            assertThat(i.getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
            assertThat(i.getPeriodStart()).isEqualTo(NOV);
            assertThat(i.getPeriodEnd()).isEqualTo(DEC);
        });
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(PAID);
        Plan bronze = new Plan(); bronze.setCode(PlanCode.BRONZE); f.subscription.setPlan(bronze);
        User user = new User(); user.setId(10L);
        when(subscriptions.findByUserIdOrderByStartDateDesc(10L)).thenReturn(List.of(f.subscription));
        var effective = new SubscriptionService(subscriptions, mock(PlanRepository.class), f.service, f.clock);
        assertThat(effective.getEffectivePlan(user).getCode()).isEqualTo(PlanCode.BRONZE);
        verify(subscriptions, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "rejected"})
    void authorizedWithoutFirstApprovedPaymentIsAwaitingPayment(String status) {
        snapshot("charge-1", NOV, status, NOV);
        assertThat(run().outcome()).isEqualTo(COMPLETE);
        var result = f.service.evaluate(f.subscription);
        assertThat(result.covered()).isFalse();
        assertThat(result.commercialState()).isEqualTo(FinancialCoverageEvaluation.CommercialState.AWAITING_PAYMENT);
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "approved"})
    void discoversLostRenewalAndLetsExistingCoverageDecideGrace(String status) {
        snapshot("previous", OCT, "approved", OCT);
        ingestion.ingest("subscription_authorized_payment", "previous");
        snapshot("charge-1", NOV, status, NOV);
        assertThat(run().outcome()).isEqualTo(COMPLETE);
        assertThat(f.history).hasSize(2);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(status.equals("approved") ? PAID : GRACE);
    }

    @Test void lostRecoveryUpdatesSameCompetencyAndAttempt() {
        snapshot("charge-1", NOV, "rejected", NOV);
        run();
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        snapshot("charge-1", NOV, "approved", NOV.plusSeconds(50));
        run();
        assertThat(f.history).hasSize(1); assertThat(f.payments).hasSize(1);
        assertThat(f.history.get(0).getPeriodEnd()).isEqualTo(DEC);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(PAID);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void webhookAndReconciliationReplaySameFactInEitherOrder(boolean webhookFirst) {
        var webhook = new MercadoPagoWebhookProcessor(client, mock(BillingCheckoutRepository.class), subscriptions,
            mock(MercadoPagoWebhookEventRepository.class), ingestion);
        if (webhookFirst) webhook.process("request", "subscription_authorized_payment", "charge-1");
        assertThat(run().outcome()).isEqualTo(COMPLETE);
        if (!webhookFirst) webhook.process("request", "subscription_authorized_payment", "charge-1");
        assertThat(f.history).hasSize(1); assertThat(f.payments).hasSize(1);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(PAID);
    }

    @Test void mismatchingAuthoritativeOwnerStopsBeforeOtherFetchesOrWrites() {
        when(client.getAuthorizedPayment("charge-1")).thenReturn(new MercadoPagoAuthorizedPayment("charge-1", "processed",
            "other-pre", "approved", "other-pay", BigDecimal.TEN, "BRL", NOV, NOV, NOV, "ref"));
        assertThat(run().outcome()).isEqualTo(CORRELATION_MISMATCH);
        verify(client, never()).getPayment(anyString()); verify(client, never()).getPreapproval(anyString());
        assertThat(f.history).isEmpty(); assertThat(f.payments).isEmpty();
    }

    @Test void budgetCountsDetailPaymentAndContractIndividuallyBeforeAnyPersistence() {
        var result = reconciliation.reconcile(candidate, new RecurringReconciliationBudget(3));
        assertThat(result.outcome()).isEqualTo(DISCOVERY_INCOMPLETE);
        assertThat(result.httpCalls()).isEqualTo(3);
        verify(client, never()).getPreapproval(anyString());
        assertThat(f.history).isEmpty(); assertThat(f.payments).isEmpty();
    }

    @Test void canceledSubscriptionKeepsPaidPeriodWithoutReactivationOrNewGrace() {
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(NOV.plusSeconds(1));
        run();
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(f.service.evaluate(f.subscription).covered()).isTrue();
        assertThat(f.service.evaluate(f.subscription, DEC).covered()).isFalse();
        verify(subscriptions, never()).save(any());
    }

    @Test void lateExpiredPaymentDoesNotGrantCurrentCoverage() {
        snapshot("charge-1", OCT, "approved", NOV);
        run();
        assertThat(f.history.get(0).getStatus()).isEqualTo(BillingInvoiceStatus.PAID);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void providerFailureDoesNotErasePreviousPayment() {
        run();
        when(client.getAuthorizedPayment("charge-1")).thenThrow(new MercadoPagoException("timeout", true));
        assertThat(run().outcome()).isEqualTo(PROVIDER_FAILURE);
        assertThat(f.history).hasSize(1); assertThat(f.payments).hasSize(1);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(PAID);
    }

    private RecurringReconciliationResult run() { return reconciliation.reconcile(candidate, new RecurringReconciliationBudget(100)); }

    private void discover(String... ids) {
        when(client.searchAuthorizedPayments(eq("pre-1"), eq(0), anyInt())).thenAnswer(c ->
            new MercadoPagoAuthorizedPaymentPage(0, c.getArgument(2), ids.length, List.of(ids)));
    }

    private void snapshot(String id, Instant debit, String status, Instant updated) {
        when(client.getAuthorizedPayment(id)).thenReturn(new MercadoPagoAuthorizedPayment(id, "processed", "pre-1", status,
            "pay-" + id, new BigDecimal("29.90"), "BRL", debit, updated, debit, "ref", debit.atOffset(ZoneOffset.UTC)));
        when(client.getPayment("pay-" + id)).thenReturn(new MercadoPagoPayment("pay-" + id, status, "accredited",
            new BigDecimal("29.90"), "BRL", debit, status.equals("approved") ? debit : null, updated, "ref", null));
    }
}
