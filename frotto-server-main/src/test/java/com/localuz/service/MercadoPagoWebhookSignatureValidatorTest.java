package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.localuz.config.MercadoPagoProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookSignatureValidatorTest {
    // Fixed manifest documented by Mercado Pago: id=ABC123, request-id=request-123, ts=1704908010.
    private static final long DOCUMENTED_TS = 1704908010L;
    private static final String VALID_SIGNATURE = "ts=1704908010,v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1";
    private static final String INVALID_SIGNATURE = "ts=1704908010,v1=0000000000000000000000000000000000000000000000000000000000000000";

    private MercadoPagoProperties properties;
    private Instant now;
    private MercadoPagoWebhookSignatureValidator validator;

    @BeforeEach void setUp(){
        properties=new MercadoPagoProperties();properties.setEnabled(true);properties.setWebhookSecret("test-secret");
        now = Instant.ofEpochSecond(DOCUMENTED_TS);
        validator = build(now);
    }

    private MercadoPagoWebhookSignatureValidator build(Instant clockNow) {
        return new MercadoPagoWebhookSignatureValidator(properties, Clock.fixed(clockNow, ZoneOffset.UTC));
    }

    @Test void acceptsDocumentedManifestWhenFresh(){assertThat(validator.isValid(VALID_SIGNATURE,"request-123","ABC123")).isTrue();}
    @Test void rejectsInvalidSignature(){assertThat(validator.isValid(INVALID_SIGNATURE,"request-123","ABC123")).isFalse();}
    @Test void rejectsMissingRequestId(){assertThat(validator.isValid("ts=1,v1=00",null,"id")).isFalse();}
    @Test void rejectsMissingDataId(){assertThat(validator.isValid("ts=1,v1=00","request",null)).isFalse();}
    @Test void rejectsMissingSecret(){properties.setWebhookSecret("");assertThat(validator.isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();}
    @Test void rejectsWhenIntegrationDisabled(){properties.setEnabled(false);assertThat(validator.isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();}

    @Test void rejectsTimestampMoreThanFiveMinutesInThePast(){
        MercadoPagoWebhookSignatureValidator stale = build(now.plusSeconds(301));
        assertThat(stale.isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();
    }
    @Test void rejectsTimestampMoreThanFiveMinutesInTheFuture(){
        MercadoPagoWebhookSignatureValidator early = build(now.minusSeconds(301));
        assertThat(early.isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();
    }
    @Test void acceptsTimestampExactlyAtTheReplayWindowBoundary(){
        // Documented behavior: the boundary (diff == window) is inclusive on both sides.
        assertThat(build(now.plusSeconds(300)).isValid(VALID_SIGNATURE,"request-123","ABC123")).isTrue();
        assertThat(build(now.minusSeconds(300)).isValid(VALID_SIGNATURE,"request-123","ABC123")).isTrue();
    }
    @Test void rejectsMissingTimestamp(){assertThat(validator.isValid("v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1","request-123","ABC123")).isFalse();}
    @Test void rejectsNonNumericTimestamp(){assertThat(validator.isValid("ts=not-a-number,v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1","request-123","ABC123")).isFalse();}
    @Test void rejectsOverflowingTimestamp(){assertThat(validator.isValid("ts=99999999999999999999,v1=a744fe8992037023c1c449de69aad9f1ab107cf0d7a8cc650511cfd1746f16e1","request-123","ABC123")).isFalse();}
    @Test void rejectsMissingV1(){assertThat(validator.isValid("ts=1704908010","request-123","ABC123")).isFalse();}

    @Test void cryptographicallyValidButStaleSignatureIsRejectedByFreshnessAlone(){
        // Same secret, same manifest inputs, correct HMAC - only the clock has moved past the window.
        MercadoPagoWebhookSignatureValidator muchLater = build(now.plus(java.time.Duration.ofDays(30)));
        assertThat(muchLater.isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();
    }

    @Test void invalidReplayWindowConfigurationFallsBackToTheDefaultInsteadOfDisablingFreshnessChecks(){
        properties.setWebhookReplayWindowSeconds(0);
        assertThat(build(now.plusSeconds(301)).isValid(VALID_SIGNATURE,"request-123","ABC123")).isFalse();
        assertThat(build(now.plusSeconds(299)).isValid(VALID_SIGNATURE,"request-123","ABC123")).isTrue();
    }
}
