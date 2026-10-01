package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.localuz.domain.MercadoPagoWebhookEvent;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

/** E2 orchestration: register (own tx) -> process (own tx, row locked) -> on failure record RETRYABLE (own tx) and re-throw. */
class MercadoPagoWebhookDeliveryServiceTest {
    private final MercadoPagoWebhookEventService events = mock(MercadoPagoWebhookEventService.class);
    private final MercadoPagoWebhookProcessor processor = mock(MercadoPagoWebhookProcessor.class);
    private final MercadoPagoWebhookDeliveryService delivery = new MercadoPagoWebhookDeliveryService(events, processor);

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("x", new org.hibernate.exception.ConstraintViolationException("duplicate",
            new SQLIntegrityConstraintViolationException("duplicate", "23000", 1062), constraint));
    }

    @Test void registersBeforeProcessingAndReturnsTheProcessorResult() {
        when(processor.process("req", "payment", "pay-1")).thenReturn(MercadoPagoWebhookProcessor.Result.PROCESSED);
        assertThat(delivery.deliver("req", "payment", "pay-1")).isEqualTo(MercadoPagoWebhookProcessor.Result.PROCESSED);
        InOrder order = inOrder(events, processor);
        order.verify(events).registerDelivery("req", "payment", "pay-1");
        order.verify(processor).process("req", "payment", "pay-1");
        verify(events, never()).recordRetryableFailure(any(), any(), any(), any());
    }

    @Test void concurrentRegistrationOfTheSameDeliveryStillProcessesAgainstTheLockedRow() {
        doThrow(violation("ux_mp_webhook_delivery")).when(events).registerDelivery("req", "payment", "pay-1");
        when(processor.process("req", "payment", "pay-1")).thenReturn(MercadoPagoWebhookProcessor.Result.DUPLICATE);
        assertThat(delivery.deliver("req", "payment", "pay-1")).isEqualTo(MercadoPagoWebhookProcessor.Result.DUPLICATE);
    }

    @Test void anyOtherConstraintFailureDuringRegistrationIsNotSwallowed() {
        doThrow(violation("some_other_constraint")).when(events).registerDelivery(any(), any(), any());
        assertThatThrownBy(() -> delivery.deliver("req", "payment", "pay-1")).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(processor);
    }

    @Test void processingFailureIsRecordedRetryableWithASafeCodeAndRethrown() {
        MercadoPagoException failure = new MercadoPagoException("body with secret must not be stored", false, 503, null, null);
        when(processor.process(any(), any(), any())).thenThrow(failure);
        when(events.recordRetryableFailure(any(), any(), any(), any())).thenReturn(MercadoPagoWebhookEvent.received("req", "payment", "pay-1", Instant.now()));
        assertThatThrownBy(() -> delivery.deliver("req", "payment", "pay-1")).isSameAs(failure);
        verify(events).recordRetryableFailure("req", "payment", "pay-1", "provider_http_5xx");
    }

    @Test void failureCodesAreDerivedFromTypesNeverFromMessages() {
        assertThat(MercadoPagoWebhookDeliveryService.failureResult(new MercadoPagoException("x", false, 429, null, null))).isEqualTo("provider_http_429");
        assertThat(MercadoPagoWebhookDeliveryService.failureResult(new MercadoPagoException("x", true, new java.net.http.HttpTimeoutException("t")))).isEqualTo("provider_timeout");
        assertThat(MercadoPagoWebhookDeliveryService.failureResult(new QueryTimeoutException("db secret"))).isEqualTo("persistence_failure");
        assertThat(MercadoPagoWebhookDeliveryService.failureResult(new IllegalStateException("anything"))).isEqualTo("operational_failure");
    }

    @Test void bookkeepingFailureNeverMasksTheOriginalFailure() {
        MercadoPagoException failure = new MercadoPagoException("provider", false, 500, null, null);
        when(processor.process(any(), any(), any())).thenThrow(failure);
        when(events.recordRetryableFailure(any(), any(), any(), any())).thenThrow(new QueryTimeoutException("db down"));
        assertThatThrownBy(() -> delivery.deliver("req", "payment", "pay-1")).isSameAs(failure);
    }
}
