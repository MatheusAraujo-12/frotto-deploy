package com.localuz.service;

import static com.localuz.service.FinancialCoverageFixture.*;
import static com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState.*;
import static com.localuz.service.dto.FinancialCoverageEvaluation.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class SubscriptionFinancialCoverageServiceTest {
    private final FinancialCoverageFixture f = new FinancialCoverageFixture();

    @Test void activeAuthorizedContractWithoutInvoicesIsAwaitingPayment() {
        var result = f.service.evaluate(f.subscription);
        assertThat(result.covered()).isFalse();
        assertThat(result.commercialState()).isEqualTo(AWAITING_PAYMENT);
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "rejected", "failed", "authorized", "in_process"})
    void firstUnapprovedPaymentNeverGrantsGrace(String providerStatus) {
        BillingInvoice invoice = f.invoice(NOV, DEC, false);
        // An unsupported provider status (failed) produces no approved attempt in 5G.3.
        new MercadoPagoBillingStatusMapper().payment(providerStatus).ifPresent(status -> {
            var attempt = f.approve(invoice);
            attempt.setStatus(status);
        });
        invoice.setStatus(BillingInvoiceStatus.PAST_DUE);
        var result = f.service.evaluate(f.subscription);
        assertThat(result.covered()).isFalse();
        assertThat(result.commercialState()).isEqualTo(AWAITING_PAYMENT);
    }

    @ParameterizedTest @ValueSource(strings = {"start", "end"})
    void paidWithIncompleteCompetencyFailsClosed(String missing) {
        BillingInvoice invoice = f.invoice(NOV, DEC, true);
        if (missing.equals("start")) invoice.setPeriodStart(null); else invoice.setPeriodEnd(null);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(INCOMPLETE_PERIOD);
    }

    @ParameterizedTest @CsvSource({"-1,false", "0,true", "1,true", "2591999999,true", "2592000000,false"})
    void paidCompetencyIsHalfOpen(long millisFromStart, boolean covered) {
        f.invoice(NOV, DEC, true);
        var result = f.service.evaluate(f.subscription, NOV.plusMillis(millisFromStart));
        assertThat(result.covered()).isEqualTo(covered);
        if (covered) {
            assertThat(result.reason()).isEqualTo(PAID);
            assertThat(result.coverageStart()).isEqualTo(NOV);
            assertThat(result.coverageEnd()).isEqualTo(DEC);
            assertThat(result.commercialState()).isEqualTo(ACTIVE);
        }
    }

    @ParameterizedTest @CsvSource({"-1,true", "0,false", "1,false"})
    void renewalGraceEndsAtExactly72Hours(long offsetMillis, boolean covered) {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        var result = f.service.evaluate(f.subscription, renewal.getGracePeriodEnd().plusMillis(offsetMillis));
        assertThat(result.covered()).isEqualTo(covered);
        assertThat(result.commercialState()).isEqualTo(PAST_DUE);
        assertThat(result.reason()).isEqualTo(covered ? GRACE : GRACE_EXPIRED);
        assertThat(renewal.getStatus()).isEqualTo(BillingInvoiceStatus.PENDING);
    }

    @Test void contiguousRenewalInGraceHasExplicitTemporaryCoverage() {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        var result = f.service.evaluate(f.subscription);
        assertThat(result.covered()).isTrue();
        assertThat(result.reason()).isEqualTo(GRACE);
        assertThat(result.relevantInvoiceId()).isEqualTo(renewal.getId());
        assertThat(result.coverageEnd()).isEqualTo(NOV.plus(Duration.ofHours(72)));
    }

    @ParameterizedTest @ValueSource(ints = {24, 96})
    void paymentDuringOrAfterGraceUsesOriginalCompetency(int hours) {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        f.approve(renewal).setApprovedAt(NOV.plus(Duration.ofHours(hours)));
        var result = f.service.evaluate(f.subscription, NOV.plus(Duration.ofHours(hours)));
        assertThat(result.covered()).isTrue();
        assertThat(result.reason()).isEqualTo(PAID);
        assertThat(result.commercialState()).isEqualTo(ACTIVE);
        assertThat(result.coverageStart()).isEqualTo(NOV);
        assertThat(result.coverageEnd()).isEqualTo(DEC);
        assertThat(f.history).hasSize(2);
        assertThat(f.subscription.getCurrentPeriodEnd()).isNull();
    }

    @Test void oldLatePaymentCannotGiveCurrentCoverage() {
        BillingInvoice old = f.invoice(OCT, NOV, true);
        f.payments.get(0).setApprovedAt(DEC);
        assertThat(f.service.evaluate(f.subscription, DEC).covered()).isFalse();
        assertThat(old.getPeriodEnd()).isEqualTo(NOV);
    }

    @ParameterizedTest @ValueSource(strings = {"late", "missing", "boundary"})
    void oldPaymentCannotManufactureHistoricalCoverageForRenewalGrace(String timing) {
        f.invoice(OCT, NOV, true);
        f.payments.get(0).setApprovedAt(timing.equals("missing") ? null : timing.equals("boundary") ? NOV : NOV.plusSeconds(1));
        f.invoice(NOV, DEC, false);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(NO_CONTIGUOUS_PAID_PREDECESSOR);
    }

    @ParameterizedTest @ValueSource(strings = {"amount", "currency"})
    void financialMismatchInRenewalDoesNotBecomeGrace(String mismatch) {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, true);
        var attempt = f.payments.get(1);
        if (mismatch.equals("amount")) attempt.setAmount(BigDecimal.ONE); else attempt.setCurrency("USD");
        renewal.setStatus(BillingInvoiceStatus.PROCESSING);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(FINANCIAL_CONFLICT);
    }

    @Test void oldReplayDoesNotHideNewUnpaidCompetency() {
        BillingInvoice old = f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        old.setUpdatedAt(DEC);
        old.setProviderUpdatedAt(DEC);
        var result = f.service.evaluate(f.subscription, NOV.plus(Duration.ofDays(4)));
        assertThat(result.covered()).isFalse();
        assertThat(result.commercialState()).isEqualTo(PAST_DUE);
        assertThat(result.relevantInvoiceId()).isEqualTo(renewal.getId());
    }

    @Test void twoPaidCompetenciesChooseCurrentAndIgnoreFuture() {
        BillingInvoice current = f.invoice(OCT, NOV, true);
        BillingInvoice future = f.invoice(NOV, DEC, true);
        assertThat(f.service.evaluate(f.subscription, NOV.minusMillis(1)).relevantInvoiceId()).isEqualTo(current.getId());
        assertThat(f.service.evaluate(f.subscription, NOV).relevantInvoiceId()).isEqualTo(future.getId());
    }

    @Test void unknownAnchorBlocksFallbackEvenToPaidInvoice() {
        f.invoice(NOV, DEC, true);
        f.invoice(null, null, false);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(INCOMPLETE_PERIOD);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void newerIncompleteCompetencyCannotBeMaskedByAnOlderPaidOne() {
        f.invoice(OCT, DEC, true);
        f.invoice(NOV, null, false);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(INCOMPLETE_PERIOD);
    }

    @Test void provablyFutureIncompleteInvoiceDoesNotReplaceCurrent() {
        f.invoice(OCT, NOV, true);
        f.invoice(NOV, null, false);
        assertThat(f.service.evaluate(f.subscription, NOV.minusMillis(1)).covered()).isTrue();
    }

    @Test void ambiguousSameStartFailsClosed() {
        f.invoice(NOV, DEC, true);
        f.invoice(NOV, DEC.plusSeconds(1), true);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(AMBIGUOUS_PERIOD);
    }

    @Test void gapDeniesInheritedGraceButDoesNotPreventNewPaidCoverage() {
        Instant feb = Instant.parse("2026-02-28T12:00:00Z");
        Instant mar28 = Instant.parse("2026-03-28T12:00:00Z");
        Instant mar31 = Instant.parse("2026-03-31T12:00:00Z");
        Instant apr = Instant.parse("2026-04-30T12:00:00Z");
        f.invoice(feb, mar28, true);
        BillingInvoice renewal = f.invoice(mar31, apr, false);
        var unpaid = f.service.evaluate(f.subscription, mar31.plusSeconds(1));
        assertThat(unpaid.covered()).isFalse();
        assertThat(unpaid.reason()).isEqualTo(NO_CONTIGUOUS_PAID_PREDECESSOR);
        f.approve(renewal);
        var paid = f.service.evaluate(f.subscription, mar31.plusSeconds(1));
        assertThat(paid.covered()).isTrue();
        assertThat(paid.coverageEnd()).isEqualTo(apr);
        assertThat(paid.reason()).isEqualTo(PAID);
    }

    @Test void unpaidMiddleCompetencyCannotChainGraceFromDistantPayment() {
        f.invoice(OCT.minusSeconds(3600), OCT, true);
        f.invoice(OCT, NOV, false);
        f.invoice(NOV, DEC, false);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(NO_CONTIGUOUS_PAID_PREDECESSOR);
    }

    @ParameterizedTest @ValueSource(strings = {"due", "grace"})
    void graceRequiresPersistedDeadlines(String field) {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        if (field.equals("due")) renewal.setDueAt(null); else renewal.setGracePeriodEnd(null);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(INCOMPLETE_PERIOD);
    }

    @ParameterizedTest @ValueSource(ints = {71, 73})
    void malformedStoredGraceIsRejectedByExistingModelValidation(int hours) {
        f.invoice(OCT, NOV, true);
        BillingInvoice renewal = f.invoice(NOV, DEC, false);
        renewal.setGracePeriodEnd(NOV.plus(Duration.ofHours(hours)));
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        assertThat(renewal.getGracePeriodEnd()).isEqualTo(NOV.plus(Duration.ofHours(hours)));
    }

    @ParameterizedTest @EnumSource(value = BillingInvoiceStatus.class, names = {"REFUNDED", "CHARGEDBACK", "CANCELED"})
    void reversalsDoNotBecomeGrace(BillingInvoiceStatus status) {
        f.invoice(OCT, NOV, true);
        f.invoice(NOV, DEC, true).setStatus(status);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(REVERSED_OR_CANCELED);
    }

    @ParameterizedTest @ValueSource(strings = {"amount", "currency", "missing", "rejected", "refund", "chargeback", "wrong_invoice"})
    void paidFlagAloneDoesNotReplaceValidFinancialEvidence(String invalid) {
        f.invoice(NOV, DEC, true);
        var attempt = f.payments.get(0);
        switch (invalid) {
            case "amount": attempt.setAmount(BigDecimal.ONE); break;
            case "currency": attempt.setCurrency("USD"); break;
            case "missing": f.payments.clear(); break;
            case "rejected": attempt.setStatus(PaymentAttemptStatus.REJECTED); break;
            case "refund": attempt.setStatus(PaymentAttemptStatus.REFUNDED); break;
            case "chargeback": attempt.setStatus(PaymentAttemptStatus.CHARGEDBACK); break;
            case "wrong_invoice": attempt.setBillingInvoice(f.invoice(OCT, NOV, false)); break;
            default: throw new AssertionError(invalid);
        }
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void approvedAttemptWithoutValidatedSettlementDoesNotGrantCoverage() {
        f.invoice(NOV, DEC, true).setStatus(BillingInvoiceStatus.PROCESSING);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void cancellationKeepsPaidCompetencyAndStopsAtExclusiveEnd() {
        f.invoice(OCT, NOV, true);
        f.subscription.setStatus(SubscriptionStatus.CANCELED);
        f.subscription.setCanceledAt(OCT.plusSeconds(1));
        assertThat(f.service.evaluate(f.subscription, NOV.minusMillis(1)).covered()).isTrue();
        assertThat(f.service.evaluate(f.subscription, NOV).covered()).isFalse();
        f.invoice(NOV, DEC, true);
        assertThat(f.service.evaluate(f.subscription, NOV).covered()).isFalse();
        assertThat(f.subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test void confirmedCancellationWithActiveLegacyStatusStillPreventsNewRenewal() {
        f.subscription.setCancelAtPeriodEnd(true);
        f.subscription.setCanceledAt(OCT.plusSeconds(1));
        f.invoice(NOV, DEC, true);
        assertThat(f.service.evaluate(f.subscription).reason()).isEqualTo(RENEWAL_STOPPED);
    }

    @Test void renewalStartingAtCancellationBoundaryCannotReactivateContract() {
        f.invoice(OCT, NOV, true);
        f.invoice(NOV, DEC, true);
        f.subscription.setCanceledAt(NOV);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void pendingCancellationPreservesPaidPeriodButNeverCreatesGrace() {
        f.subscription.setCancelAtPeriodEnd(true);
        f.subscription.setCurrentPeriodEnd(NOV);
        f.invoice(OCT, NOV, true);
        assertThat(f.service.evaluate(f.subscription, NOV.minusMillis(1)).covered()).isTrue();
        f.invoice(NOV, DEC, false);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void partialRefundDoesNotInvalidateAnApprovedPaidCompetency() {
        f.invoice(NOV, DEC, true);
        f.payments.get(0).setRefundedAmount(BigDecimal.ONE);
        assertThat(f.service.evaluate(f.subscription).covered()).isTrue();
    }

    @ParameterizedTest @EnumSource(value = SubscriptionStatus.class, names = {"CANCELED", "PAUSED"})
    void stoppedContractHasNoRenewalGrace(SubscriptionStatus status) {
        f.invoice(OCT, NOV, true);
        f.invoice(NOV, DEC, false);
        f.subscription.setStatus(status);
        assertThat(f.service.evaluate(f.subscription).covered()).isFalse();
    }

    @Test void readsNeverSaveAndPersistedEnumRemainsUnchanged() {
        f.invoice(NOV, DEC, true);
        f.service.evaluate(f.subscription);
        f.service.evaluate(f.subscription);
        verify(f.invoices, never()).save(any());
        verify(f.attempts, never()).save(any());
        assertThat(SubscriptionStatus.values()).containsExactly(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.PAUSED, SubscriptionStatus.CANCELED, SubscriptionStatus.EXPIRED);
    }
}
