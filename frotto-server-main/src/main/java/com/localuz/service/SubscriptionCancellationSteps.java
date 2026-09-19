package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingCancellationProviderRejectedException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The individually-committed persistence steps behind SubscriptionCancellationService, split
 * into their own bean specifically so each one runs as its own short, independently-COMMITTED
 * transaction. This is not a stylistic choice: SubscriptionCancellationService#cancel is
 * deliberately not @Transactional (same reasoning as BillingAutoReconciliationScheduler - an HTTP
 * call to Mercado Pago must never happen inside an open transaction that a try/catch is supposed
 * to contain, or a failure risks UnexpectedRollbackException). If markIntent() lived on the same
 * class as cancel() and were called as a plain `this.markIntent(...)` self-invocation, Spring's
 * @Transactional AOP proxy would never see that call at all - self-invocation bypasses the proxy
 * entirely, silently making @Transactional a no-op. Putting these steps on a separate bean and
 * calling them through the injected reference forces every call through the real proxy, so each
 * step really does commit on its own before the caller does anything else.
 *
 * That "commit before doing anything else" property is exactly what closes the race described in
 * the Etapa 5F.1 requirements: Mercado Pago can process a PUT /preapproval cancellation and fire
 * a webhook almost synchronously, and that webhook's own transaction (MercadoPagoWebhookProcessor
 * #process) reads Subscription fresh from the database. As long as markIntent()'s write of
 * cancelAtPeriodEnd=true is committed BEFORE SubscriptionCancellationService ever calls
 * MercadoPagoClient#cancelPreapproval, there is no ordering under which a racing webhook can ever
 * observe cancelAtPeriodEnd=false for a cancellation Frotto itself initiated - the flag is already
 * durable before the provider even receives the request that could trigger that webhook.
 */
@Service
public class SubscriptionCancellationSteps {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCancellationSteps.class);
    private static final String ENTITY_NAME = "subscriptionCancellation";
    /**
     * PAUSED was added here for 5G.9 section B: Mercado Pago's own docs describe pausing,
     * reactivating and cancelling a preapproval as independent PUT /preapproval/{id} operations
     * (status=paused / status=authorized / status=cancelled respectively; see "Gerenciamento de
     * assinaturas" / "Subscription management" in the Mercado Pago developer docs) with no
     * documented precondition that a paused subscription must first be reactivated before it can
     * be cancelled - and cancelPreapproval already sends a plain PUT status=cancelled with no
     * current-status precondition of its own. This is not a blind assumption: if Mercado Pago
     * were to reject a paused-to-cancelled transition, markIntent()/the provider call below still
     * fail safely (BillingCancellationProviderRejectedException, local intent rolled back, no
     * corrupted state - see cancel()/rollbackIntent()), so allowing the attempt costs nothing on
     * the failure path while fixing the concrete case where a real remote contract (PAUSED) could
     * not be cancelled through Frotto at all.
     */
    static final List<SubscriptionStatus> CANCELLABLE_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.PAUSED);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MercadoPagoClient client;
    private final Clock clock;

    @Autowired
    public SubscriptionCancellationSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository, MercadoPagoClient client) {
        this(userRepository, subscriptionRepository, client, Clock.systemUTC());
    }

    SubscriptionCancellationSteps(UserRepository userRepository, SubscriptionRepository subscriptionRepository, MercadoPagoClient client, Clock clock) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.client = client;
        this.clock = clock;
    }

    static boolean isTerminalCancelled(String providerStatus) {
        if (providerStatus == null) {
            return false;
        }
        String normalized = providerStatus.toLowerCase(Locale.ROOT);
        return "cancelled".equals(normalized) || "canceled".equals(normalized);
    }

    /**
     * Locks the user row (same PESSIMISTIC_WRITE primitive BillingCheckoutService already uses to
     * serialize concurrent checkout creation for one user) so two concurrent cancel requests for
     * the same user can never both decide "not yet requested" and both proceed - the second one
     * blocks here until the first commits, then observes the up-to-date cancelAtPeriodEnd/
     * canceledAt state.
     *
     * cancelAtPeriodEnd and canceledAt together carry three distinguishable states, with no new
     * column or status needed:
     * - false / null           -> nothing requested yet
     * - true  / null           -> requested, but the provider has not confirmed it - PENDING
     * - true  / non-null       -> the provider confirmed the cancellation - CONFIRMED
     *
     * Only the CONFIRMED state is a true no-op here (needsProviderCall=false): a second click
     * after confirmation must never call the provider again. The PENDING state is deliberately
     * NOT treated as "already requested, nothing to do" - a caller (a manual retry, or this same
     * request after an earlier ambiguous failure) must still be able to drive it to CONFIRMED, so
     * this returns needsProviderCall=true again, reusing the exact same deterministic idempotency
     * key ("cancel-" + externalSubscriptionId in SubscriptionCancellationService) so repeating the
     * provider call is safe.
     *
     * Validates everything the provider call depends on BEFORE writing anything: a missing
     * externalSubscriptionId or currentPeriodEnd means we could never safely defer access to a
     * period end we don't know, so this refuses rather than guessing.
     */
    @Transactional
    public IntentOutcome markIntent(Long userId) {
        userRepository.findByIdForBillingCheckoutLock(userId).orElseThrow(() -> new IllegalArgumentException("Authenticated user is required"));

        Subscription subscription = subscriptionRepository
            .findByUserIdAndSourceAndStatusIn(userId, SubscriptionSource.PAYMENT_PROVIDER, CANCELLABLE_STATUSES)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BadRequestAlertException("No active payment-provider subscription to cancel", ENTITY_NAME, "nosubscriptiontocancel")
            );

        boolean alreadyRequested = Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd());
        boolean alreadyConfirmed = alreadyRequested && subscription.getCanceledAt() != null;
        if (alreadyConfirmed) {
            return new IntentOutcome(subscription, false);
        }

        if (StringUtils.isBlank(subscription.getExternalSubscriptionId())) {
            throw new BadRequestAlertException("Subscription is missing its provider reference", ENTITY_NAME, "missingproviderreference");
        }
        if (subscription.getCurrentPeriodEnd() == null) {
            throw new BadRequestAlertException(
                "Subscription is missing its current period end; refusing to cancel without a guaranteed paid-access boundary",
                ENTITY_NAME,
                "missingperiodend"
            );
        }

        if (!alreadyRequested) {
            subscription.setCancelAtPeriodEnd(true);
            subscriptionRepository.save(subscription);
        }
        // alreadyRequested-but-not-confirmed (PENDING): cancelAtPeriodEnd is already true and
        // durable, nothing new to write here - the caller retries the provider call itself.
        return new IntentOutcome(subscription, true);
    }

    /** Confirmed by the provider's own synchronous response - never touches status/currentPeriodEnd/plan/price. */
    @Transactional
    public Subscription finalizeConfirmedCancellation(Long subscriptionId, Instant providerConfirmedAt) {
        Subscription subscription = subscriptionRepository
            .findById(subscriptionId)
            .orElseThrow(() -> new IllegalStateException("Subscription disappeared during cancellation: " + subscriptionId));
        if (subscription.getCanceledAt() == null) {
            subscription.setCanceledAt(providerConfirmedAt != null ? providerConfirmedAt : Instant.now(clock));
            subscriptionRepository.save(subscription);
        }
        return subscription;
    }

    /**
     * Only called after a definite (non-ambiguous) provider rejection of the cancel call - we
     * know for a fact the provider was never told to cancel, so undoing the local intent is safe.
     * Still re-checks canceledAt first: if anything already confirmed the cancellation in the
     * meantime (a very unlikely but possible race between our own ambiguous-vs-definite reading
     * and a concurrent webhook), never undo a confirmed cancellation.
     */
    @Transactional
    public void rollbackIntent(Long subscriptionId) {
        subscriptionRepository
            .findById(subscriptionId)
            .filter(subscription -> subscription.getCanceledAt() == null)
            .ifPresent(subscription -> {
                subscription.setCancelAtPeriodEnd(false);
                subscriptionRepository.save(subscription);
            });
    }

    /**
     * The provider's response to our PUT was ambiguous (timeout/5xx) or unexpectedly not a
     * cancelled status. Mercado Pago is the authority, so instead of guessing we issue a
     * read-only GET to find out what actually happened:
     * - confirmed cancelled -> treat as success (the PUT worked, only its response was lost)
     * - confirmed NOT cancelled -> safe to roll back, since we now know for certain
     * - the confirming GET itself fails/is ambiguous -> leave cancelAtPeriodEnd as it is (true).
     *   Flipping it blindly in either direction here would be a guess; leaving it set is the
     *   side that can never cause premature loss of access or a premature CANCELED status - the
     *   worst case is a stale "cancellation scheduled" flag until a retry or a real webhook
     *   resolves it, never a wrongly-lost subscription.
     */
    @Transactional(noRollbackFor = BillingCancellationProviderRejectedException.class)
    public Subscription resolveAfterUnconfirmedResponse(Long subscriptionId) {
        Subscription subscription = subscriptionRepository
            .findById(subscriptionId)
            .orElseThrow(() -> new IllegalStateException("Subscription disappeared during cancellation: " + subscriptionId));

        MercadoPagoPreapproval current;
        try {
            current = client.getPreapproval(subscription.getExternalSubscriptionId());
        } catch (MercadoPagoException confirmFailure) {
            log.warn(
                "Subscription cancellation could not be confirmed for subscriptionId={} category={} httpStatus={} providerErrorCode={}",
                subscriptionId,
                confirmFailure.getCategory(),
                confirmFailure.getHttpStatus(),
                confirmFailure.getSafeProviderErrorCode()
            );
            throw confirmFailure;
        }

        if (isTerminalCancelled(current.getStatus())) {
            if (subscription.getCanceledAt() == null) {
                subscription.setCanceledAt(current.getLastModified() != null ? current.getLastModified() : Instant.now(clock));
                subscriptionRepository.save(subscription);
            }
            return subscription;
        }
        if (subscription.getCanceledAt() == null) {
            subscription.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(subscription);
        }
        throw new BillingCancellationProviderRejectedException();
    }

    public static final class IntentOutcome {
        private final Subscription subscription;
        private final boolean needsProviderCall;

        IntentOutcome(Subscription subscription, boolean needsProviderCall) {
            this.subscription = subscription;
            this.needsProviderCall = needsProviderCall;
        }

        public Subscription getSubscription() {
            return subscription;
        }

        public boolean isNeedsProviderCall() {
            return needsProviderCall;
        }
    }
}
