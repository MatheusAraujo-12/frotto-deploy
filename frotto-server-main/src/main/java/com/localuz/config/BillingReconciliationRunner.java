package com.localuz.config;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.MercadoPagoWebhookProcessor;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Operational one-off: reconciles a single user's already-authorized Mercado Pago subscription
 * when the webhook that should have delivered that state was never received (e.g. the
 * notification URL is registered on the wrong Application in the Mercado Pago panel - see the
 * Webhooks alignment investigation). It never talks to Mercado Pago beyond a read (via
 * MercadoPagoWebhookProcessor#process, which only ever issues GET /preapproval and
 * GET /authorized_payments) and never creates or cancels a preapproval, so running it can never
 * generate a new charge.
 *
 * Controlled entirely by billing.reconciliation.enabled (default false) and
 * billing.reconciliation.user-login, both read once at startup - same operational pattern as
 * AdminBootstrap: set the two env vars for one deploy, confirm the outcome in the logs, then set
 * BILLING_RECONCILIATION_ENABLED=false again.
 *
 * Fail-closed on every branch: refuses to run outside mercadopago.test-mode (so a stray env var
 * can never fire this against production), and any missing precondition (blank login, unknown
 * user, no reconcilable checkout, checkout without a providerSubscriptionId) is logged and
 * skipped rather than guessed at. It never accepts a provider id, price, plan, vehicle count or
 * status as input - it only ever reads the providerSubscriptionId already persisted on the
 * user's most recent CREATED/PROVIDER_PENDING/PROVIDER_UNKNOWN BillingCheckout, so there is no
 * way to point it at an arbitrary Mercado Pago resource.
 *
 * The requestId passed to the processor is deterministic ("manual-reconcile-" + checkoutId), so
 * MercadoPagoWebhookProcessor's own existing dedup (MercadoPagoWebhookEvent's
 * request_id/event_type/resource_id unique constraint) makes re-running this idempotent for
 * free - a second run reports Result.DUPLICATE and touches nothing.
 *
 * Deliberately not wired to any HTTP endpoint: exposing "reconcile this subscription" over
 * REST would be a forged-webhook-equivalent surface, so this only runs as server-side
 * bootstrap, the same reasoning AdminBootstrap documents for why admin promotion isn't an
 * endpoint either.
 */
@Component
public class BillingReconciliationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationRunner.class);
    private static final String PREAPPROVAL_TOPIC = "subscription_preapproval";
    private static final List<BillingCheckoutStatus> RECONCILABLE_STATUSES = List.of(
        BillingCheckoutStatus.CREATED,
        BillingCheckoutStatus.PROVIDER_PENDING,
        BillingCheckoutStatus.PROVIDER_UNKNOWN
    );
    private static final List<SubscriptionStatus> OPEN_SUBSCRIPTION_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    @Value("${billing.reconciliation.enabled:false}")
    private boolean enabled;

    @Value("${billing.reconciliation.user-login:}")
    private String userLogin;

    private final MercadoPagoProperties mercadoPagoProperties;
    private final UserRepository userRepository;
    private final BillingCheckoutRepository checkoutRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MercadoPagoWebhookProcessor processor;

    public BillingReconciliationRunner(
        MercadoPagoProperties mercadoPagoProperties,
        UserRepository userRepository,
        BillingCheckoutRepository checkoutRepository,
        SubscriptionRepository subscriptionRepository,
        MercadoPagoWebhookProcessor processor
    ) {
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.userRepository = userRepository;
        this.checkoutRepository = checkoutRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (!mercadoPagoProperties.isTestMode()) {
            log.error("Billing reconciliation: refusing to run because mercadopago.test-mode is not enabled; skipping.");
            return;
        }
        if (StringUtils.isBlank(userLogin)) {
            log.error("Billing reconciliation: billing.reconciliation.enabled=true but billing.reconciliation.user-login is blank; skipping.");
            return;
        }

        String normalizedLogin = userLogin.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findOneByLogin(normalizedLogin);
        if (userOpt.isEmpty()) {
            log.error("Billing reconciliation: no existing user found for the configured login; skipping.");
            return;
        }
        User user = userOpt.get();

        Optional<BillingCheckout> checkoutOpt = checkoutRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            user.getId(),
            RECONCILABLE_STATUSES
        );
        if (checkoutOpt.isEmpty()) {
            log.error("Billing reconciliation: user has no checkout in a reconcilable status; skipping.");
            return;
        }
        BillingCheckout checkout = checkoutOpt.get();

        if (StringUtils.isBlank(checkout.getProviderSubscriptionId())) {
            log.error("Billing reconciliation: checkoutId={} has no providerSubscriptionId; nothing to reconcile.", checkout.getId());
            return;
        }

        // run() is deliberately NOT @Transactional: process() below is @Transactional on its
        // own and must stay the transaction owner. Wrapping this method in @Transactional too
        // would make process() merely participate in run()'s transaction instead of owning one;
        // if process() then threw, it would mark that shared transaction rollback-only and
        // rethrow, this catch would swallow the exception, run() would return normally, and
        // Spring would raise UnexpectedRollbackException on commit - outside this try/catch,
        // propagating out of run() and failing application startup. Letting process() open its
        // own transaction means a failure inside it rolls back cleanly and this catch actually
        // stops it here, as intended.
        String requestId = "manual-reconcile-" + checkout.getId();
        try {
            MercadoPagoWebhookProcessor.Result result = processor.process(requestId, PREAPPROVAL_TOPIC, checkout.getProviderSubscriptionId());
            boolean hasActivePaymentProviderSubscription = !subscriptionRepository
                .findByUserIdAndSourceAndStatusIn(user.getId(), SubscriptionSource.PAYMENT_PROVIDER, OPEN_SUBSCRIPTION_STATUSES)
                .isEmpty();
            log.info(
                "Billing reconciliation: checkoutId={} result={} checkoutStatus={} paymentProviderSubscriptionActive={}",
                checkout.getId(),
                result,
                checkout.getStatus(),
                hasActivePaymentProviderSubscription
            );
        } catch (Exception exception) {
            log.error("Billing reconciliation: checkoutId={} failed: {}", checkout.getId(), exception.getMessage());
        }
    }
}
