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

    public BillingPaymentStateDTO(Subscription subscription, BillingCheckout checkout) {
        paymentProviderSubscription = subscription == null ? null : new PaymentProviderSubscription(subscription);
        latestCheckout = checkout == null ? null : new LatestCheckout(checkout);
    }

    public PaymentProviderSubscription getPaymentProviderSubscription() { return paymentProviderSubscription; }
    public LatestCheckout getLatestCheckout() { return latestCheckout; }

    public static class PaymentProviderSubscription {
        private final SubscriptionStatus status; private final PlanCode planCode; private final BillingCycle billingCycle;
        PaymentProviderSubscription(Subscription subscription) { status=subscription.getStatus();planCode=subscription.getPlan().getCode();billingCycle=subscription.getBillingCycle(); }
        public SubscriptionStatus getStatus(){return status;} public PlanCode getPlanCode(){return planCode;} public BillingCycle getBillingCycle(){return billingCycle;}
    }

    public static class LatestCheckout {
        private final BillingCheckoutStatus status; private final PlanCode planCode; private final Instant createdAt;
        LatestCheckout(BillingCheckout checkout) { status=checkout.getStatus();planCode=checkout.getPlan().getCode();createdAt=checkout.getCreatedAt(); }
        public BillingCheckoutStatus getStatus(){return status;} public PlanCode getPlanCode(){return planCode;} public Instant getCreatedAt(){return createdAt;}
    }
}
