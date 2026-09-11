package com.localuz.domain.enumeration;

/** Persisted financial state; does not perform transitions. */
public enum PaymentAttemptStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED,
    CANCELED,
    REFUNDED,
    CHARGEDBACK
}
