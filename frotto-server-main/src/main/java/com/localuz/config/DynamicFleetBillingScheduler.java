package com.localuz.config;

import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.DynamicFleetBillingService;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DynamicFleetBillingScheduler {
    private final SubscriptionRepository subscriptions;
    private final DynamicFleetBillingService service;
    private final MercadoPagoProperties provider;
    @Value("${billing.dynamic-fleet.enabled:false}")
    private boolean enabled;
    @Value("${billing.dynamic-fleet.max-per-run:500}")
    private int maxPerRun = 500;
    private long afterId;

    public DynamicFleetBillingScheduler(SubscriptionRepository subscriptions, DynamicFleetBillingService service, MercadoPagoProperties provider) {
        this.subscriptions = subscriptions; this.service = service; this.provider = provider;
    }
    @Scheduled(fixedDelayString = "${billing.dynamic-fleet.interval-ms:900000}")
    public void closeRenewals() {
        if (!enabled || !provider.isEnabled() || !provider.hasAccessToken()) return;
        int remaining = Math.max(1, maxPerRun);
        while (remaining > 0) {
            int size = Math.min(remaining, 100);
            var candidates = subscriptions.findFleetRenewalCandidates(afterId, PageRequest.of(0, size));
            for (var s : candidates) {
                try { service.reconcile(s, Instant.now()); }
                catch (com.localuz.service.MercadoPagoException failure) {
                    if (failure.getCategory() == com.localuz.service.MercadoPagoException.Category.HTTP_429) {
                        LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal outcome=RATE_LIMITED; retry next scheduled run");
                        return;
                    }
                }
                catch (RuntimeException failure) {
                    LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal subscriptionId={} outcome=RECONCILIATION_FAILURE", s.getId());
                }
                afterId = s.getId();
                remaining--;
            }
            if (candidates.size() < size) { afterId = 0; return; }
        }
    }
}
