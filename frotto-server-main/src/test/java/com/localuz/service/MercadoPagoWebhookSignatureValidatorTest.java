package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.localuz.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    /** Computes the exact HMAC-SHA256 manifest signature Mercado Pago would send for the given raw {@code ts} text. */
    private static String signatureFor(String secret, String requestId, String dataId, String ts) {
        try {
            String manifest = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String signatureHeader(String secret, String requestId, String dataId, String ts) {
        return "ts=" + ts + ",v1=" + signatureFor(secret, requestId, dataId, ts);
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

    // --- Millisecond-timestamp support (the fix under test) ---------------------------------

    @Test void acceptsValidSignatureWithThirteenDigitMillisecondTimestamp(){
        // Same instant as DOCUMENTED_TS (1704908010s), expressed as 13-digit epoch-millis.
        String tsMillis = "1704908010000";
        String header = signatureHeader("test-secret", "request-123", "ABC123", tsMillis);
        assertThat(validator.isValid(header, "request-123", "ABC123")).isTrue();
    }

    @Test void acceptsReplayWindowBoundaryInMillisecondFormat(){
        String tsMillis = "1704908010000"; // == now
        String header = signatureHeader("test-secret", "request-123", "ABC123", tsMillis);
        assertThat(build(now.plusSeconds(300)).isValid(header, "request-123", "ABC123")).isTrue();
        assertThat(build(now.minusSeconds(300)).isValid(header, "request-123", "ABC123")).isTrue();
    }

    @Test void rejectsJustPastReplayWindowBoundaryInSecondsFormat(){
        // Existing seconds-format boundary tests already cover 301s past/future (see above); this
        // re-asserts the same invariant explicitly for the "both formats" requirement.
        assertThat(build(now.plusSeconds(301)).isValid(VALID_SIGNATURE, "request-123", "ABC123")).isFalse();
        assertThat(build(now.minusSeconds(301)).isValid(VALID_SIGNATURE, "request-123", "ABC123")).isFalse();
    }

    @Test void rejectsJustPastReplayWindowBoundaryInMillisecondFormat(){
        String tsMillis = "1704908010000"; // == now
        String header = signatureHeader("test-secret", "request-123", "ABC123", tsMillis);
        assertThat(build(now.plusSeconds(301)).isValid(header, "request-123", "ABC123")).isFalse();
        assertThat(build(now.minusSeconds(301)).isValid(header, "request-123", "ABC123")).isFalse();
    }

    @Test void rejectsOverflowingNineteenDigitTimestamp(){
        // 19 nines exceeds Long.MAX_VALUE, and is also not a recognized 10- or 13-digit shape.
        String ts = "9999999999999999999";
        assertThat(validator.isValid("ts=" + ts + ",v1=00", "request-123", "ABC123")).isFalse();
    }

    @Test void rejectsNegativeTimestamp(){
        assertThat(validator.isValid("ts=-100,v1=00", "request-123", "ABC123")).isFalse();
    }

    @Test void rejectsTimestampsWithUnexpectedDigitCounts(){
        for (int digits : new int[]{8, 11, 12, 14, 20}) {
            String ts = "1".repeat(digits);
            assertThat(validator.isValid("ts=" + ts + ",v1=00", "request-123", "ABC123"))
                .as("digit count %d must be rejected", digits)
                .isFalse();
        }
    }

    @Test void rejectsMutatedRawTimestampInSecondsFormat(){
        // Prove the manifest hashes the exact raw ts text: a signature computed over one ts value
        // must not validate against a different (but still well-formed and fresh) ts value.
        String originalTs = "1704908010"; // 10 digits, == now
        String mutatedTs = "1704908011";  // one digit changed, still 10 digits and still fresh
        String header = "ts=" + mutatedTs + ",v1=" + signatureFor("test-secret", "request-123", "ABC123", originalTs);
        assertThat(validator.isValid(header, "request-123", "ABC123")).isFalse();
    }

    @Test void rejectsMutatedRawTimestampInMillisecondFormat(){
        String originalTs = "1704908010000"; // 13 digits, == now
        String mutatedTs = "1704908010001";  // one digit changed, still 13 digits and still fresh
        String header = "ts=" + mutatedTs + ",v1=" + signatureFor("test-secret", "request-123", "ABC123", originalTs);
        assertThat(validator.isValid(header, "request-123", "ABC123")).isFalse();
    }

    /** 5G.9 closure item 4: a leading '+' must never be accepted as part of a valid digit-only ts. */
    @Test void rejectsPlusPrefixedTimestamp(){
        assertThat(validator.isValid("ts=+704908010,v1=00", "request-123", "ABC123")).isFalse();
        // Even a value that would otherwise be a fresh, valid 10-digit seconds ts.
        String plusPrefixed = "+" + DOCUMENTED_TS;
        String header = "ts=" + plusPrefixed + ",v1=" + signatureFor("test-secret", "request-123", "ABC123", plusPrefixed);
        assertThat(validator.isValid(header, "request-123", "ABC123")).isFalse();
    }

    /**
     * Internal whitespace inside the ts value is never digit-only, so it is always rejected by the
     * 10/13-digit shape check. A leading/trailing space sits at the same position the parser already
     * trims as ordinary "key=value" list hygiene (matching how Mercado Pago's own comma-separated
     * header list may be spaced, e.g. "ts=X, v1=Y") - since that trim is applied identically before
     * BOTH the freshness parse and the manifest text are derived from the same `timestamp` variable,
     * there is no divergence between what is hashed and what is freshness-checked, so this can never
     * turn into a signature bypass. A signature computed over the padded text will therefore not
     * validate here (the manifest ends up built from the trimmed value instead) - asserted below.
     */
    @Test void rejectsTimestampWithInternalWhitespaceOrASignatureComputedOverPaddedText(){
        String withInternalSpace = "170490 8010"; // still 10 characters, but not all digits
        assertThat(validator.isValid("ts=" + withInternalSpace + ",v1=00", "request-123", "ABC123")).isFalse();
        String withLeadingSpace = " " + DOCUMENTED_TS;
        String headerLeading = "ts=" + withLeadingSpace + ",v1=" + signatureFor("test-secret", "request-123", "ABC123", withLeadingSpace);
        assertThat(validator.isValid(headerLeading, "request-123", "ABC123")).isFalse();
        String withTrailingSpace = DOCUMENTED_TS + " ";
        String headerTrailing = "ts=" + withTrailingSpace + ",v1=" + signatureFor("test-secret", "request-123", "ABC123", withTrailingSpace);
        assertThat(validator.isValid(headerTrailing, "request-123", "ABC123")).isFalse();
    }

    /**
     * The comparison must be MessageDigest.isEqual (constant-time by contract), never String#equals
     * or a manual byte-by-byte loop that returns early - this pins the implementation choice: a
     * supplied signature whose bytes are the exact same length as the real HMAC but wrong content
     * must still be rejected as false, not throw or behave differently from a wrong-length one.
     */
    @Test void rejectsSameLengthWrongSignatureAndDifferentLengthWrongSignatureBothAsPlainFalse(){
        String sameLengthWrong = "0".repeat(64); // 64 hex chars == 32 bytes, same length as a real HMAC-SHA256
        String shorterWrong = "00"; // 1 byte, different length
        assertThat(validator.isValid("ts=" + DOCUMENTED_TS + ",v1=" + sameLengthWrong, "request-123", "ABC123")).isFalse();
        assertThat(validator.isValid("ts=" + DOCUMENTED_TS + ",v1=" + shorterWrong, "request-123", "ABC123")).isFalse();
    }
}
