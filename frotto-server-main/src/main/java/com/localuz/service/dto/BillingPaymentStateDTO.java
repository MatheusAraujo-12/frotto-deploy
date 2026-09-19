package com.localuz.service.dto;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.time.Instant;

/** Safe user-facing payment state. Provider identifiers and raw payloads are excluded. */
public class BillingPaymentStateDTO {
    private final PaymentProviderSubscription paymentProviderSubscription;
    private final LatestCheckout latestCheckout;

    public BillingPaymentStateDTO(Subscription subscription, boolean financiallyCovered, boolean canCancel, BillingCheckout checkout) {
        paymentProviderSubscription = subscription == null ? null : new PaymentProviderSubscription(subscription, financiallyCovered, canCancel);
        latestCheckout = checkout == null ? null : new LatestCheckout(checkout);
    }

    public PaymentProviderSubscription getPaymentProviderSubscription() { return paymentProviderSubscription; }
    public LatestCheckout getLatestCheckout() { return latestCheckout; }

    public static class PaymentProviderSubscription {
        private final SubscriptionStatus status; private final PlanCode planCode; private final BillingCycle billingCycle;
        private final boolean financiallyCovered;
        private final boolean canCancel;
        private final SubscriptionCancellationState cancellationState;
        private final Instant currentPeriodEnd;
        /**
         * financiallyCovered mirrors SubscriptionFinancialCoverageService's 5G verdict (BillingInvoice +
         * PaymentAttempt evidence), never the persisted status alone - status=ACTIVE on its own is not
         * financial proof. The frontend must not render "confirmado"/"ativa" language from this status
         * field unless this flag is true.
         *
         * canCancel is a SEPARATE question (5G.9 section B: canCancelRemoteContract !=
         * hasPaidEntitlement) - it is true whenever this remote contract is in a status the
         * cancellation flow (SubscriptionCancellationSteps.CANCELLABLE_STATUSES) accepts,
         * regardless of financiallyCovered. This is what fixes the bug where a PAYMENT_PROVIDER
         * subscription authorized-but-not-yet-financially-proven (status=ACTIVE, no BillingInvoice/
         * PaymentAttempt yet) made BillingMeDTO fall back to FREE/subscriptionSource=null, hiding
         * the cancel action for a remote contract that genuinely exists and can still charge.
         * cancellationState/currentPeriodEnd are exposed only to drive that same UI (progress/
         * messaging), never providerSubscriptionId/externalReference/idempotencyKey/payer/payload.
         */
        PaymentProviderSubscription(Subscription subscription, boolean financiallyCovered, boolean canCancel) {
            status=subscription.getStatus();planCode=subscription.getPlan().getCode();billingCycle=subscription.getBillingCycle();
            this.financiallyCovered=financiallyCovered;
            this.canCancel=canCancel;
            this.cancellationState=SubscriptionCancellationState.from(subscription.getCancelAtPeriodEnd(), subscription.getCanceledAt());
            this.currentPeriodEnd=subscription.getCurrentPeriodEnd();
        }
        public SubscriptionStatus getStatus(){return status;} public PlanCode getPlanCode(){return planCode;} public BillingCycle getBillingCycle(){return billingCycle;}
        public boolean isFinanciallyCovered(){return financiallyCovered;}
        public boolean isCanCancel(){return canCancel;}
        public SubscriptionCancellationState getCancellationState(){return cancellationState;}
        public Instant getCurrentPeriodEnd(){return currentPeriodEnd;}
    }

    public static class LatestCheckout {
        private final BillingCheckoutStatus status; private final PlanCode planCode; private final Instant createdAt;
        private final boolean canResume; private final String checkoutUrl;
        LatestCheckout(BillingCheckout checkout) {
            status=checkout.getStatus();planCode=checkout.getPlan().getCode();createdAt=checkout.getCreatedAt();
            String initPoint=checkout.getInitPoint();
            canResume=status.isResumable() && initPoint!=null && !initPoint.isBlank();
            checkoutUrl=canResume?initPoint:null;
        }
        public BillingCheckoutStatus getStatus(){return status;} public PlanCode getPlanCode(){return planCode;} public Instant getCreatedAt(){return createdAt;}
        public boolean isCanResume(){return canResume;} public String getCheckoutUrl(){return checkoutUrl;}
    }
}
