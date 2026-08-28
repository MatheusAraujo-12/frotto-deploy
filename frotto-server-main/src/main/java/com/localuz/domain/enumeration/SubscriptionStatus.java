package com.localuz.domain.enumeration;

/**
 * The SubscriptionStatus enumeration.
 *
 * Represents lifecycle states of a paid subscription only. There is no FREE value here:
 * a user with no Subscription row is implicitly on the FREE plan (see EntitlementService).
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    PAUSED,
    CANCELED,
    EXPIRED,
}
