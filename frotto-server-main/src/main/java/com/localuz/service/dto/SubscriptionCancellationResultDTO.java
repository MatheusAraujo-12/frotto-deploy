package com.localuz.service.dto;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.time.Instant;

/**
 * Response for POST /api/billing/cancel. Deliberately excludes providerSubscriptionId,
 * idempotencyKey, and any other gateway-facing detail - same safe-field philosophy as
 * BillingMeDTO.
 *
 * cancelAtPeriodEnd alone (as returned by BillingMeDTO/GET /api/billing/me) cannot distinguish a
 * cancellation Mercado Pago has actually confirmed from one still only recorded locally as
 * intent - see SubscriptionCancellationSteps#markIntent's javadoc for why both states share
 * cancelAtPeriodEnd=true. This DTO makes that distinction explicit via state, so a future
 * frontend can render "cancellation scheduled" only once it is truly CONFIRMED, and something
 * like "we're still confirming this with the payment provider, please try again shortly" for
 * PENDING_CONFIRMATION - never presenting a merely-requested-but-unconfirmed cancellation as if
 * Mercado Pago had already accepted it.
 */
public class SubscriptionCancellationResultDTO {

    public enum CancellationState {
        /** The provider confirmed the cancellation (canceledAt is set); paid access still runs until currentPeriodEnd. */
        CONFIRMED,
        /** cancelAtPeriodEnd=true was recorded, but the provider has not confirmed it yet - never present this as a completed cancellation. */
        PENDING_CONFIRMATION,
    }

    private final CancellationState state;
    private final PlanCode planCode;
    private final SubscriptionStatus subscriptionStatus;
    private final Instant currentPeriodEnd;
    private final boolean hasResidualActiveContract;

    public SubscriptionCancellationResultDTO(CancellationState state, PlanCode planCode, SubscriptionStatus subscriptionStatus,
        Instant currentPeriodEnd, boolean hasResidualActiveContract) {
        this.state = state;
        this.planCode = planCode;
        this.subscriptionStatus = subscriptionStatus;
        this.currentPeriodEnd = currentPeriodEnd;
        this.hasResidualActiveContract = hasResidualActiveContract;
    }

    public static SubscriptionCancellationResultDTO from(Subscription subscription, boolean hasResidualActiveContract) {
        CancellationState state = subscription.getCanceledAt() != null
            ? CancellationState.CONFIRMED
            : CancellationState.PENDING_CONFIRMATION;
        return new SubscriptionCancellationResultDTO(state, subscription.getPlan().getCode(), subscription.getStatus(),
            subscription.getCurrentPeriodEnd(), hasResidualActiveContract);
    }

    public CancellationState getState() {
        return state;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    /**
     * 5G.11: true when, after this call, the user still has ANY PAYMENT_PROVIDER contract able to
     * charge them - a historical pre-5G.9 duplicate that this call could not confirm as cancelled.
     * Never means "this cancellation failed" (state already covers that): the primary contract
     * above may be CONFIRMED while this is still true, and the frontend must surface both facts
     * rather than presenting a false "everything is cancelled".
     */
    public boolean isHasResidualActiveContract() {
        return hasResidualActiveContract;
    }
}
