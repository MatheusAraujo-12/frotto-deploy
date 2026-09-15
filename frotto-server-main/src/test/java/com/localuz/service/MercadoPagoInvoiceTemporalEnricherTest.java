package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.BillingInvoice;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MercadoPagoInvoiceTemporalEnricherTest {
    private static final Instant VERSION = Instant.parse("2026-09-15T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
        "2026-01-09T14:56:39Z,1,2026-02-09T14:56:39Z",
        "2026-01-31T14:56:39Z,2,2026-03-31T14:56:39Z",
        "2026-01-31T14:56:39Z,1,2026-02-28T14:56:39Z",
        "2024-01-31T14:56:39Z,1,2024-02-29T14:56:39Z",
        "2026-01-31T23:30:00-03:00,1,2026-03-01T02:30:00Z",
        "2026-03-01T00:30:00+14:00,1,2026-03-31T10:30:00Z",
        "2026-10-09T14:56:38.999Z,1,2026-11-09T14:56:38.999Z",
        "2026-10-09T14:56:39.001Z,1,2026-11-09T14:56:39.001Z"
    })
    void derivesCalendarIntervalInOriginalOffset(String debit, int frequency, String expectedEnd) {
        BillingInvoice invoice = new BillingInvoice();
        assertThat(enrich(invoice, debit, frequency, "months")).isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.COMPLETE);
        Instant start = OffsetDateTime.parse(debit).toInstant();
        assertThat(invoice.getPeriodStart()).isEqualTo(start);
        assertThat(invoice.getPeriodEnd()).isEqualTo(Instant.parse(expectedEnd));
        assertThat(invoice.getDueAt()).isEqualTo(start);
        assertThat(invoice.getGracePeriodEnd()).isEqualTo(start.plus(Duration.ofHours(72)));
        assertThat(invoice.isPeriodValid()).isTrue();
    }

    @Test void exactGraceIsIndependentOfCalendarMonth() {
        BillingInvoice invoice = new BillingInvoice();
        enrich(invoice, "2026-10-09T14:56:39Z", 1, "months");
        assertThat(invoice.getGracePeriodEnd()).isEqualTo(Instant.parse("2026-10-12T14:56:39Z"));
        assertThat(Duration.between(invoice.getDueAt(), invoice.getGracePeriodEnd())).isEqualTo(Duration.ofHours(72));
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"years", "days", "MONTHLY", "unknown", ""})
    void unsupportedRecurrenceNeverCompletesPeriod(String type) {
        BillingInvoice invoice = new BillingInvoice();
        assertThat(enrich(invoice, "2026-01-31T23:30:00-03:00", 1, type))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.INCOMPLETE);
        assertThat(invoice.getPeriodEnd()).isNull();
        assertThat(invoice.getDueAt()).isEqualTo(Instant.parse("2026-02-01T02:30:00Z"));
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void missingInvalidOrOverflowingFrequencyLeavesPeriodIncomplete(Integer frequency) {
        BillingInvoice invoice = new BillingInvoice();
        assertThat(enrich(invoice, "2026-01-31T12:00:00Z", frequency, "months"))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.INCOMPLETE);
        assertThat(invoice.getPeriodEnd()).isNull();
    }

    @Test void missingDebitDoesNotUseCreationUpdateOrNextPaymentDates() {
        BillingInvoice invoice = new BillingInvoice();
        assertThat(enrich(invoice, null, 1, "months")).isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.INCOMPLETE);
        assertThat(invoice.getPeriodStart()).isNull();
        assertThat(invoice.getPeriodEnd()).isNull();
        assertThat(invoice.getDueAt()).isNull();
        assertThat(invoice.getGracePeriodEnd()).isNull();
    }

    @Test void instantWithoutOriginalOffsetCannotBecomeACalendarPeriod() {
        BillingInvoice invoice = new BillingInvoice();
        MercadoPagoAuthorizedPayment legacy = new MercadoPagoAuthorizedPayment("charge", "processed", "pre", null, null,
            null, null, VERSION, VERSION, VERSION, null);
        assertThat(MercadoPagoInvoiceTemporalEnricher.enrich(invoice, legacy, contract(1, "months")))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.INCOMPLETE);
        assertThat(invoice.getPeriodEnd()).isNull();
    }

    @Test void replayAndLaterMissingDataPreserveAllDates() {
        BillingInvoice invoice = new BillingInvoice();
        enrich(invoice, "2026-01-31T23:30:00-03:00", 1, "months");
        var dates = dates(invoice);
        enrich(invoice, "2026-01-31T23:30:00-03:00", 1, "months");
        enrich(invoice, null, null, null);
        assertThat(dates(invoice)).containsExactlyElementsOf(dates);
    }

    @ParameterizedTest @CsvSource({"2026-02-02T23:30:00-03:00,1", "2026-01-31T23:30:00-03:00,2"})
    void retryOrContractChangeCannotMoveFrozenCompetence(String newDebit, int frequency) {
        BillingInvoice invoice = new BillingInvoice();
        enrich(invoice, "2026-01-31T23:30:00-03:00", 1, "months");
        var dates = dates(invoice);
        assertThat(enrich(invoice, newDebit, frequency, "months")).isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.CONFLICT);
        assertThat(dates(invoice)).containsExactlyElementsOf(dates);
    }

    @Test void firstAnchorIsRetainedEvenBeforeRecurrenceBecomesKnown() {
        BillingInvoice invoice = new BillingInvoice();
        enrich(invoice, "2026-01-31T23:30:00-03:00", null, null);
        assertThat(enrich(invoice, "2026-02-02T23:30:00-03:00", 1, "months"))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.CONFLICT);
        assertThat(invoice.getPeriodEnd()).isNull();
        assertThat(enrich(invoice, "2026-01-31T23:30:00-03:00", 1, "months"))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.COMPLETE);
        assertThat(invoice.getPeriodEnd()).isEqualTo(Instant.parse("2026-03-01T02:30:00Z"));
    }

    @Test void olderSnapshotCannotEnrichAnIncompleteInvoice() {
        BillingInvoice invoice = new BillingInvoice();
        invoice.setProviderUpdatedAt(VERSION.plusSeconds(1));
        assertThat(enrich(invoice, "2026-01-31T12:00:00Z", 1, "months"))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.STALE);
        assertThat(invoice.getPeriodStart()).isNull();
    }

    @Test void conflictingPartialPeriodIsNotOverwrittenOrCompleted() {
        BillingInvoice invoice = new BillingInvoice();
        invoice.setPeriodEnd(Instant.parse("2026-03-15T00:00:00Z"));
        assertThat(enrich(invoice, "2026-01-31T12:00:00Z", 1, "months"))
            .isEqualTo(MercadoPagoInvoiceTemporalEnricher.Result.CONFLICT);
        assertThat(invoice.getPeriodStart()).isNull();
        assertThat(invoice.getDueAt()).isNull();
        assertThat(invoice.getPeriodEnd()).isEqualTo(Instant.parse("2026-03-15T00:00:00Z"));
    }

    private MercadoPagoInvoiceTemporalEnricher.Result enrich(BillingInvoice invoice, String debit, Integer frequency, String type) {
        OffsetDateTime offset = debit == null ? null : OffsetDateTime.parse(debit);
        MercadoPagoAuthorizedPayment charge = new MercadoPagoAuthorizedPayment("charge", "processed", "pre", null, null,
            null, null, VERSION.minusSeconds(100), VERSION, offset == null ? null : offset.toInstant(), null, offset);
        return MercadoPagoInvoiceTemporalEnricher.enrich(invoice, charge, contract(frequency, type));
    }

    private MercadoPagoPreapproval contract(Integer frequency, String type) {
        return new MercadoPagoPreapproval("pre", "authorized", null, null, VERSION.minusSeconds(200), VERSION.plusSeconds(200),
            VERSION, frequency, type);
    }

    private java.util.List<Instant> dates(BillingInvoice invoice) {
        return java.util.List.of(invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getDueAt(), invoice.getGracePeriodEnd());
    }
}
