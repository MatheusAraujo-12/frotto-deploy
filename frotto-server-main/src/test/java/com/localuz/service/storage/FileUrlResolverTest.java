package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUrlResolverTest {

    private static final String BUCKET = "https://localuz-locamais.s3.us-east-1.amazonaws.com";
    private static final String FILES = "https://arquivos-staging.frotto.com.br";
    private static final String LEGACY = "1672926360659_Car_11.png";
    private static final String NEW_KEY = "car-damages/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.jpg";

    private StorageProperties properties;
    private LocalFileStorageService localStorage;
    private FileUrlSigner signer;
    private FileUrlResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.getLegacyS3().setPublicBaseUrl(BUCKET + "/");
        properties.getFiles().setBaseUrl(FILES);
        properties.getFiles().setSigningSecret(FileUrlSignerTest.SECRET);
        localStorage = mock(LocalFileStorageService.class);
        signer = new FileUrlSigner(properties, Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
        resolver = new FileUrlResolver(properties, localStorage, signer);
    }

    @Test
    void defaultModeKeepsTheLegacyBucketUrlAndNeverTouchesLocalStorage() {
        assertThat(properties.getMode()).isEqualTo(StorageProperties.StorageMode.S3);
        assertThat(resolver.resolve(LEGACY)).contains(BUCKET + "/" + LEGACY);
        assertThat(resolver.resolve("1765000000000_5_foto batida.jpg")).contains(BUCKET + "/1765000000000_5_foto%20batida.jpg");
        assertThat(resolver.resolve(NEW_KEY)).isEmpty();
        verify(localStorage, never()).exists(anyString());
    }

    @Test
    void localModeServesExistingFilesThroughSignedUrls() {
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        when(localStorage.exists(NEW_KEY)).thenReturn(true);

        String url = resolver.resolve(NEW_KEY).orElseThrow();

        assertThat(url).startsWith(FILES + "/files/" + NEW_KEY + "?exp=").contains("&sig=");
        String exp = url.replaceAll(".*\\?exp=([0-9]+)&sig=.*", "$1");
        String sig = url.replaceAll(".*&sig=", "");
        assertThat(signer.verify(NEW_KEY, exp, sig)).isTrue();
    }

    @Test
    void localCopiesOfLegacyKeysWinOverTheBucket() {
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        properties.getLegacyS3().setReadFallbackEnabled(true);
        when(localStorage.exists(LEGACY)).thenReturn(true);

        assertThat(resolver.resolve(LEGACY).orElseThrow()).startsWith(FILES + "/files/" + LEGACY + "?exp=");
    }

    @Test
    void legacyKeysMissingLocallyFallBackToTheBucketOnlyWhenEnabled() {
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        when(localStorage.exists(LEGACY)).thenReturn(false);

        assertThat(resolver.resolve(LEGACY)).isEmpty();

        properties.getLegacyS3().setReadFallbackEnabled(true);
        assertThat(resolver.resolve(LEGACY)).contains(BUCKET + "/" + LEGACY);
    }

    @Test
    void newKeysNeverFallBackToTheBucket() {
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        properties.getLegacyS3().setReadFallbackEnabled(true);
        when(localStorage.exists(NEW_KEY)).thenReturn(false);

        assertThat(resolver.resolve(NEW_KEY)).isEmpty();
    }

    @Test
    void noUrlWithoutSigningSecretOrBaseUrl() {
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        when(localStorage.exists(NEW_KEY)).thenReturn(true);

        properties.getFiles().setSigningSecret(null);
        assertThat(resolver.resolve(NEW_KEY)).isEmpty();

        properties.getFiles().setSigningSecret(FileUrlSignerTest.SECRET);
        for (String base : new String[] { null, "", "javascript:alert(1)", "ftp://files", "https://files?x=1", "https://user@files", "/files" }) {
            properties.getFiles().setBaseUrl(base);
            assertThat(resolver.resolve(NEW_KEY)).as(String.valueOf(base)).isEmpty();
        }
    }

    @Test
    void clientControlledStringsNeverBecomeUrls() {
        properties.getLegacyS3().setReadFallbackEnabled(true);
        for (StorageProperties.StorageMode mode : StorageProperties.StorageMode.values()) {
            properties.setMode(mode);
            for (String value : new String[] { null, "", "https://evil.example/x.png", "../../etc/passwd", "javascript:alert(1)", "/avatar.png" }) {
                assertThat(resolver.resolve(value)).as(mode + " " + value).isEmpty();
            }
        }
        verify(localStorage, never()).exists(anyString());
    }
}
