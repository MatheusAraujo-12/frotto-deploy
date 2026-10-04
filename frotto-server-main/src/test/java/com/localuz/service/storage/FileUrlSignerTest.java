package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUrlSignerTest {

    static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final String KEY = "avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png";
    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.getFiles().setSigningSecret(SECRET);
        properties.getFiles().setUrlTtlSeconds(3600);
    }

    @Test
    void signedUrlVerifiesForTheSameKey() {
        FileUrlSigner signer = signerAt(NOW);
        FileUrlSigner.Signature signature = signer.sign(KEY).orElseThrow();

        assertThat(signature.getValue()).matches("[A-Za-z0-9_-]{43}");
        assertThat(signer.verify(KEY, String.valueOf(signature.getExpiresAt()), signature.getValue())).isTrue();
    }

    @Test
    void expirationIsAlignedToWindowsAndValidBetweenOneAndTwoTtls() {
        FileUrlSigner.Signature first = signerAt(NOW).sign(KEY).orElseThrow();
        FileUrlSigner.Signature tenMinutesLater = signerAt(NOW.plusSeconds(600)).sign(KEY).orElseThrow();

        assertThat(tenMinutesLater.getValue()).isEqualTo(first.getValue());
        assertThat(first.getExpiresAt() - NOW.getEpochSecond()).isBetween(3600L, 7200L);
    }

    @Test
    void rejectsTamperedKeySignatureOrExpiration() {
        FileUrlSigner signer = signerAt(NOW);
        FileUrlSigner.Signature signature = signer.sign(KEY).orElseThrow();
        String exp = String.valueOf(signature.getExpiresAt());

        assertThat(signer.verify(KEY.replace(".png", ".jpg"), exp, signature.getValue())).isFalse();
        assertThat(signer.verify("1771248962905_USER_4.png", exp, signature.getValue())).isFalse();
        assertThat(signer.verify(KEY, String.valueOf(signature.getExpiresAt() - 1), signature.getValue())).isFalse();
        String flipped = (signature.getValue().charAt(0) == 'A' ? "B" : "A") + signature.getValue().substring(1);
        assertThat(signer.verify(KEY, exp, flipped)).isFalse();
    }

    @Test
    void rejectsMissingAndMalformedParameters() {
        FileUrlSigner signer = signerAt(NOW);
        FileUrlSigner.Signature signature = signer.sign(KEY).orElseThrow();
        String exp = String.valueOf(signature.getExpiresAt());

        assertThat(signer.verify(KEY, null, signature.getValue())).isFalse();
        assertThat(signer.verify(KEY, exp, null)).isFalse();
        assertThat(signer.verify(KEY, "not-a-number", signature.getValue())).isFalse();
        assertThat(signer.verify(KEY, exp, "%%%not-base64%%%")).isFalse();
        assertThat(signer.verify(KEY, exp, "")).isFalse();
        assertThat(signer.verify(null, exp, signature.getValue())).isFalse();
    }

    @Test
    void rejectsExpiredUrls() {
        FileUrlSigner.Signature signature = signerAt(NOW).sign(KEY).orElseThrow();
        FileUrlSigner later = signerAt(Instant.ofEpochSecond(signature.getExpiresAt() + 1));

        assertThat(later.verify(KEY, String.valueOf(signature.getExpiresAt()), signature.getValue())).isFalse();
    }

    @Test
    void rejectsExpirationsBeyondTheCurrentTtl() {
        FileUrlSigner.Signature longLived = signerAt(NOW).sign(KEY).orElseThrow();
        properties.getFiles().setUrlTtlSeconds(300);

        assertThat(signerAt(NOW).verify(KEY, String.valueOf(longLived.getExpiresAt()), longLived.getValue())).isFalse();
    }

    @Test
    void signaturesFromAnotherSecretAreRejected() {
        FileUrlSigner.Signature signature = signerAt(NOW).sign(KEY).orElseThrow();
        properties.getFiles().setSigningSecret("another-signing-secret-0123456789abcdefgh");

        assertThat(signerAt(NOW).verify(KEY, String.valueOf(signature.getExpiresAt()), signature.getValue())).isFalse();
    }

    @Test
    void missingOrShortSecretDisablesSigningAndVerification() {
        FileUrlSigner.Signature signature = signerAt(NOW).sign(KEY).orElseThrow();
        for (String secret : new String[] { null, "", "   ", "short-secret" }) {
            properties.getFiles().setSigningSecret(secret);
            FileUrlSigner signer = signerAt(NOW);
            assertThat(signer.isConfigured()).isFalse();
            assertThat(signer.sign(KEY)).isEmpty();
            assertThat(signer.verify(KEY, String.valueOf(signature.getExpiresAt()), signature.getValue())).isFalse();
        }
    }

    @Test
    void outOfRangeTtlFallsBackToDefault() {
        properties.getFiles().setUrlTtlSeconds(0);
        assertThat(signerAt(NOW).ttlSeconds()).isEqualTo(FileUrlSigner.DEFAULT_TTL_SECONDS);
        properties.getFiles().setUrlTtlSeconds(30L * 24 * 3600);
        assertThat(signerAt(NOW).ttlSeconds()).isEqualTo(FileUrlSigner.DEFAULT_TTL_SECONDS);
    }

    private FileUrlSigner signerAt(Instant instant) {
        return new FileUrlSigner(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
