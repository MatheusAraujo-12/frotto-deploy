package com.localuz.service;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.Subscription;
import com.localuz.service.dto.MercadoPagoPayment;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Competency derivation for invoices anchored on a bare Mercado Pago Payment - the Frotto checkout
 * flow (POST /preapproval with status=pending + back_url/init_point, no card_token_id) never
 * produces a queryable AuthorizedPayment charge (see the 5G.9 investigation), so
 * MercadoPagoInvoiceTemporalEnricher's debit_date anchor is not available here.
 *
 * Anchored on point_of_interaction.transaction_data.subscription_sequence.number instead: a
 * documented, integer, offset-free field (1 = the initial charge) rather than the equivalent
 * Payment-side scheduled-date field (transaction_data.billing_date), whose exact format was never
 * confirmed against a real payload and must not be guessed (see 5G.9 report, Fase 6 - "não
 * substitua debit_date por date_created cegamente" applies equally to an unverified date format).
 *
 * The subscription's own startDate is the recurrence anchor. startDate is persisted as a plain
 * Instant (its original provider offset was already discarded when the preapproval was first
 * parsed - see MercadoPagoWebhookProcessor), so month boundaries are computed in UTC: a
 * deterministic, disclosed simplification (same subscription always yields the same boundaries),
 * not a fabricated value. Mirrors MercadoPagoInvoiceTemporalEnricher's STALE/CONFLICT/INCOMPLETE
 * contract so both anchoring strategies fail closed the same way.
 */
final class MercadoPagoPaymentInvoiceTemporalEnricher {
    enum Result { COMPLETE, INCOMPLETE, STALE, CONFLICT }
    private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

    private MercadoPagoPaymentInvoiceTemporalEnricher() {}

    static Result enrich(BillingInvoice invoice, MercadoPagoPayment payment, Subscription subscription, Integer frequency, String frequencyType) {
        Instant storedVersion = invoice.getProviderUpdatedAt();
        if (storedVersion != null && (payment.getDateLastUpdated() == null || payment.getDateLastUpdated().isBefore(storedVersion))) {
            return Result.STALE;
        }
        Integer sequence = payment.getSubscriptionSequenceNumber();
        Instant subscriptionStart = subscription.getStartDate();
        if (sequence == null || sequence < 1 || subscriptionStart == null || frequency == null || frequency <= 0) {
            return Result.INCOMPLETE;
        }
        Instant[] period = computePeriod(subscriptionStart, sequence, frequency, frequencyType);
        if (period == null) return Result.INCOMPLETE;
        Instant start = period[0], end = period[1];
        Instant grace = graceEnd(start);
        if (grace == null) return Result.INCOMPLETE;
        if (differs(invoice.getPeriodStart(), start) || differs(invoice.getDueAt(), start)
            || differs(invoice.getGracePeriodEnd(), grace) || differs(invoice.getPeriodEnd(), end)
            || (invoice.getPeriodEnd() != null && !invoice.getPeriodEnd().isAfter(start))) {
            return Result.CONFLICT;
        }
        // Retain the first observed anchor; a later retry/observation cannot move it.
        if (invoice.getPeriodStart() == null) invoice.setPeriodStart(start);
        if (invoice.getDueAt() == null) invoice.setDueAt(start);
        if (invoice.getGracePeriodEnd() == null) invoice.setGracePeriodEnd(grace);
        if (invoice.getPeriodEnd() == null) invoice.setPeriodEnd(end);
        return Result.COMPLETE;
    }

    /**
     * Shared with MercadoPagoFinancialIngestion, which needs the period boundaries BEFORE a
     * BillingInvoice exists (to look up whether a retry for the same competency already has one) -
     * enrich() cannot be reused there since it mutates an existing invoice. The current Frotto
     * integration produces months; other units require an explicit contract.
     */
    static Instant[] computePeriod(Instant subscriptionStart, int sequence, int frequency, String frequencyType) {
        if (subscriptionStart == null || sequence < 1 || frequency <= 0 || !"months".equals(frequencyType)) return null;
        try {
            OffsetDateTime anchor = subscriptionStart.atOffset(ZoneOffset.UTC);
            Instant start = anchor.plusMonths((long) (sequence - 1) * frequency).toInstant();
            Instant end = anchor.plusMonths((long) sequence * frequency).toInstant();
            return representable(start) && representable(end) && end.isAfter(start) ? new Instant[] { start, end } : null;
        } catch (DateTimeException | ArithmeticException invalid) {
            return null;
        }
    }

    private static Instant graceEnd(Instant due) {
        try {
            Instant end = due.plus(Duration.ofHours(72));
            return representable(due) && representable(end) ? end : null;
        } catch (DateTimeException | ArithmeticException invalid) {
            return null;
        }
    }

    private static boolean representable(Instant value) {
        return !value.isBefore(MIN_DATE) && !value.isAfter(MAX_DATE);
    }

    private static boolean differs(Instant stored, Instant candidate) {
        return stored != null && !stored.equals(candidate);
    }
}
