package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.config.BillingAutoReconciliationBackoffPolicy.Decision;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingAutoReconciliationBackoffPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final int INTERVAL_MINUTES = 15;

    private static long windowBucketAt(Instant now) {
        return now.getEpochSecond() / (INTERVAL_MINUTES * 60L);
    }

    @Test
    void underOneHourAlwaysReconcilesRegardlessOfWindow() {
        Instant createdAt = NOW.minusSeconds(30 * 60);
        for (int i = 0; i < 8; i++) {
            long window = windowBucketAt(NOW) + i;
            assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, window, INTERVAL_MINUTES)).isEqualTo(Decision.RECONCILE);
        }
    }

    @Test
    void exactlyOneHourOldEntersTheHourlyBackoffTier() {
        Instant createdAt = NOW.minus(BillingAutoReconciliationBackoffPolicy.RECENT_MAX_AGE);
        // 60 minutes / 15 minutes per tick = 4 ticks per allowed reconciliation
        long alignedWindow = 4000L; // divisible by 4
        long unalignedWindow = 4001L;
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, alignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.RECONCILE);
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, unalignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.SKIP_BACKOFF);
    }

    @Test
    void betweenOneHourAndOneDayReconcilesAtMostOncePerHour() {
        Instant createdAt = NOW.minusSeconds(5 * 3600); // 5 hours old
        int reconcileCount = 0;
        for (long window = 4000L; window < 4000L + 16; window++) {
            // 16 consecutive 15-minute ticks span exactly 4 hours = 4 aligned windows expected
            if (BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, window, INTERVAL_MINUTES) == Decision.RECONCILE) {
                reconcileCount++;
            }
        }
        assertThat(reconcileCount).isEqualTo(4);
    }

    @Test
    void exactlyOneDayOldEntersTheSixHourlyBackoffTier() {
        Instant createdAt = NOW.minus(BillingAutoReconciliationBackoffPolicy.STALE_MAX_AGE);
        // 360 minutes / 15 minutes per tick = 24 ticks per allowed reconciliation
        long alignedWindow = 4800L; // divisible by 24
        long unalignedWindow = 4801L;
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, alignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.RECONCILE);
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, unalignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.SKIP_BACKOFF);
    }

    @Test
    void betweenOneDayAndSevenDaysReconcilesAtMostOnceEverySixHours() {
        Instant createdAt = NOW.minusSeconds(3 * 24 * 3600L); // 3 days old
        int reconcileCount = 0;
        for (long window = 4800L; window < 4800L + 96; window++) {
            // 96 consecutive 15-minute ticks span exactly 24 hours = 4 aligned 6-hourly windows expected
            if (BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, window, INTERVAL_MINUTES) == Decision.RECONCILE) {
                reconcileCount++;
            }
        }
        assertThat(reconcileCount).isEqualTo(4);
    }

    @Test
    void exactlySevenDaysOldEntersTheDailyBackoffTierInsteadOfExpiring() {
        Instant createdAt = NOW.minus(BillingAutoReconciliationBackoffPolicy.VERY_STALE_MAX_AGE);
        // 1440 minutes / 15 minutes per tick = 96 ticks per allowed reconciliation
        long alignedWindow = 9600L; // divisible by 96
        long unalignedWindow = 9601L;
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, alignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.RECONCILE);
        assertThat(BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, unalignedWindow, INTERVAL_MINUTES)).isEqualTo(Decision.SKIP_BACKOFF);
    }

    @Test
    void wellOverSevenDaysStillReconcilesAtMostOncePerDayAndNeverExpires() {
        Instant createdAt = NOW.minusSeconds(90 * 24 * 3600L); // 90 days old
        int reconcileCount = 0;
        for (long window = 9600L; window < 9600L + 96 * 3; window++) {
            // 3 days of 15-minute ticks = 288 ticks = 3 aligned daily windows expected
            Decision decision = BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, window, INTERVAL_MINUTES);
            assertThat(decision).isIn(Decision.RECONCILE, Decision.SKIP_BACKOFF); // there is no "give up" decision
            if (decision == Decision.RECONCILE) {
                reconcileCount++;
            }
        }
        assertThat(reconcileCount).isEqualTo(3);
    }

    @Test
    void nonDividingIntervalNeverBackoffsShorterThanRequested() {
        // interval=7 does not divide 60 evenly; ceiling division must still guarantee >= 60 minutes between reconciles.
        Instant createdAt = NOW.minusSeconds(5 * 3600);
        int intervalMinutes = 7;
        long firstAligned = -1;
        long secondAligned = -1;
        for (long window = 0; window < 40 && secondAligned == -1; window++) {
            if (BillingAutoReconciliationBackoffPolicy.decide(createdAt, NOW, window, intervalMinutes) == Decision.RECONCILE) {
                if (firstAligned == -1) {
                    firstAligned = window;
                } else {
                    secondAligned = window;
                }
            }
        }
        long minutesBetween = (secondAligned - firstAligned) * intervalMinutes;
        assertThat(minutesBetween).isGreaterThanOrEqualTo(60);
    }
}
