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
    private MercadoPagoProperties properties; private MercadoPagoWebhookSignatureValidator validator; private MercadoPagoWebhookProcessor processor; private MercadoPagoWebhookResource resource;
    @BeforeEach void setUp(){properties=new MercadoPagoProperties();properties.setEnabled(true);validator=mock(MercadoPagoWebhookSignatureValidator.class);processor=mock(MercadoPagoWebhookProcessor.class);resource=new MercadoPagoWebhookResource(properties,validator,processor);}
    @Test void acceptsValidDelivery(){when(validator.isValid("sig","req","pre-1")).thenReturn(true);assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(200);verify(processor).process("req","subscription_preapproval","pre-1");}
    @Test void rejectsInvalidSignature(){assertThat(resource.receive("bad","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(401);verifyNoInteractions(processor);}
    @Test void rejectsManipulatedPayload(){when(validator.isValid(any(),any(),any())).thenReturn(true);assertThat(resource.receive("sig","req","pre-1",payload("attacker-id")).getStatusCodeValue()).isEqualTo(400);verifyNoInteractions(processor);}
    @Test void retriesProviderFailure(){when(validator.isValid(any(),any(),any())).thenReturn(true);doThrow(new MercadoPagoException("temporary",false)).when(processor).process(any(),any(),any());assertThat(resource.receive("sig","req","pre-1",payload("pre-1")).getStatusCodeValue()).isEqualTo(503);}
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
    private MercadoPagoWebhookPayload payload(String id){MercadoPagoWebhookPayload p=new MercadoPagoWebhookPayload();p.setType("subscription_preapproval");MercadoPagoWebhookPayload.Data d=new MercadoPagoWebhookPayload.Data();d.setId(id);p.setData(d);return p;}
}
