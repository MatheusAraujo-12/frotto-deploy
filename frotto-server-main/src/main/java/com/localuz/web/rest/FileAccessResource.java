package com.localuz.web.rest;

import com.localuz.service.storage.FileStorageService;
import com.localuz.service.storage.FileUrlSigner;
import com.localuz.service.storage.LocalFileStorageService;
import com.localuz.service.storage.StorageKeys;
import com.localuz.service.storage.StorageProperties;
import com.localuz.service.storage.StoredFileContent;
import com.localuz.service.storage.StoredFileType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/**
 * Serves files of the local storage through URLs signed by {@link FileUrlSigner}: {@code GET /files/{key}?exp&sig}.
 *
 * <p>No JWT is involved (the URLs are used directly in {@code <img src>} on a dedicated files domain); the
 * signature is checked before the filesystem is touched. Only active when {@code frotto.storage.mode=local}.
 * The query parameters are read from the request on purpose, so the logging aspect never logs a signature.
 */
@RestController
public class FileAccessResource {

    static final String FILES_PREFIX = "/files/";

    private final StorageProperties properties;
    private final FileStorageService storage;
    private final FileUrlSigner signer;
    private final Clock clock;

    @Autowired
    public FileAccessResource(StorageProperties properties, LocalFileStorageService storage, FileUrlSigner signer) {
        this(properties, storage, signer, Clock.systemUTC());
    }

    FileAccessResource(StorageProperties properties, FileStorageService storage, FileUrlSigner signer, Clock clock) {
        this.properties = properties;
        this.storage = storage;
        this.signer = signer;
        this.clock = clock;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        if (!properties.isLocalMode()) {
            return status(HttpStatus.NOT_FOUND);
        }
        Optional<String> key = extractKey(request);
        if (key.isEmpty() || StorageKeys.parse(key.get()).isEmpty()) {
            return status(HttpStatus.NOT_FOUND);
        }
        String expiresAt = request.getParameter("exp");
        if (!signer.verify(key.get(), expiresAt, request.getParameter("sig"))) {
            return status(HttpStatus.FORBIDDEN);
        }
        Optional<StoredFileContent> content = storage.open(key.get());
        if (content.isEmpty()) {
            return status(HttpStatus.NOT_FOUND);
        }
        long maxAge = Math.max(0, Long.parseLong(expiresAt) - clock.instant().getEpochSecond());
        Optional<StoredFileType> type = content.get().getType();
        HttpHeaders headers = securityHeaders();
        headers.setCacheControl("private, max-age=" + maxAge);
        if (type.isPresent()) {
            headers.setContentType(MediaType.parseMediaType(type.get().getMimeType()));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        } else {
            // Unrecognized legacy content (e.g. HEIC): never rendered by the browser.
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }
        if (type.isEmpty() || type.get().isImage()) {
            // Not applied to PDFs: a sandboxed document cannot use the browser's built-in PDF viewer.
            headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        }
        return ResponseEntity.ok().headers(headers).contentLength(content.get().getSize()).body(content.get().getResource());
    }

    static Optional<String> extractKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        if (uri == null || !uri.startsWith(contextPath + FILES_PREFIX)) {
            return Optional.empty();
        }
        String encoded = uri.substring(contextPath.length() + FILES_PREFIX.length());
        try {
            return Optional.of(UriUtils.decode(encoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<Resource> status(HttpStatus status) {
        return ResponseEntity.status(status).headers(securityHeaders()).cacheControl(CacheControl.noStore()).build();
    }

    private static HttpHeaders securityHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Robots-Tag", "noindex, nofollow");
        // The signed URL is the capability; no cookies or credentials are ever honored here.
        headers.set("Cross-Origin-Resource-Policy", "cross-origin");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        return headers;
    }
}
