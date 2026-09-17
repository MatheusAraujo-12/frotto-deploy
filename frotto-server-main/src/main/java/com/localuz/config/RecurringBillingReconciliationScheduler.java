package com.localuz.config;

import com.localuz.service.RecurringBillingReconciliationService;
import com.localuz.service.RecurringBillingReservationService;
import com.localuz.service.RecurringReconciliationBudget;
import com.localuz.service.RecurringReconciliationCircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Financial discovery only; the 5E.3 checkout scheduler remains independent. */
@Component
public class RecurringBillingReconciliationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RecurringBillingReconciliationScheduler.class);
    private final RecurringBillingReconciliationProperties config;
    private final MercadoPagoProperties provider;
    private final RecurringBillingReservationService reservations;
    private final RecurringBillingReconciliationService reconciliation;
    private final RecurringReconciliationCircuitBreaker breaker;
    private final Clock clock;

    @Autowired
    public RecurringBillingReconciliationScheduler(RecurringBillingReconciliationProperties config, MercadoPagoProperties provider,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationService reconciliation,
        RecurringReconciliationCircuitBreaker breaker) {
        this(config, provider, reservations, reconciliation, breaker, Clock.systemUTC());
    }
    public RecurringBillingReconciliationScheduler(RecurringBillingReconciliationProperties config, MercadoPagoProperties provider,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationService reconciliation,
        RecurringReconciliationCircuitBreaker breaker, Clock clock) {
        this.config = config; this.provider = provider; this.reservations = reservations; this.reconciliation = reconciliation;
        this.breaker = breaker; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${billing.recurring-reconciliation.interval-minutes:15}", timeUnit = TimeUnit.MINUTES)
    public void reconcileSubscriptions() {
        if (!config.isEnabled() || !provider.isEnabled() || !provider.hasAccessToken()) return;
        config.validate();
        if (breaker.isOpen()) {
            LOG.info("Recurring financial reconciliation outcome=SKIPPED_BACKOFF cooldownUntil={}", breaker.cooldownUntil());
            return;
        }
        var budget = new RecurringReconciliationBudget(config.getMaxHttpCalls());
        try {
            var now = clock.instant();
            var candidates = reservations.candidates(now.minus(config.getMinIntervalMinutes(), ChronoUnit.MINUTES), now,
                config.getCancelledTerminalHorizonDays(), config.getBatchSize());
            for (var candidate : candidates.stream().limit(config.getBatchSize()).toList()) {
                if (!budget.available()) break;
                try {
                    var result = reconciliation.reconcile(candidate, budget);
                    // failureCategory/failureHttpStatus are a safe enum name and a plain HTTP status code only -
                    // never the exception message, provider body, or Authorization header. See MercadoPagoException.Category.
                    // providerErrorCode is Mercado Pago's own short error/cause code (e.g. "bad_request"), already
                    // filtered to a safe enum-like shape by MercadoPagoException.getSafeProviderErrorCode(); it is
                    // null whenever the provider code did not match that shape, so nothing unsanitized is ever logged.
                    LOG.info("Recurring financial reconciliation subscriptionId={} outcome={} discovered={} ingested={} skipped={} httpCalls={} failureCategory={} failureHttpStatus={} providerErrorCode={}",
                        result.subscriptionId(), result.outcome(), result.discovered(), result.ingested(), result.skipped(), result.httpCalls(),
                        result.failureCategory(), result.failureHttpStatus(), result.providerErrorCode());
                } catch (RuntimeException failure) {
                    // Never log exception messages or bodies that may contain provider data/secrets.
                    LOG.warn("Recurring financial reconciliation subscriptionId={} outcome=OPERATIONAL_FAILURE", candidate.id());
                }
            }
            if (budget.rateLimited()) {
                Duration cooldown = cooldownFor(budget.retryAfterSeconds());
                breaker.openFor(cooldown);
                LOG.warn("Recurring financial reconciliation outcome=RATE_LIMITED cooldownSeconds={} cooldownUntil={}",
                    cooldown.toSeconds(), breaker.cooldownUntil());
            }
        } catch (RuntimeException failure) {
            LOG.warn("Recurring financial reconciliation outcome=CANDIDATE_SELECTION_FAILURE");
        }
    }

    /** Respects a valid provider Retry-After, clamped to the configured ceiling; falls back to the configured default cooldown otherwise. */
    private Duration cooldownFor(Integer retryAfterSeconds) {
        long maxSeconds = config.getRateLimitMaxCooldownSeconds();
        long seconds = retryAfterSeconds != null && retryAfterSeconds > 0
            ? Math.min(retryAfterSeconds, maxSeconds)
            : Math.min(config.getRateLimitDefaultCooldownSeconds(), maxSeconds);
        return Duration.ofSeconds(seconds);
    }
}
