package com.localuz.domain.enumeration;

/** Persisted financial state; does not perform transitions. */
public enum BillingInvoiceStatus {
    PENDING,
    PROCESSING,
    PAID,
    PAST_DUE,
    REFUNDED,
    CHARGEDBACK,
    CANCELED
}
