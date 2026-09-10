package com.localuz.config;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure, stateless decision of whether the auto-reconciliation scheduler should contact Mercado
 * Pago for one BillingCheckout this cycle, based only on its age and the current scheduling
 * window - no persisted "last attempt" state. Deliberately not a Spring bean: it needs no
 * dependencies, so there is no constructor for Spring to misconfigure (see
 * BillingAutoReconciliationScheduler's javadoc on the @Autowired incident this project already
 * hit once).
 *
 * MercadoPagoWebhookEvent was considered as a source of "last attempt" history, but it only
 * records an attempt once MercadoPagoWebhookProcessor#process reaches saveEvent() - a provider
 * GET failure (timeout, 5xx) throws before that point and leaves no row. Using it would mean a
 * struggling provider produces zero recorded attempts, so backoff would never engage in exactly
 * the scenario it exists for. Age + windowBucket needs no such history and can't be defeated that
 * way.
 *
 * windowBucket (the same value BillingAutoReconciliationScheduler uses to build its deterministic
 * requestId) is epochSecond / (intervalMinutes * 60): a pure function of absolute wall-clock time,
 * not of "which tick number this is" - so `windowBucket % N == 0` identifies the same absolute
 * time windows regardless of @Scheduled fixedDelay drift, execution jitter, or restarts, and never
 * fires more often than once every N windows.
 *
 * This policy only ever throttles GET calls to Mercado Pago - it never decides to give up on a
 * checkout. Even a checkout stuck for months keeps being reconciled (at most once every 24h) so
 * MercadoPagoWebhookProcessor - the actual authority - gets a chance to observe a terminal
 * provider state and reconcile normally. Deciding to stop polling and clean up an abandoned
 * preapproval (with an explicit, safe cancellation on the provider) is deferred to a later stage;
 * this class must never mark anything EXPIRED by age alone, since the preapproval could still be
 * legitimately pending on Mercado Pago and completing it later would create a second concurrent
 * subscription if the local checkout had already been freed up for a new one.
 */
final class BillingAutoReconciliationBackoffPolicy {

    static final Duration RECENT_MAX_AGE = Duration.ofHours(1);
    static final Duration STALE_MAX_AGE = Duration.ofHours(24);
    static final Duration VERY_STALE_MAX_AGE = Duration.ofDays(7);
    private static final Duration STALE_BACKOFF = Duration.ofHours(1);
    private static final Duration VERY_STALE_BACKOFF = Duration.ofHours(6);
    private static final Duration ABANDONED_BACKOFF = Duration.ofHours(24);

    enum Decision {
        /** Attempt MercadoPagoWebhookProcessor#process this cycle. */
        RECONCILE,
        /** Backoff window not reached yet for this checkout's age tier - do nothing this cycle. */
        SKIP_BACKOFF,
    }

    private BillingAutoReconciliationBackoffPolicy() {}

    static Decision decide(Instant createdAt, Instant now, long windowBucket, int intervalMinutes) {
        Duration age = Duration.between(createdAt, now);
        if (age.compareTo(RECENT_MAX_AGE) < 0) {
            return Decision.RECONCILE;
        }
        Duration backoff = backoffFor(age);
        long backoffTicks = ceilingTicks(backoff, intervalMinutes);
        return windowBucket % backoffTicks == 0 ? Decision.RECONCILE : Decision.SKIP_BACKOFF;
    }

    private static Duration backoffFor(Duration age) {
        if (age.compareTo(STALE_MAX_AGE) < 0) {
            return STALE_BACKOFF;
        }
        if (age.compareTo(VERY_STALE_MAX_AGE) < 0) {
            return VERY_STALE_BACKOFF;
        }
        return ABANDONED_BACKOFF;
    }

    /** Rounds up so the effective period is never shorter than requested - "at most once per X" must never be violated by a non-dividing interval. */
    private static long ceilingTicks(Duration backoff, int intervalMinutes) {
        long interval = Math.max(1, intervalMinutes);
        long backoffMinutes = backoff.toMinutes();
        return Math.max(1L, (backoffMinutes + interval - 1) / interval);
    }
}
