package com.localuz.config;

import com.localuz.service.RecurringBillingReconciliationService;
import com.localuz.service.RecurringBillingReservationService;
import com.localuz.service.RecurringReconciliationBudget;
import java.time.Clock;
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
    private final Clock clock;

    @Autowired
    public RecurringBillingReconciliationScheduler(RecurringBillingReconciliationProperties config, MercadoPagoProperties provider,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationService reconciliation) {
        this(config, provider, reservations, reconciliation, Clock.systemUTC());
    }
    public RecurringBillingReconciliationScheduler(RecurringBillingReconciliationProperties config, MercadoPagoProperties provider,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationService reconciliation, Clock clock) {
        this.config = config; this.provider = provider; this.reservations = reservations; this.reconciliation = reconciliation; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${billing.recurring-reconciliation.interval-minutes:15}", timeUnit = TimeUnit.MINUTES)
    public void reconcileSubscriptions() {
        if (!config.isEnabled() || !provider.isEnabled() || !provider.hasAccessToken()) return;
        config.validate();
        var budget = new RecurringReconciliationBudget(config.getMaxHttpCalls());
        try {
            var candidates = reservations.candidates(clock.instant().minus(config.getMinIntervalMinutes(), ChronoUnit.MINUTES), config.getBatchSize());
            for (var candidate : candidates.stream().limit(config.getBatchSize()).toList()) {
                if (!budget.available()) break;
                try {
                    var result = reconciliation.reconcile(candidate, budget);
                    LOG.info("Recurring financial reconciliation subscriptionId={} outcome={} discovered={} ingested={} skipped={} httpCalls={}",
                        result.subscriptionId(), result.outcome(), result.discovered(), result.ingested(), result.skipped(), result.httpCalls());
                } catch (RuntimeException failure) {
                    // Never log exception messages or bodies that may contain provider data/secrets.
                    LOG.warn("Recurring financial reconciliation subscriptionId={} outcome=OPERATIONAL_FAILURE", candidate.id());
                }
            }
        } catch (RuntimeException failure) {
            LOG.warn("Recurring financial reconciliation outcome=CANDIDATE_SELECTION_FAILURE");
        }
    }
}
