package com.localuz.domain.enumeration;

/**
 * 5G.12.1 prorated-upgrade state machine (persisted; transitions live in SubscriptionPlanUpgradeSteps).
 *
 * AWAITING_PAYMENT -> APPLYING         authoritative GET /v1/payments/{id} approved with the exact
 *                                      quoted amount/currency (or no charge was due at all).
 * AWAITING_PAYMENT -> EXPIRED          checkout window closed and the provider shows no approved or
 *                                      still-pending payment for the reference.
 * AWAITING_PAYMENT -> FAILED           the checkout preference was definitively rejected - nothing charged.
 * AWAITING_PAYMENT -> REQUIRES_REVIEW  an approved payment does not match the quoted amount/currency.
 * APPLYING         -> APPLIED          recurrence amount confirmed by an authoritative GET, local plan switched.
 * APPLYING         -> REQUIRES_REVIEW  paid, but the upgrade can no longer be applied safely (the
 *                                      subscription stopped being eligible, the paid cycle already
 *                                      ended, or the recurrence update kept failing). Never silently
 *                                      granted, never silently dropped: needs an operator (refund).
 *
 * Only AWAITING_PAYMENT and APPLYING are "open": at most one open row per subscription, and while one
 * exists no other plan change for that subscription may start.
 */
public enum SubscriptionPlanUpgradeStatus {
    AWAITING_PAYMENT,
    APPLYING,
    APPLIED,
    EXPIRED,
    FAILED,
    REQUIRES_REVIEW;

    public boolean isOpen() {
        return this == AWAITING_PAYMENT || this == APPLYING;
    }
}
