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
        Long tsEpochSeconds = parseTimestamp(timestamp);
        if (tsEpochSeconds == null || !fresh(tsEpochSeconds)) return false;
        try {
            String manifest="id:"+dataId.toLowerCase(Locale.ROOT)+";request-id:"+requestId+";ts:"+timestamp+";";
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] actual=java.util.HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected,actual);
        } catch (Exception ignored) { return false; }
    }

    /** Only a plain non-negative epoch-seconds integer is accepted; anything else (blank, signed, non-numeric, overflow) is rejected upstream. */
    private Long parseTimestamp(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,19}")) return null;
        try { return Long.parseLong(raw); } catch (NumberFormatException overflow) { return null; }
    }

    /** Boundary is inclusive: a diff exactly equal to the configured window is accepted. */
    private boolean fresh(long tsEpochSeconds) {
        long nowEpochSeconds = clock.instant().getEpochSecond();
        long diff = Math.abs(nowEpochSeconds - tsEpochSeconds);
        return diff <= replayWindowSeconds();
    }

    private int replayWindowSeconds() {
        int configured = properties.getWebhookReplayWindowSeconds();
        return configured >= MIN_REPLAY_WINDOW_SECONDS && configured <= MAX_REPLAY_WINDOW_SECONDS
            ? configured : DEFAULT_REPLAY_WINDOW_SECONDS;
    }

    private boolean blank(String value){return value==null||value.isBlank();}
}
