package com.localuz.service;

import com.localuz.domain.BillingInvoice;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Frotto contractual derivation, not service-period fields returned by Mercado Pago. */
final class MercadoPagoInvoiceTemporalEnricher {
    enum Result { COMPLETE, INCOMPLETE, STALE, CONFLICT }
    private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

    private MercadoPagoInvoiceTemporalEnricher() {}

    static Result enrich(BillingInvoice invoice, MercadoPagoAuthorizedPayment charge, MercadoPagoPreapproval contract) {
        Instant storedVersion = invoice.getProviderUpdatedAt();
        if (storedVersion != null && (charge.getLastModified() == null || charge.getLastModified().isBefore(storedVersion))) {
            return Result.STALE;
        }
        OffsetDateTime debit = charge.getDebitDateWithOffset();
        if (debit == null || !debit.toInstant().equals(charge.getDebitDate())) {
            // An Instant alone cannot recover the provider's civil calendar. Preserve known dates.
            if (invoice.getDueAt() != null && invoice.getGracePeriodEnd() == null) {
                Instant grace = graceEnd(invoice.getDueAt());
                if (grace != null) invoice.setGracePeriodEnd(grace);
            }
            return Result.INCOMPLETE;
        }
        Instant start = debit.toInstant();
        Instant grace = graceEnd(start);
        if (!representable(start) || grace == null) return Result.INCOMPLETE;
        Instant end = periodEnd(debit, contract.getFrequency(), contract.getFrequencyType());
        if (differs(invoice.getPeriodStart(), start) || differs(invoice.getDueAt(), start)
            || differs(invoice.getGracePeriodEnd(), grace)
            || (end != null && differs(invoice.getPeriodEnd(), end))
            || (invoice.getPeriodEnd() != null && !invoice.getPeriodEnd().isAfter(start))) {
            return Result.CONFLICT;
        }
        // Retain the first observed anchor even if recurrence is not yet known; retries cannot move it.
        if (invoice.getPeriodStart() == null) invoice.setPeriodStart(start);
        if (invoice.getDueAt() == null) invoice.setDueAt(start);
        if (invoice.getGracePeriodEnd() == null) invoice.setGracePeriodEnd(grace);
        if (end == null) return Result.INCOMPLETE;
        if (invoice.getPeriodEnd() == null) invoice.setPeriodEnd(end);
        return Result.COMPLETE;
    }

    private static Instant periodEnd(OffsetDateTime debit, Integer frequency, String type) {
        // The current Frotto integration produces months. Other units require an explicit contract.
        if (frequency == null || frequency <= 0 || !"months".equals(type)) return null;
        try {
            // Add calendar months in the provider's fixed offset BEFORE converting to Instant.
            Instant end = debit.plusMonths(frequency).toInstant();
            return representable(end) && end.isAfter(debit.toInstant()) ? end : null;
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
