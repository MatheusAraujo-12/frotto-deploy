package com.localuz.service.dto;

public record RecurringReconciliationResult(Long subscriptionId, Outcome outcome, int discovered, int ingested, int skipped, int httpCalls) {
    public enum Outcome { COMPLETE, DISCOVERY_INCOMPLETE, SKIP_ALREADY_RESERVED, INVALID_CANDIDATE, PROVIDER_FAILURE,
        INVALID_RESPONSE, CORRELATION_MISMATCH, INGESTION_INCONCLUSIVE, PERSISTENCE_FAILURE, RATE_LIMITED, OPERATIONAL_FAILURE }
}
