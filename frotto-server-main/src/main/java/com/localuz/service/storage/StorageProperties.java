package com.localuz.service.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File storage configuration ({@code frotto.storage.*}).
 *
 * <p>The default mode is {@link StorageMode#S3}: the legacy behavior. The local (VPS volume) storage is only
 * used when {@code frotto.storage.mode=local} is set explicitly, so a missing configuration can never switch the
 * storage used by production. The signing secret must never be serialized or logged (no toString on purpose).
 */
@ConfigurationProperties(prefix = "frotto.storage")
public class StorageProperties {

    private StorageMode mode = StorageMode.S3;
    /** Absolute path of the persistent storage root inside the container (bind mount target). */
    private String root;
    private final Files files = new Files();
    private final LegacyS3 legacyS3 = new LegacyS3();

    public StorageMode getMode() { return mode; }
    public void setMode(StorageMode mode) { this.mode = mode == null ? StorageMode.S3 : mode; }
    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    public Files getFiles() { return files; }
    public LegacyS3 getLegacyS3() { return legacyS3; }
    public boolean isLocalMode() { return mode == StorageMode.LOCAL; }

    public enum StorageMode {
        /** Legacy AWS S3 (current production behavior). */
        S3,
        /** Persistent local volume served through signed URLs. */
        LOCAL,
    }

    /** Signed access URLs served by {@code GET /files/**}. */
    public static class Files {

        /** Public origin that routes {@code /files/**} to the backend, e.g. https://arquivos-staging.frotto.com.br */
        private String baseUrl;
        /** HMAC-SHA256 secret (at least 32 bytes). Backend only. */
        private String signingSecret;
        private long urlTtlSeconds = 21600;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getSigningSecret() { return signingSecret; }
        public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
        public long getUrlTtlSeconds() { return urlTtlSeconds; }
        public void setUrlTtlSeconds(long urlTtlSeconds) { this.urlTtlSeconds = urlTtlSeconds; }
    }

    /** Read-only access to historical objects in the public legacy bucket. Never used for writes or deletes. */
    public static class LegacyS3 {

        private boolean readFallbackEnabled = false;
        /** Public bucket URL, e.g. https://localuz-locamais.s3.us-east-1.amazonaws.com */
        private String publicBaseUrl;

        public boolean isReadFallbackEnabled() { return readFallbackEnabled; }
        public void setReadFallbackEnabled(boolean readFallbackEnabled) { this.readFallbackEnabled = readFallbackEnabled; }
        public String getPublicBaseUrl() { return publicBaseUrl; }
        public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    }
}
