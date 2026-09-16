package com.localuz.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Instance-local, in-memory (not persisted) rate-limit cooldown gate for
 * RecurringBillingReconciliationScheduler only (5G.7). It is deliberately not wired into
 * checkout, webhook, or any manual/admin reconciliation flow - those must keep working even
 * while the automatic recurring scheduler is backing off from a 429.
 *
 * A singleton Spring bean rather than a static field so tests can construct independent
 * instances; state is intentionally lost on restart (no persistence requirement for 5G.7 - see
 * docs/billing-hardening-5g7.md).
 */
@Component
public class RecurringReconciliationCircuitBreaker {
    private final Clock clock;
    private final AtomicReference<Instant> cooldownUntil = new AtomicReference<>();

    public RecurringReconciliationCircuitBreaker() {
        this(Clock.systemUTC());
    }

    public RecurringReconciliationCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    public boolean isOpen() {
        Instant until = cooldownUntil.get();
        return until != null && clock.instant().isBefore(until);
    }

    /** Extends the cooldown only if the new deadline is later than any cooldown already in effect. */
    public void openFor(Duration cooldown) {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) return;
        Instant candidate = clock.instant().plus(cooldown);
        cooldownUntil.updateAndGet(current -> current == null || candidate.isAfter(current) ? candidate : current);
    }

    public Instant cooldownUntil() {
        return cooldownUntil.get();
    }
}
