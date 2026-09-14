package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingInvoiceStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class BillingInvoiceTemporalValidationTest {
    @Test void unknownDatesPassBeanValidationButOtherRequiredFieldsRemainRequired() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            BillingInvoice invoice = new BillingInvoice();
            invoice.setSubscription(new Subscription());
            invoice.setProvider("MERCADO_PAGO");
            invoice.setExternalAuthorizedPaymentId("charge-1");
            invoice.setStatus(BillingInvoiceStatus.PENDING);
            invoice.setAmount(new BigDecimal("15.90"));
            invoice.setCurrency("BRL");
            invoice.prePersist();
            assertThat(factory.getValidator().validate(invoice)).isEmpty();
            invoice.setAmount(null);
            assertThat(factory.getValidator().validate(invoice)).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("amount"));
        }
    }

    @Test void knownDueRequiresExactly72HoursAndMissingDueCannotHaveGrace() {
        BillingInvoice invoice = new BillingInvoice();
        Instant due = Instant.parse("2026-09-14T12:00:00Z");
        invoice.setDueAt(due);
        assertThat(invoice.isPeriodValid()).isFalse();
        invoice.setGracePeriodEnd(due.plus(Duration.ofHours(72)));
        assertThat(invoice.isPeriodValid()).isTrue();
        invoice.setGracePeriodEnd(due.plus(Duration.ofHours(72)).plusSeconds(1));
        assertThat(invoice.isPeriodValid()).isFalse();
        invoice.setDueAt(null);
        assertThat(invoice.isPeriodValid()).isFalse();
    }

    @Test void completePeriodStillRequiresPositiveDurationAndMatchingDue() {
        BillingInvoice invoice = new BillingInvoice();
        Instant start = Instant.parse("2026-09-14T12:00:00Z");
        invoice.setPeriodStart(start);
        invoice.setPeriodEnd(start);
        assertThat(invoice.isPeriodValid()).isFalse();
        invoice.setPeriodEnd(start.plus(Duration.ofDays(30)));
        assertThat(invoice.isPeriodValid()).isTrue();
        invoice.setDueAt(start.plusSeconds(1));
        invoice.setGracePeriodEnd(invoice.getDueAt().plus(Duration.ofHours(72)));
        assertThat(invoice.isPeriodValid()).isFalse();
    }
}
