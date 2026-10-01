package com.localuz.domain.enumeration;

/**
 * Lifecycle of one Mercado Pago webhook delivery (request_id + event_type + resource_id).
 * PROCESSED and IGNORED_FINAL are terminal: a repeated delivery is acknowledged without any new
 * effect. RECEIVED (stored, never finished - e.g. a crash mid-processing) and RETRYABLE (a transient
 * condition or provider failure) are re-processed on the same row when the delivery arrives again.
 */
public enum MercadoPagoWebhookProcessingStatus {
    RECEIVED, PROCESSED, IGNORED_FINAL, RETRYABLE;

    public boolean isTerminal() {
        return this == PROCESSED || this == IGNORED_FINAL;
    }
}
