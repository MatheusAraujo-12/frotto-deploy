package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.*;
import com.localuz.service.dto.MercadoPagoWebhookPayload;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MercadoPagoWebhookResourceTest {
    private MercadoPagoProperties properties; private MercadoPagoWebhookSignatureValidator validator; private MercadoPagoWebhookDeliveryService processor; private MercadoPagoWebhookResource resource;
    @BeforeEach void setUp(){properties=new MercadoPagoProperties();properties.setEnabled(true);validator=mock(MercadoPagoWebhookSignatureValidator.class);processor=mock(MercadoPagoWebhookDeliveryService.class);resource=new MercadoPagoWebhookResource(properties,validator,processor);}
    @Test void acceptsValidDelivery(){when(validator.isValid("sig","req","pre-1")).thenReturn(true);assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(200);verify(processor).deliver("req","subscription_preapproval","pre-1");}
    @Test void rejectsInvalidSignature(){assertThat(resource.receive("bad","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(401);verifyNoInteractions(processor);}
    @Test void rejectsManipulatedPayload(){when(validator.isValid(any(),any(),any())).thenReturn(true);assertThat(resource.receive("sig","req","pre-1",payload("attacker-id")).getStatusCodeValue()).isEqualTo(400);verifyNoInteractions(processor);}
    @Test void retriesProviderFailure(){when(validator.isValid(any(),any(),any())).thenReturn(true);doThrow(new MercadoPagoException("temporary",false)).when(processor).deliver(any(),any(),any());assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(503);}
    @Test void genuineInvoiceConstraintFailureIsNotAcknowledgedAsDuplicate() {
        when(validator.isValid(any(), any(), any())).thenReturn(true);
        doThrow(integrityFailure("ux_billing_invoice_provider_id")).when(processor).deliver(any(), any(), any());
        assertThat(resource.receive("sig", "req", "pre-1", payload("pre-1")).getStatusCodeValue()).isEqualTo(503);
    }
    @Test void onlyExpectedDeliveryUniqueConstraintIsAcknowledged() {
        when(validator.isValid(any(), any(), any())).thenReturn(true);
        doThrow(integrityFailure("mercadopago_webhook_event.ux_mp_webhook_delivery")).when(processor).deliver(any(), any(), any());
        assertThat(resource.receive("sig", "req", "pre-1", payload("pre-1")).getStatusCodeValue()).isEqualTo(200);
    }
    @Test void unidentifiedIntegrityFailureIsRetriable() {
        when(validator.isValid(any(), any(), any())).thenReturn(true);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("unknown constraint"))
            .when(processor).deliver(any(), any(), any());
        assertThat(resource.receive("sig", "req", "pre-1", payload("pre-1")).getStatusCodeValue()).isEqualTo(503);
    }
    private org.springframework.dao.DataIntegrityViolationException integrityFailure(String constraint) {
        return new org.springframework.dao.DataIntegrityViolationException("persistence failure",
            new org.hibernate.exception.ConstraintViolationException("constraint failure",
                new java.sql.SQLException("duplicate", "23000", 1062), constraint));
    }
    @Test void logsNeverContainTheRawSignatureHeaderValue(){
        ch.qos.logback.classic.Logger logbackLogger=(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(MercadoPagoWebhookResource.class);
        ListAppender<ILoggingEvent> appender=new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            String realisticSignatureHeader="ts=1704908010,v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1";
            resource.receive(realisticSignatureHeader,"req","pre-1",payload("pre-1"));
            resource.receive("bad-signature","req","pre-1",payload("pre-1"));
            java.util.List<String> messages=appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
            assertThat(messages).isNotEmpty();
            for (String message : messages) {
                assertThat(message).doesNotContain(realisticSignatureHeader).doesNotContain("v1=").doesNotContain("bad-signature");
            }
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }
    // E2: a transient result is stored as RETRYABLE and answered 503 (same contract as provider failures) so that
    // Mercado Pago delivers it again; terminal results and duplicates are acknowledged with 200.
    @Test void transientResultAsksMercadoPagoToRetry(){
        when(validator.isValid(any(),any(),any())).thenReturn(true);
        when(processor.deliver("req","subscription_preapproval","pre-1")).thenReturn(MercadoPagoWebhookProcessor.Result.RETRY);
        assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(503);
    }
    @Test void terminalAndDuplicateResultsAreAcknowledged(){
        when(validator.isValid(any(),any(),any())).thenReturn(true);
        for (MercadoPagoWebhookProcessor.Result result : java.util.List.of(MercadoPagoWebhookProcessor.Result.PROCESSED,
            MercadoPagoWebhookProcessor.Result.IGNORED, MercadoPagoWebhookProcessor.Result.DUPLICATE)) {
            when(processor.deliver(any(),any(),any())).thenReturn(result);
            assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).as(result.name()).isEqualTo(200);
        }
    }
    @Test void rateLimitedProviderIsAnsweredWithRetry(){
        when(validator.isValid(any(),any(),any())).thenReturn(true);
        doThrow(new MercadoPagoException("rate limited",false,429,null,null,30)).when(processor).deliver(any(),any(),any());
        assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(503);
    }
    private MercadoPagoWebhookPayload payload(String id){MercadoPagoWebhookPayload p=new MercadoPagoWebhookPayload();p.setType("subscription_preapproval");MercadoPagoWebhookPayload.Data d=new MercadoPagoWebhookPayload.Data();d.setId(id);p.setData(d);return p;}
}
