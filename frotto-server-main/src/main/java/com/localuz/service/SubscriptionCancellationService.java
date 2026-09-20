package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.service.SubscriptionCancellationSteps.IntentOutcome;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.web.rest.errors.BillingCancellationProviderRejectedException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates safe subscription cancellation: never @Transactional itself, so the HTTP call to
 * Mercado Pago always happens between two separately-committed steps rather than inside an open
 * transaction - see SubscriptionCancellationSteps' javadoc for why that matters and how it closes
 * the PUT/webhook race.
 *
 * Only ever targets the user's own PAYMENT_PROVIDER Subscription rows (source is part of the
 * lookup query itself in SubscriptionCancellationSteps#markIntents, scoped to the authenticated
 * user's id) - an ADMIN_GRANT or GRANDFATHERED row, or another user's subscription, can never be
 * found or touched by this flow, so the ADMIN_GRANT &gt; PAYMENT_PROVIDER &gt; GRANDFATHERED
 * precedence in SubscriptionService is unaffected regardless of which one is currently effective.
 *
 * Never creates a preapproval, never changes plan/price/billingCycle, never calls anything but
 * MercadoPagoClient#cancelPreapproval (a write) and MercadoPagoClient#getPreapproval (read-only,
 * used only to resolve an ambiguous cancel response - see SubscriptionCancellationSteps).
 *
 * 5G.11: pre-5G.9 data can leave a user with more than one PAYMENT_PROVIDER row in a cancellable
 * status. "Cancelar assinatura" means "stop this user's Mercado Pago recurring charges", so every
 * such row is attempted - the most-recently-started one (index 0 of
 * SubscriptionCancellationSteps#markIntents, already ordered) is treated as the primary result and
 * behaves EXACTLY as the original single-subscription flow always has, including letting its own
 * failure propagate to the caller unchanged. Any further historical duplicates are cancelled best-
 * effort: a failure on one of them is logged (never with a raw provider message) and never masks
 * the primary result, never triggers a rollback of a sibling the provider already confirmed, and
 * never claims a duplicate is cancelled when it is not - see hasResidualActiveContract, which
 * BillingResource surfaces to the frontend so the user is told a recurrence may still be active
 * instead of being falsely told everything succeeded.
 */
@Service
public class SubscriptionCancellationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCancellationService.class);

    private final SubscriptionCancellationSteps steps;
    private final MercadoPagoClient client;

    public SubscriptionCancellationService(SubscriptionCancellationSteps steps, MercadoPagoClient client) {
        this.steps = steps;
        this.client = client;
    }

    public Subscription cancel(User authenticatedUser) {
        Long userId = requireUserId(authenticatedUser);

        List<IntentOutcome> intents = steps.markIntents(userId);
        Subscription primary = processOne(intents.get(0));

        for (int i = 1; i < intents.size(); i++) {
            IntentOutcome siblingIntent = intents.get(i);
            try {
                processOne(siblingIntent);
            } catch (RuntimeException siblingFailure) {
                logSiblingFailure(siblingIntent.getSubscription().getId(), siblingFailure);
            }
        }
        return primary;
    }

    /**
     * Whether the user still has ANY PAYMENT_PROVIDER contract able to charge them, re-checked
     * fresh after cancel() has run. True means a historical duplicate could not be confirmed
     * cancelled (partial failure) - the caller must tell the user, never silently report full
     * success.
     */
    public boolean hasResidualActiveContract(User authenticatedUser) {
        return steps.hasRemainingChargeableContract(requireUserId(authenticatedUser));
    }

    private Long requireUserId(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return authenticatedUser.getId();
    }

    private Subscription processOne(IntentOutcome intent) {
        if (!intent.isNeedsProviderCall()) {
            // Already requested by an earlier call (or already confirmed, or this particular row
            // could not be safely processed) - idempotent no-op, never a second provider call.
            return intent.getSubscription();
        }

        Subscription subscription = intent.getSubscription();
        String idempotencyKey = "cancel-" + subscription.getExternalSubscriptionId();

        MercadoPagoPreapproval result;
        try {
            result = client.cancelPreapproval(subscription.getExternalSubscriptionId(), idempotencyKey);
        } catch (MercadoPagoException exception) {
            if (!exception.isAmbiguous()) {
                // A definite rejection: the provider was never told to cancel, safe to undo.
                steps.rollbackIntent(subscription.getId());
                throw new BillingCancellationProviderRejectedException();
            }
            // Timeout/5xx/connection failure: unknown whether the provider processed it.
            // Resolved by a read-only confirming GET, never by guessing.
            return steps.resolveAfterUnconfirmedResponse(subscription.getId());
        }
        if (SubscriptionCancellationSteps.isTerminalCancelled(result.getStatus())) {
            return steps.finalizeConfirmedCancellation(subscription.getId(), result.getLastModified());
        }
        return steps.resolveAfterUnconfirmedResponse(subscription.getId());
    }

    private void logSiblingFailure(Long subscriptionId, RuntimeException failure) {
        if (failure instanceof MercadoPagoException mercadoPagoException) {
            log.warn(
                "Historical duplicate PAYMENT_PROVIDER subscriptionId={} could not be confirmed cancelled category={} httpStatus={} providerErrorCode={}",
                subscriptionId, mercadoPagoException.getCategory(), mercadoPagoException.getHttpStatus(), mercadoPagoException.getSafeProviderErrorCode()
            );
        } else {
            log.warn(
                "Historical duplicate PAYMENT_PROVIDER subscriptionId={} could not be confirmed cancelled exceptionType={}",
                subscriptionId, failure.getClass().getSimpleName()
            );
        }
    }
}
