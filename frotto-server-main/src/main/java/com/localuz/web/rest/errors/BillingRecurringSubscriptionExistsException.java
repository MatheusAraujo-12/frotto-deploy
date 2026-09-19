package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * A PAYMENT_PROVIDER contract that can still charge (or is an unresolved live remote contract -
 * see RecurringSubscriptionGuardService) already exists for this user; refuses to create a second
 * remote recurrence rather than guessing at a substitution/upgrade flow (docs/billing-recurring-
 * contract-5g1.md invariant 16, section 20).
 */
@SuppressWarnings("java:S110")
public class BillingRecurringSubscriptionExistsException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;

    public BillingRecurringSubscriptionExistsException() {
        super(
            ErrorConstants.BILLING_RECURRING_SUBSCRIPTION_EXISTS_TYPE,
            "A remote recurring subscription already exists",
            Status.CONFLICT,
            null,
            null,
            null,
            Map.of("message", "error.BILLING_RECURRING_SUBSCRIPTION_EXISTS")
        );
    }
}
