package com.localuz.web.rest.errors;

import java.util.Map;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/** A checkout already exists in a state that cannot safely be retried. */
@SuppressWarnings("java:S110")
public class BillingCheckoutInProgressException extends AbstractThrowableProblem {
    private static final long serialVersionUID = 1L;
    public BillingCheckoutInProgressException(){
        super(ErrorConstants.BILLING_CHECKOUT_IN_PROGRESS_TYPE,"Billing checkout already in progress",Status.CONFLICT,null,null,null,Map.of("message","error.BILLING_CHECKOUT_IN_PROGRESS"));
    }
}
