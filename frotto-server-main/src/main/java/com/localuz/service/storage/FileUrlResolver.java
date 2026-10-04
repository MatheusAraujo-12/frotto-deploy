package com.localuz.service.storage;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * Turns a stored key into the URL a browser should load. Callers must have authorized access to the owning
 * record before asking for its URL.
 *
 * <ul>
 *   <li>{@code mode=s3} (default, current production): public legacy bucket URL - same as the frontend builds today.</li>
 *   <li>{@code mode=local}: signed {@code /files/} URL when the file exists locally; otherwise, for legacy keys and
 *       only while {@code legacy-s3.read-fallback-enabled=true}, the public legacy bucket URL (read-only, no
 *       credentials); otherwise empty.</li>
 * </ul>
 *
 * Invalid keys (absolute URLs, traversal attempts, arbitrary client strings) never produce a URL.
 */
@Service
public class FileUrlResolver {

    static final String FILES_PATH = "/files/";

    private final StorageProperties properties;
    private final LocalFileStorageService localStorage;
    private final FileUrlSigner signer;

    public FileUrlResolver(StorageProperties properties, LocalFileStorageService localStorage, FileUrlSigner signer) {
        this.properties = properties;
        this.localStorage = localStorage;
        this.signer = signer;
    }

    public Optional<String> resolve(String key) {
        Optional<StorageKeys.StorageKey> parsed = StorageKeys.parse(key);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        if (!properties.isLocalMode()) {
            return legacyBucketUrl(parsed.get());
        }
        if (localStorage.exists(key)) {
            return signedUrl(parsed.get());
        }
        if (parsed.get().isLegacy() && properties.getLegacyS3().isReadFallbackEnabled()) {
            return legacyBucketUrl(parsed.get());
        }
        return Optional.empty();
    }

    private Optional<String> signedUrl(StorageKeys.StorageKey key) {
        Optional<String> base = normalizedBaseUrl(properties.getFiles().getBaseUrl());
        Optional<FileUrlSigner.Signature> signature = signer.sign(key.getKey());
        if (base.isEmpty() || signature.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
            base.get() +
            FILES_PATH +
            encodePath(key.getKey()) +
            "?exp=" +
            signature.get().getExpiresAt() +
            "&sig=" +
            signature.get().getValue()
        );
    }

    private Optional<String> legacyBucketUrl(StorageKeys.StorageKey key) {
        if (!key.isLegacy()) {
            return Optional.empty();
        }
        return normalizedBaseUrl(properties.getLegacyS3().getPublicBaseUrl()).map(base -> base + "/" + encodePath(key.getKey()));
    }

    static String encodePath(String key) {
        return Arrays.stream(key.split("/", -1)).map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8)).collect(Collectors.joining("/"));
    }

    /** http(s) origin (optionally with a path) without trailing slash; anything else counts as not configured. */
    static Optional<String> normalizedBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            boolean http = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
            if (!http || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(trimmed);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
