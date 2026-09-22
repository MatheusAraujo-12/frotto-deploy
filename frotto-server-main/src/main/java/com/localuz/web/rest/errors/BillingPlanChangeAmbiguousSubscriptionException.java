package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * 5G.12: a plan change requires exactly one still-chargeable PAYMENT_PROVIDER contract to act on.
 * Pre-5G.9 data can leave a user with more than one (see RecurringSubscriptionGuardService); this
 * is thrown instead of arbitrarily picking one, leaving the user to resolve/cancel the duplicates
 * through the existing cancellation flow first.
 */
@SuppressWarnings("java:S110")
public class BillingPlanChangeAmbiguousSubscriptionException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingPlanChangeAmbiguousSubscriptionException() {
        super(ErrorConstants.DEFAULT_TYPE, "Multiple chargeable subscriptions exist", Status.CONFLICT,
            "Existem múltiplas assinaturas ativas vinculadas à sua conta. Resolva ou cancele as duplicidades antes de mudar de plano.",
            null, null, Map.of("message", "error.BILLING_PLAN_CHANGE_AMBIGUOUS_SUBSCRIPTION"));
    }
}
