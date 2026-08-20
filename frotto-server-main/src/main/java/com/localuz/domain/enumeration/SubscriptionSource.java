package com.localuz.domain.enumeration;

/**
 * The SubscriptionSource enumeration.
 *
 * Where a Subscription came from. No FREE value here, for the same reason there is no FREE
 * value in SubscriptionStatus: a user with no ACTIVE/PAST_DUE Subscription row is implicitly
 * on FREE (see EntitlementService/SubscriptionService) - storing "FREE" as a source would
 * imply a Subscription row exists for every free user, which the rest of the Billing design
 * deliberately avoids.
 *
 * A user can validly have more than one current row at once (e.g. a temporary ADMIN_GRANT on
 * top of a PAYMENT_PROVIDER subscription); SubscriptionService#getCurrentSubscription picks
 * the effective one by an explicit source priority (ADMIN_GRANT > PAYMENT_PROVIDER >
 * GRANDFATHERED), not by which one started most recently.
 */
public enum SubscriptionSource {
    /** Real payment gateway (not implemented yet). */
    PAYMENT_PROVIDER,
    /** Manually granted by an admin via POST /api/admin/billing/grants - see SubscriptionAdminService. */
    ADMIN_GRANT,
    /** Backfilled for a pre-Billing user so enforcement doesn't break their existing fleet - see GrandfatheringService. */
    GRANDFATHERED,
}
