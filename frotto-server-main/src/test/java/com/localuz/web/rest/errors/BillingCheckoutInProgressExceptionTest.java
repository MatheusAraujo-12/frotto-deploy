package com.localuz.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BillingCheckoutInProgressExceptionTest {

    @Test
    void exposesStableConflictContractWithoutInternalDetails() {
        BillingCheckoutInProgressException exception = new BillingCheckoutInProgressException();

        assertThat(exception.getStatus().getStatusCode()).isEqualTo(409);
        assertThat(exception.getParameters()).containsEntry("message", "error.BILLING_CHECKOUT_IN_PROGRESS");
        assertThat(exception.getParameters()).hasSize(1);
    }
}
