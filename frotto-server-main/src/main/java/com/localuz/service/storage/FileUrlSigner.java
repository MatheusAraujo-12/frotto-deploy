package com.localuz.service.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 signatures for {@code GET /files/{key}?exp=...&sig=...}.
 *
 * <p>Authorization happens when a URL is issued (the caller already checked ownership); the signature only proves
 * that the backend issued it and that it has not expired. Expirations are aligned to TTL-sized windows so the same
 * key yields the same URL for a while (browser cache friendly): a URL stays valid between one and two TTLs.
 * Expirations further in the future than that are rejected, so lowering the TTL also shortens existing links.
 */
@Component
public class FileUrlSigner {

    static final int MIN_SECRET_BYTES = 32;
    static final long DEFAULT_TTL_SECONDS = 21600;
    static final long MIN_TTL_SECONDS = 300;
    static final long MAX_TTL_SECONDS = 604800;
    static final long CLOCK_SKEW_SECONDS = 60;
    private static final String ALGORITHM = "HmacSHA256";

    private final StorageProperties properties;
    private final Clock clock;

    @Autowired
    public FileUrlSigner(StorageProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public FileUrlSigner(StorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return secretBytes().isPresent();
    }

    /** Signature parameters for a key, or empty when no valid secret is configured. */
    public Optional<Signature> sign(String key) {
        Optional<byte[]> secret = secretBytes();
        if (secret.isEmpty() || key == null || key.isEmpty()) {
            return Optional.empty();
        }
        long ttl = ttlSeconds();
        long now = clock.instant().getEpochSecond();
        long expiresAt = (now / ttl + 2) * ttl;
        return Optional.of(new Signature(expiresAt, encode(hmac(secret.get(), key, expiresAt))));
    }

    public boolean verify(String key, String expiresAt, String signature) {
        Optional<byte[]> secret = secretBytes();
        if (secret.isEmpty() || key == null || key.isEmpty() || expiresAt == null || signature == null) {
            return false;
        }
        long exp;
        byte[] provided;
        try {
            exp = Long.parseLong(expiresAt);
            provided = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        long now = clock.instant().getEpochSecond();
        if (exp < now || exp > now + 2 * ttlSeconds() + CLOCK_SKEW_SECONDS) {
            return false;
        }
        return MessageDigest.isEqual(hmac(secret.get(), key, exp), provided);
    }

    long ttlSeconds() {
        long configured = properties.getFiles().getUrlTtlSeconds();
        return configured < MIN_TTL_SECONDS || configured > MAX_TTL_SECONDS ? DEFAULT_TTL_SECONDS : configured;
    }

    private Optional<byte[]> secretBytes() {
        String secret = properties.getFiles().getSigningSecret();
        if (secret == null) {
            return Optional.empty();
        }
        byte[] bytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        return bytes.length >= MIN_SECRET_BYTES ? Optional.of(bytes) : Optional.empty();
    }

    private static byte[] hmac(byte[] secret, String key, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(("v1\n" + key + "\n" + expiresAt).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static final class Signature {

        private final long expiresAt;
        private final String value;

        Signature(long expiresAt, String value) {
            this.expiresAt = expiresAt;
            this.value = value;
        }

        public long getExpiresAt() { return expiresAt; }
        public String getValue() { return value; }
    }
}
