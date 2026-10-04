package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.service.storage.FileTypeDetector;
import com.localuz.service.storage.FileUrlSigner;
import com.localuz.service.storage.LocalFileStorageService;
import com.localuz.service.storage.StorageCategory;
import com.localuz.service.storage.StorageProperties;
import com.localuz.service.storage.StorageTestDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriUtils;

class FileAccessResourceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1);

    @RegisterExtension
    final StorageTestDirectory storageTestDirectory = new StorageTestDirectory();

    Path root;

    private StorageProperties properties;
    private LocalFileStorageService storage;
    private FileUrlSigner signer;
    private FileAccessResource resource;

    @BeforeEach
    void setUp() throws IOException {
        root = storageTestDirectory.path();
        Files.createFile(root.resolve(LocalFileStorageService.SENTINEL));
        properties = new StorageProperties();
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        properties.setRoot(root.toString());
        properties.getFiles().setSigningSecret(SECRET);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        storage = new LocalFileStorageService(properties, new FileTypeDetector());
        signer = new FileUrlSigner(properties, clock);
        resource = new FileAccessResource(properties, storage, signer, clock);
    }

    @Test
    void servesSignedImageWithDefensiveHeaders() throws IOException {
        String key = storage.store(StorageCategory.AVATAR, PNG);

        ResponseEntity<Resource> response = resource.serve(signedRequest(key));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        try (InputStream body = response.getBody().getInputStream()) {
            assertThat(body.readAllBytes()).isEqualTo(PNG);
        }
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).hasToString("image/png");
        assertThat(headers.getContentLength()).isEqualTo(PNG.length);
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
        assertThat(headers.getCacheControl()).startsWith("private, max-age=");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void pdfIsServedInlineWithoutSandboxSoTheBrowserViewerWorks() {
        String key = storage.store(StorageCategory.DOCUMENT, PDF);

        ResponseEntity<Resource> response = resource.serve(signedRequest(key));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("application/pdf");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isNull();
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void legacyKeysWithSpacesAndAccentsAreServedFromLegacyDirectory() throws IOException {
        String key = "1765000000000_5_Foto da batida ção.png";
        Files.createDirectories(root.resolve("legacy"));
        Files.write(root.resolve("legacy").resolve(key), PNG);

        assertThat(resource.serve(signedRequest(key)).getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void unrecognizedLegacyContentIsDownloadedNeverRendered() throws IOException {
        String key = "1765000000000_foto.heic";
        Files.createDirectories(root.resolve("legacy"));
        Files.write(root.resolve("legacy").resolve(key), "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Resource> response = resource.serve(signedRequest(key));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("application/octet-stream");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
    }

    @Test
    void rejectsMissingWrongOrExpiredSignatures() {
        String key = storage.store(StorageCategory.AVATAR, PNG);
        String otherKey = storage.store(StorageCategory.AVATAR, PNG);
        FileUrlSigner.Signature signature = signer.sign(key).orElseThrow();

        assertThat(resource.serve(request(key, null, null)).getStatusCodeValue()).isEqualTo(403);
        assertThat(resource.serve(request(key, String.valueOf(signature.getExpiresAt()), "AAAA")).getStatusCodeValue()).isEqualTo(403);
        assertThat(resource.serve(request(otherKey, String.valueOf(signature.getExpiresAt()), signature.getValue())).getStatusCodeValue()).isEqualTo(403);

        FileAccessResource later = new FileAccessResource(
            properties,
            storage,
            new FileUrlSigner(properties, Clock.fixed(Instant.ofEpochSecond(signature.getExpiresAt() + 1), ZoneOffset.UTC)),
            Clock.systemUTC()
        );
        assertThat(later.serve(signedRequest(key)).getStatusCodeValue()).isEqualTo(403);
    }

    @Test
    void signatureIsCheckedBeforeExistenceSoMissingFilesAreNotRevealed() {
        String missing = "avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png";

        assertThat(resource.serve(request(missing, "1", "AAAA")).getStatusCodeValue()).isEqualTo(403);
        assertThat(resource.serve(signedRequest(missing)).getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void invalidKeysAndInternalFilesAreNotFound() {
        for (String path : new String[] {
            "/files/..%2F..%2Fetc%2Fpasswd",
            "/files/../../etc/passwd",
            "/files/.frotto-storage",
            "/files/.tmp/upload-1.part",
            "/files/legacy/1672926360659_Car_11.png",
            "/files/",
            "/files/%ZZ",
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRequestURI(path);
            assertThat(resource.serve(request).getStatusCodeValue()).as(path).isEqualTo(404);
        }
    }

    @Test
    void endpointIsDisabledOutsideLocalMode() {
        String key = storage.store(StorageCategory.AVATAR, PNG);
        properties.setMode(StorageProperties.StorageMode.S3);

        assertThat(resource.serve(signedRequest(key)).getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void errorResponsesAreNeverCached() {
        assertThat(resource.serve(request("avatars/x.png", null, null)).getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private MockHttpServletRequest signedRequest(String key) {
        FileUrlSigner.Signature signature = signer.sign(key).orElseThrow();
        return request(key, String.valueOf(signature.getExpiresAt()), signature.getValue());
    }

    private static MockHttpServletRequest request(String key, String exp, String sig) {
        String path = "/files/" + UriUtils.encodePath(key, StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (exp != null) {
            request.setParameter("exp", exp);
        }
        if (sig != null) {
            request.setParameter("sig", sig);
        }
        return request;
    }
}
