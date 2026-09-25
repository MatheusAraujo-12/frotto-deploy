package com.localuz.config;

import com.localuz.service.SubscriptionPlanUpgradeService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 5G.12.1: safety net for prorated upgrades whose user never came back / whose webhook was lost:
 * discovers payments by external_reference and finishes applying paid attempts. Independent from
 * the recurring financial reconciliation (different resource, different cadence).
 */
@Component
public class PlanUpgradeReconciliationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(PlanUpgradeReconciliationScheduler.class);
    private final MercadoPagoProperties provider;
    private final SubscriptionPlanUpgradeService upgrades;
    private final int batchSize;

    public PlanUpgradeReconciliationScheduler(MercadoPagoProperties provider, SubscriptionPlanUpgradeService upgrades,
        @Value("${billing.plan-upgrade-reconciliation.batch-size:20}") int batchSize) {
        this.provider = provider;
        this.upgrades = upgrades;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Scheduled(fixedDelayString = "${billing.plan-upgrade-reconciliation.interval-seconds:120}", initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void reconcileOpenUpgrades() {
        if (!provider.isEnabled() || !provider.hasAccessToken()) return;
        try {
            int processed = upgrades.reconcileOpenUpgrades(batchSize);
            if (processed > 0) LOG.info("Plan upgrade reconciliation processed={}", processed);
        } catch (RuntimeException failure) {
            // Never log exception messages or bodies that may contain provider data/secrets.
            LOG.warn("Plan upgrade reconciliation outcome=CANDIDATE_SELECTION_FAILURE");
        }
    }
}
