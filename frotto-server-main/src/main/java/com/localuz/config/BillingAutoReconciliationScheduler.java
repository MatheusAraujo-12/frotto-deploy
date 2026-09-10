package com.localuz.config;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.service.MercadoPagoWebhookProcessor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net behind the Mercado Pago webhook: periodically re-reads any local BillingCheckout
 * still stuck in PROVIDER_PENDING/PROVIDER_UNKNOWN and reconciles it through the exact same
 * authoritative path a real webhook delivery uses (MercadoPagoWebhookProcessor#process, which
 * only ever issues GET /preapproval or GET /authorized_payments - never a POST/PUT against
 * /preapproval), so a missed or misdelivered webhook self-heals without anyone re-sending it or
 * running the manual BillingReconciliationRunner. The webhook stays the primary path; this only
 * catches what it misses.
 *
 * Controlled by billing.reconciliation.auto.enabled (default false). Staging can turn it on;
 * production activation is a separate decision, not gated here beyond the flag itself - unlike
 * BillingReconciliationRunner (a manual one-off), this is meant to eventually run continuously,
 * so it isn't restricted to mercadopago.test-mode.
 *
 * Idempotency key: MercadoPagoWebhookEvent dedups on (request_id, event_type, resource_id) - see
 * that entity's unique constraint. A requestId fixed forever per checkout (e.g.
 * "auto-reconcile-42") would dedup itself out of existence after the very first attempt, even if
 * that attempt was IGNORED or the checkout is still PROVIDER_PENDING an hour later - no future
 * tick could ever reconcile it again. Instead the requestId is
 * "auto-reconcile-<checkoutId>-<windowBucket>", where windowBucket is the current time floor-
 * divided by the configured interval: concurrent runs within the same scheduling window (e.g.
 * two app instances ticking at once) collide on purpose and the processor's own dedup makes the
 * second one a no-op, while the next window gets a fresh requestId and can always try again.
 *
 * Every checkout is reconciled independently: a failure (provider error, unexpected exception)
 * for one checkout is caught and logged without aborting the rest of the batch, and the whole
 * method is deliberately NOT @Transactional so that MercadoPagoWebhookProcessor#process keeps
 * owning its own transaction per checkout - see BillingReconciliationRunner's javadoc for why
 * wrapping it in an outer @Transactional here would silently defeat this try/catch via
 * UnexpectedRollbackException. Never creates, cancels, or pays for anything: it only ever reads
 * checkouts already stuck locally and calls the same read-only reconciliation path the webhook
 * uses, so the ADMIN_GRANT > PAYMENT_PROVIDER > GRANDFATHERED precedence in SubscriptionService
 * is inherited unchanged - this class never writes to Subscription itself.
 */
@Component
public class BillingAutoReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingAutoReconciliationScheduler.class);
    private static final String PREAPPROVAL_TOPIC = "subscription_preapproval";
    private static final List<BillingCheckoutStatus> ELIGIBLE_STATUSES = List.of(
        BillingCheckoutStatus.PROVIDER_PENDING,
        BillingCheckoutStatus.PROVIDER_UNKNOWN
    );

    @Value("${billing.reconciliation.auto.enabled:false}")
    private boolean enabled;

    @Value("${billing.reconciliation.auto.interval-minutes:15}")
    private int intervalMinutes;

    @Value("${billing.reconciliation.auto.min-age-minutes:5}")
    private int minAgeMinutes;

    private final MercadoPagoProperties mercadoPagoProperties;
    private final BillingCheckoutRepository checkoutRepository;
    private final MercadoPagoWebhookProcessor processor;
    private final Clock clock;

    public BillingAutoReconciliationScheduler(
        MercadoPagoProperties mercadoPagoProperties,
        BillingCheckoutRepository checkoutRepository,
        MercadoPagoWebhookProcessor processor
    ) {
        this(mercadoPagoProperties, checkoutRepository, processor, Clock.systemUTC());
    }

    BillingAutoReconciliationScheduler(
        MercadoPagoProperties mercadoPagoProperties,
        BillingCheckoutRepository checkoutRepository,
        MercadoPagoWebhookProcessor processor,
        Clock clock
    ) {
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.checkoutRepository = checkoutRepository;
        this.processor = processor;
        this.clock = clock;
    }

    // Not @Transactional on purpose - see this class's javadoc. Each checkout's call into
    // process() must remain the owner of its own transaction so a failure rolls back cleanly
    // instead of silently poisoning a shared one.
    @Scheduled(fixedDelayString = "${billing.reconciliation.auto.interval-minutes:15}", timeUnit = TimeUnit.MINUTES)
    public void reconcilePendingCheckouts() {
        if (!enabled) {
            return;
        }
        if (!mercadoPagoProperties.isEnabled()) {
            log.info("Billing auto-reconciliation: Mercado Pago integration is disabled; skipping this run.");
            return;
        }

        List<BillingCheckout> eligible;
        try {
            Instant cutoff = Instant.now(clock).minus(minAgeMinutes, ChronoUnit.MINUTES);
            eligible = checkoutRepository.findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(ELIGIBLE_STATUSES, cutoff);
        } catch (Exception exception) {
            log.error("Billing auto-reconciliation: failed to list eligible checkouts: {}", exception.getMessage());
            return;
        }

        long windowBucket = Instant.now(clock).getEpochSecond() / Math.max(1L, (long) intervalMinutes * 60);
        log.info("Billing auto-reconciliation: {} eligible checkout(s) found for window={}", eligible.size(), windowBucket);

        for (BillingCheckout checkout : eligible) {
            reconcileOne(checkout, windowBucket);
        }
    }

    private void reconcileOne(BillingCheckout checkout, long windowBucket) {
        if (StringUtils.isBlank(checkout.getProviderSubscriptionId())) {
            log.warn("Billing auto-reconciliation: checkoutId={} has no providerSubscriptionId; skipping.", checkout.getId());
            return;
        }
        String requestId = "auto-reconcile-" + checkout.getId() + "-" + windowBucket;
        try {
            MercadoPagoWebhookProcessor.Result result = processor.process(requestId, PREAPPROVAL_TOPIC, checkout.getProviderSubscriptionId());
            log.info("Billing auto-reconciliation: checkoutId={} result={}", checkout.getId(), result);
        } catch (Exception exception) {
            log.error("Billing auto-reconciliation: checkoutId={} failed: {}", checkout.getId(), exception.getMessage());
        }
    }
}
