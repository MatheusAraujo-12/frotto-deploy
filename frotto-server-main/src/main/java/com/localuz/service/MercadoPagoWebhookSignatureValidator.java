package com.localuz.service;

import com.localuz.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validates the exact HMAC-SHA256 manifest documented by Mercado Pago, plus a replay window
 * (5G.7) on the signed {@code ts}: a cryptographically valid signature is rejected once its
 * timestamp is more than {@link MercadoPagoProperties#getWebhookReplayWindowSeconds()} away from
 * the server clock, in either direction. Freshness alone never substitutes for the HMAC check -
 * both must pass. The replay window is a defense in depth on top of, not a replacement for,
 * MercadoPagoWebhookEvent's delivery-dedup unique constraint.
 */
@Component
public class MercadoPagoWebhookSignatureValidator {
    private static final int DEFAULT_REPLAY_WINDOW_SECONDS = 300;
    private static final int MIN_REPLAY_WINDOW_SECONDS = 1;
    private static final int MAX_REPLAY_WINDOW_SECONDS = 3600;

    private final MercadoPagoProperties properties;
    private final Clock clock;

    @Autowired
    public MercadoPagoWebhookSignatureValidator(MercadoPagoProperties properties) {
        this(properties, Clock.systemUTC());
    }

    MercadoPagoWebhookSignatureValidator(MercadoPagoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(String signature, String requestId, String dataId) {
        if (!properties.isEnabled() || !properties.hasWebhookSecret() || blank(signature) || blank(requestId) || blank(dataId)) return false;
        String timestamp=null; String supplied=null;
        for (String part : signature.split(",")) {
            String[] pair=part.trim().split("=",2);
            if (pair.length==2 && "ts".equals(pair[0])) timestamp=pair[1];
            if (pair.length==2 && "v1".equals(pair[0])) supplied=pair[1];
        }
        if (blank(timestamp) || blank(supplied)) return false;
        Long tsEpochMillis = parseTimestampEpochMillis(timestamp);
        if (tsEpochMillis == null || !fresh(tsEpochMillis)) return false;
        try {
            // The manifest MUST use the exact raw ts text from the header, never the normalized
            // epoch-millis value used above for the freshness check - Mercado Pago signs the
            // literal string it sent, whatever unit/format it happens to be in.
            String manifest="id:"+dataId.toLowerCase(Locale.ROOT)+";request-id:"+requestId+";ts:"+timestamp+";";
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] actual=java.util.HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected,actual);
        } catch (Exception ignored) { return false; }
    }

    private static final long MIN_EPOCH_SECONDS = 1_000_000_000L;
    private static final long MAX_EPOCH_SECONDS = 9_999_999_999L;
    private static final long MIN_EPOCH_MILLIS = 1_000_000_000_000L;
    private static final long MAX_EPOCH_MILLIS = 9_999_999_999_999L;

    /**
     * Mercado Pago's {@code ts} is a plain non-negative integer, but its documentation uses both
     * legacy epoch-SECONDS examples and current epoch-MILLISECONDS examples. The unit is inferred
     * from the digit count of the raw numeric string, since in the current era (years 2001-2286)
     * epoch-seconds is reliably exactly 10 digits and epoch-milliseconds is reliably exactly 13
     * digits. Anything else - blank, signed, non-numeric, overflow, or any other digit count - is
     * rejected (fail-closed) rather than guessed at. Returns the value normalized to epoch millis
     * for the freshness check only; the raw string is never altered for the HMAC manifest.
     */
    private Long parseTimestampEpochMillis(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.matches("[0-9]{10}")) {
            long seconds;
            try { seconds = Long.parseLong(raw); } catch (NumberFormatException overflow) { return null; }
            if (seconds < MIN_EPOCH_SECONDS || seconds > MAX_EPOCH_SECONDS) return null;
            return seconds * 1000L;
        }
        if (raw.matches("[0-9]{13}")) {
            long millis;
            try { millis = Long.parseLong(raw); } catch (NumberFormatException overflow) { return null; }
            if (millis < MIN_EPOCH_MILLIS || millis > MAX_EPOCH_MILLIS) return null;
            return millis;
        }
        return null;
    }

    /** Boundary is inclusive: a diff exactly equal to the configured window is accepted, regardless of which unit the ts was expressed in. */
    private boolean fresh(long tsEpochMillis) {
        long nowEpochMillis = clock.millis();
        long diffMillis = Math.abs(nowEpochMillis - tsEpochMillis);
        return diffMillis <= replayWindowSeconds() * 1000L;
    }

    private int replayWindowSeconds() {
        int configured = properties.getWebhookReplayWindowSeconds();
        return configured >= MIN_REPLAY_WINDOW_SECONDS && configured <= MAX_REPLAY_WINDOW_SECONDS
            ? configured : DEFAULT_REPLAY_WINDOW_SECONDS;
    }

    private boolean blank(String value){return value==null||value.isBlank();}
}
