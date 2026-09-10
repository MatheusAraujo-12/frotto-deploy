package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.service.SubscriptionCancellationSteps.IntentOutcome;
import com.localuz.service.dto.MercadoPagoPreapproval;
import org.springframework.stereotype.Service;

/**
 * Orchestrates safe subscription cancellation: never @Transactional itself, so the HTTP call to
 * Mercado Pago always happens between two separately-committed steps rather than inside an open
 * transaction - see SubscriptionCancellationSteps' javadoc for why that matters and how it closes
 * the PUT/webhook race.
 *
 * Only ever targets the user's PAYMENT_PROVIDER Subscription (source is part of the lookup query
 * itself in SubscriptionCancellationSteps#markIntent) - an ADMIN_GRANT or GRANDFATHERED row can
 * never be found or touched by this flow, so the ADMIN_GRANT &gt; PAYMENT_PROVIDER &gt;
 * GRANDFATHERED precedence in SubscriptionService is unaffected regardless of which one is
 * currently effective for the user.
 *
 * Never creates a preapproval, never changes plan/price/billingCycle, never calls anything but
 * MercadoPagoClient#cancelPreapproval (a write) and MercadoPagoClient#getPreapproval (read-only,
 * used only to resolve an ambiguous cancel response - see SubscriptionCancellationSteps).
 */
@Service
public class SubscriptionCancellationService {

    private final SubscriptionCancellationSteps steps;
    private final MercadoPagoClient client;

    public SubscriptionCancellationService(SubscriptionCancellationSteps steps, MercadoPagoClient client) {
        this.steps = steps;
        this.client = client;
    }

    public Subscription cancel(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        IntentOutcome intent = steps.markIntent(authenticatedUser.getId());
        if (!intent.isNeedsProviderCall()) {
            // Already requested by an earlier call (or already confirmed) - idempotent no-op,
            // never a second provider call.
            return intent.getSubscription();
        }

        Subscription subscription = intent.getSubscription();
        String idempotencyKey = "cancel-" + subscription.getExternalSubscriptionId();

        try {
            MercadoPagoPreapproval result = client.cancelPreapproval(subscription.getExternalSubscriptionId(), idempotencyKey);
            if (SubscriptionCancellationSteps.isTerminalCancelled(result.getStatus())) {
                return steps.finalizeConfirmedCancellation(subscription.getId(), result.getLastModified());
            }
            // Provider responded, but not with a cancelled status - never assumed successful
            // without confirmation.
            return steps.resolveAfterUnconfirmedResponse(subscription.getId());
        } catch (MercadoPagoException exception) {
            if (!exception.isAmbiguous()) {
                // A definite rejection: the provider was never told to cancel, safe to undo.
                steps.rollbackIntent(subscription.getId());
                throw exception;
            }
            // Timeout/5xx/connection failure: unknown whether the provider processed it.
            // Resolved by a read-only confirming GET, never by guessing.
            return steps.resolveAfterUnconfirmedResponse(subscription.getId());
        }
    }
}
