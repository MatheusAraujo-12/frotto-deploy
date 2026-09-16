package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RecurringReconciliationCircuitBreakerTest {
    private final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    private final MutableClock clock = new MutableClock(now);
    private final RecurringReconciliationCircuitBreaker breaker = new RecurringReconciliationCircuitBreaker(clock);

    @Test void closedByDefault() {
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.cooldownUntil()).isNull();
    }

    @Test void opensForTheRequestedDuration() {
        breaker.openFor(Duration.ofMinutes(15));
        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.cooldownUntil()).isEqualTo(now.plusSeconds(900));
    }

    @Test void staysOpenUntilOneSecondBeforeTheDeadlineThenClosesAtAndAfterIt() {
        breaker.openFor(Duration.ofSeconds(60));
        clock.set(now.plusSeconds(59));
        assertThat(breaker.isOpen()).isTrue();
        clock.set(now.plusSeconds(60)); // deadline itself: no longer open (isBefore is exclusive at the boundary)
        assertThat(breaker.isOpen()).isFalse();
        clock.set(now.plusSeconds(61));
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test void ignoresNullNegativeAndZeroDurations() {
        breaker.openFor(null);
        breaker.openFor(Duration.ZERO);
        breaker.openFor(Duration.ofSeconds(-5));
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test void repeatedOpenExtendsRatherThanShortensTheCooldown() {
        breaker.openFor(Duration.ofMinutes(15));
        Instant first = breaker.cooldownUntil();
        breaker.openFor(Duration.ofMinutes(5)); // shorter - must not shorten the existing cooldown
        assertThat(breaker.cooldownUntil()).isEqualTo(first);
        breaker.openFor(Duration.ofMinutes(30)); // longer - extends it
        assertThat(breaker.cooldownUntil()).isEqualTo(now.plusSeconds(30 * 60));
    }

    @Test void canReopenAfterClosingNaturally() {
        breaker.openFor(Duration.ofSeconds(60));
        clock.set(now.plusSeconds(61));
        assertThat(breaker.isOpen()).isFalse();
        breaker.openFor(Duration.ofSeconds(30));
        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.cooldownUntil()).isEqualTo(now.plusSeconds(91));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant initial) { this.instant = new AtomicReference<>(initial); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return instant.get(); }
    }
}
