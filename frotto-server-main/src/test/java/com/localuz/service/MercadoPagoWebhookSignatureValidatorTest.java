package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.localuz.config.MercadoPagoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookSignatureValidatorTest {
    private MercadoPagoProperties properties; private MercadoPagoWebhookSignatureValidator validator;
    @BeforeEach void setUp(){properties=new MercadoPagoProperties();properties.setEnabled(true);properties.setWebhookSecret("test-secret");validator=new MercadoPagoWebhookSignatureValidator(properties);}
    @Test void acceptsDocumentedManifest(){assertThat(validator.isValid("ts=1704908010,v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1","request-123","ABC123")).isTrue();}
    @Test void rejectsInvalidSignature(){assertThat(validator.isValid("ts=1704908010,v1=0000000000000000000000000000000000000000000000000000000000000000","request-123","ABC123")).isFalse();}
    @Test void rejectsMissingRequestId(){assertThat(validator.isValid("ts=1,v1=00",null,"id")).isFalse();}
    @Test void rejectsMissingDataId(){assertThat(validator.isValid("ts=1,v1=00","request",null)).isFalse();}
    @Test void rejectsMissingSecret(){properties.setWebhookSecret("");assertThat(validator.isValid("ts=1,v1=00","request","id")).isFalse();}
    @Test void rejectsWhenIntegrationDisabled(){properties.setEnabled(false);assertThat(validator.isValid("ts=1,v1=00","request","id")).isFalse();}
}
