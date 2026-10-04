package com.localuz.service.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

/**
 * Mode-aware entry point for application flows (avatar, logo; later damages and documents).
 *
 * <ul>
 *   <li>{@code mode=s3} (default): exactly the legacy behavior through {@link LegacyS3FileStorageService}.</li>
 *   <li>{@code mode=local}: {@link LocalFileStorageService} only. There is no write or delete path to S3 in this
 *       mode, not even as a fallback; historical keys are only ever read from the public bucket by browsers.</li>
 * </ul>
 */
@Service
public class FileStorageGateway {

    private static final Pattern LEGACY_BUCKET_HOST = Pattern.compile("^[a-z0-9.-]+\\.s3(\\.[a-z0-9-]+)?\\.amazonaws\\.com$");

    private final StorageProperties properties;
    private final LocalFileStorageService localStorage;
    private final LegacyS3FileStorageService legacyS3;
    private final FileUrlResolver urlResolver;
    private final StorageTransactionSupport transactions;

    public FileStorageGateway(
        StorageProperties properties,
        LocalFileStorageService localStorage,
        LegacyS3FileStorageService legacyS3,
        FileUrlResolver urlResolver,
        StorageTransactionSupport transactions
    ) {
        this.properties = properties;
        this.localStorage = localStorage;
        this.legacyS3 = legacyS3;
        this.urlResolver = urlResolver;
        this.transactions = transactions;
    }

    public boolean isLocalMode() {
        return properties.isLocalMode();
    }

    /**
     * Stores an upload and returns its key. In local mode the content is validated by its real type and size
     * ({@link InvalidStoredFileException} / {@link StorageUnavailableException}); in s3 mode the legacy service keeps
     * its historical key ({@code {millis}_{legacyIdentifier}.{ext}}) and may return an empty string.
     */
    public String store(StorageCategory category, MultipartFile file, String legacyIdentifier) {
        if (isLocalMode()) {
            return localStorage.store(category, file);
        }
        return legacyS3.store(file, legacyIdentifier);
    }

    /**
     * Same as {@link #store(StorageCategory, MultipartFile, String)} for a Base64 payload (optionally a data: URL).
     * In local mode the declared media type is ignored: the decoded content is validated by its real type and size,
     * and oversized payloads are rejected before decoding.
     */
    public String storeBase64(StorageCategory category, String base64Data, String legacyIdentifier) {
        if (isLocalMode()) {
            return localStorage.store(category, decodeBase64(category, base64Data));
        }
        return legacyS3.storeBase64(base64Data, legacyIdentifier);
    }

    /** Local mode: removes a just-stored file if the transaction that would reference it rolls back. */
    public void deleteOnRollback(String key) {
        if (isLocalMode() && key != null) {
            transactions.runAfterRollback(() -> localStorage.delete(key));
        }
    }

    /**
     * Local mode: removes a file no longer referenced, once the change is committed. Historical keys and values that
     * are not valid keys are never deleted (see {@link LocalFileStorageService#delete}). No-op in s3 mode.
     */
    public void deleteAfterCommit(String key) {
        if (isLocalMode() && key != null) {
            transactions.runAfterCommit(() -> localStorage.delete(key));
        }
    }

    /** s3 mode only: the legacy immediate S3 delete. Refused in local mode, so S3 is never written from there. */
    public void deleteFromLegacyS3(String key) {
        if (isLocalMode()) {
            throw new IllegalStateException("S3 deletes are disabled in local storage mode");
        }
        legacyS3.delete(key);
    }

    /**
     * URL a browser should load for a stored value (signed local URL, public legacy bucket URL or empty). Historical
     * rows may hold the full URL of an object of the legacy bucket instead of its key; those are read as that key.
     */
    public Optional<String> resolveUrl(String storedValue) {
        return urlResolver.resolve(legacyBucketKey(storedValue).orElse(storedValue));
    }

    /**
     * Value for the API's resolved-URL fields: the resolved URL or, when there is none, "" in local mode (explicit
     * "no image") and null in s3 mode (field omitted, so the client keeps its legacy resolution of the stored key).
     */
    public String displayUrl(String storedValue) {
        return resolveUrl(storedValue).orElse(isLocalMode() ? "" : null);
    }

    static byte[] decodeBase64(StorageCategory category, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidStoredFileException("empty", "Arquivo vazio");
        }
        String payload = value.trim();
        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            payload = comma < 0 ? "" : payload.substring(comma + 1);
        }
        long maxEncodedLength = ((category.getMaxBytes() + 2) / 3) * 4;
        if (payload.length() > maxEncodedLength) {
            throw new InvalidStoredFileException("toolarge", "Arquivo excede o tamanho máximo permitido");
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new InvalidStoredFileException("invalidtype", "Tipo de arquivo não permitido");
        }
    }

    /**
     * Key of a full URL pointing at the configured legacy bucket (with or without the region in the host), e.g.
     * https://localuz-locamais.s3.amazonaws.com/1771248962905_USER_4.png. Any other URL is not a stored file.
     */
    Optional<String> legacyBucketKey(String value) {
        String base = properties.getLegacyS3().getPublicBaseUrl();
        if (value == null || base == null || base.isBlank() || !value.startsWith("https://")) {
            return Optional.empty();
        }
        try {
            String bucket = URI.create(base.trim()).getHost().split("\\.")[0];
            URI uri = URI.create(value.trim());
            boolean bucketHost =
                uri.getHost() != null && LEGACY_BUCKET_HOST.matcher(uri.getHost()).matches() && uri.getHost().startsWith(bucket + ".");
            if (!bucketHost || uri.getRawQuery() != null || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
                return Optional.empty();
            }
            return Optional.of(UriUtils.decode(uri.getRawPath().substring(1), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
