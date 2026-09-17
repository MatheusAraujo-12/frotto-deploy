package com.localuz.service.dto;

public record RecurringReconciliationResult(Long subscriptionId, Outcome outcome, int discovered, int ingested, int skipped, int httpCalls,
    String failureCategory, Integer failureHttpStatus, String providerErrorCode) {
    /** Existing 6-arg call sites are unaffected; failureCategory/failureHttpStatus/providerErrorCode default to null (no failure to report). */
    public RecurringReconciliationResult(Long subscriptionId, Outcome outcome, int discovered, int ingested, int skipped, int httpCalls) {
        this(subscriptionId, outcome, discovered, ingested, skipped, httpCalls, null, null, null);
    }
    /** Existing 8-arg call sites are unaffected; providerErrorCode defaults to null (e.g. non-HTTP failures). */
    public RecurringReconciliationResult(Long subscriptionId, Outcome outcome, int discovered, int ingested, int skipped, int httpCalls,
        String failureCategory, Integer failureHttpStatus) {
        this(subscriptionId, outcome, discovered, ingested, skipped, httpCalls, failureCategory, failureHttpStatus, null);
    }
    public enum Outcome { COMPLETE, DISCOVERY_INCOMPLETE, SKIP_ALREADY_RESERVED, INVALID_CANDIDATE, PROVIDER_FAILURE,
        INVALID_RESPONSE, CORRELATION_MISMATCH, INGESTION_INCONCLUSIVE, PERSISTENCE_FAILURE, RATE_LIMITED, OPERATIONAL_FAILURE }
}
