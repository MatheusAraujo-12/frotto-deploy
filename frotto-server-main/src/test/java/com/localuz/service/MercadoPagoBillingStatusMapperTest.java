package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import com.localuz.domain.enumeration.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MercadoPagoBillingStatusMapperTest {
    private final MercadoPagoBillingStatusMapper mapper = new MercadoPagoBillingStatusMapper();
    @ParameterizedTest
    @CsvSource({"pending,PENDING", "in_process,PROCESSING", "in_mediation,PROCESSING", "authorized,PROCESSING",
        "approved,APPROVED", "rejected,REJECTED", "cancelled,CANCELED", "canceled,CANCELED", "refunded,REFUNDED", "charged_back,CHARGEDBACK"})
    void mapsPayment(String remote, PaymentAttemptStatus expected) {
        assertThat(mapper.payment(remote)).contains(expected);
    }
    @ParameterizedTest
    @CsvSource({"scheduled,PENDING", "pending,PENDING", "waiting,PROCESSING", "processing,PROCESSING",
        "recycling,PROCESSING", "processed,PROCESSING", "cancelled,CANCELED", "canceled,CANCELED"})
    void mapsOnlyConservativeUnsettledInvoiceState(String remote, BillingInvoiceStatus expected) {
        assertThat(mapper.unsettledInvoice(remote)).contains(expected);
    }
    @Test void unknownOrMissingStatusDoesNotInventFinancialState() {
        assertThat(mapper.payment(null)).isEmpty();
        assertThat(mapper.payment("new-status")).isEmpty();
        assertThat(mapper.unsettledInvoice("authorized")).isEmpty();
    }
}
