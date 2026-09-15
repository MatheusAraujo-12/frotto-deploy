package com.localuz.service.dto;

import java.time.Instant;

/** Read-only commercial projection. These enums are not persisted or exposed by a new public API. */
public record FinancialCoverageEvaluation(
    boolean covered,
    CommercialState commercialState,
    Long relevantInvoiceId,
    Instant coverageStart,
    Instant coverageEnd,
    Instant gracePeriodEnd,
    Reason reason
) {
    public enum CommercialState { AWAITING_PAYMENT, ACTIVE, PAST_DUE, EXPIRED, CANCELED, PAUSED, UNRESOLVED }
    public enum Reason {
        PAID, GRACE, NO_INVOICE, NOT_PAYMENT_PROVIDER, INCOMPLETE_PERIOD, AMBIGUOUS_PERIOD,
        FUTURE_PERIOD, PERIOD_EXPIRED, NO_APPROVED_PAYMENT, REVERSED_OR_CANCELED,
        NO_CONTIGUOUS_PAID_PREDECESSOR, GRACE_EXPIRED, RENEWAL_STOPPED, FINANCIAL_CONFLICT
    }
}
