package com.localuz.service.dto;

/**
 * 5G.12.1: explicit outcome of a plan-change request, so the UI never has to infer it from
 * changeType/pending combinations.
 */
public enum PlanChangeStatus {
    /** A prorated charge must be paid (checkoutUrl) before the new plan is granted. Nothing changed yet. */
    UPGRADE_PAYMENT_REQUIRED,
    /** Payment was started or approved and is still being confirmed/applied. The new plan is NOT active yet. */
    UPGRADE_PAYMENT_PENDING,
    /** Payment confirmed (or none due), recurrence confirmed at the new price, new plan active. */
    UPGRADE_APPLIED,
    /** Current plan kept until effectiveAt; the recurrence already carries the lower price. */
    DOWNGRADE_SCHEDULED,
    /** The scheduled downgrade was removed after the provider confirmed the original price. */
    DOWNGRADE_UNDONE,
    /** Paid -> FREE: handled by the existing cancellation flow. */
    CANCELLATION_SCHEDULED
}
