package com.localuz.service;

import com.localuz.domain.enumeration.BillingInvoiceStatus;
import com.localuz.domain.enumeration.PaymentAttemptStatus;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Normalizes authoritative snapshots only. Never changes persistence or entitlement. */
@Component
public class MercadoPagoBillingStatusMapper {
    public Optional<PaymentAttemptStatus> payment(String status) {
        switch (normalize(status)) {
            case "pending": return Optional.of(PaymentAttemptStatus.PENDING);
            case "in_process": case "in_mediation": case "authorized":
                return Optional.of(PaymentAttemptStatus.PROCESSING);
            case "approved": return Optional.of(PaymentAttemptStatus.APPROVED);
            case "rejected": return Optional.of(PaymentAttemptStatus.REJECTED);
            case "cancelled": case "canceled": return Optional.of(PaymentAttemptStatus.CANCELED);
            case "refunded": return Optional.of(PaymentAttemptStatus.REFUNDED);
            case "charged_back": return Optional.of(PaymentAttemptStatus.CHARGEDBACK);
            default: return Optional.empty();
        }
    }

    public Optional<BillingInvoiceStatus> unsettledInvoice(String status) {
        switch (normalize(status)) {
            case "scheduled": case "pending": return Optional.of(BillingInvoiceStatus.PENDING);
            case "waiting": case "processing": case "recycling": case "processed":
                // processed/rejected alone does not establish service-period delinquency.
                return Optional.of(BillingInvoiceStatus.PROCESSING);
            case "cancelled": case "canceled": return Optional.of(BillingInvoiceStatus.CANCELED);
            default: return Optional.empty();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
