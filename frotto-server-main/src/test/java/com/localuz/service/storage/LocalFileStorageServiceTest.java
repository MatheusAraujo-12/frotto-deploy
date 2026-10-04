package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class LocalFileStorageServiceTest {

    @RegisterExtension
    final StorageTestDirectory storageTestDirectory = new StorageTestDirectory();

    Path tempDir;

    private Path root;
    private StorageProperties properties;
    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = storageTestDirectory.path();
        root = tempDir.resolve("files");
        Files.createDirectories(root);
        Files.createFile(root.resolve(LocalFileStorageService.SENTINEL));
        properties = new StorageProperties();
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        properties.setRoot(root.toString());
        storage = new LocalFileStorageService(properties, new FileTypeDetector(), Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesUnderServerGeneratedKeyWithoutLeftovers() throws IOException {
        String key = storage.store(StorageCategory.AVATAR, FileTypeDetectorTest.PNG);

        assertThat(key).matches("avatars/2026/10/[0-9a-f-]{36}\\.png");
        assertThat(Files.readAllBytes(root.resolve(key))).isEqualTo(FileTypeDetectorTest.PNG);
        assertThat(storage.exists(key)).isTrue();
        assertThat(listFiles(root.resolve(LocalFileStorageService.TEMP_DIRECTORY))).isEmpty();

        StoredFileContent content = storage.open(key).orElseThrow();
        assertThat(content.getType()).contains(StoredFileType.PNG);
        assertThat(content.getSize()).isEqualTo(FileTypeDetectorTest.PNG.length);
    }

    @Test
    void extensionComesFromRealContentNotFromClientMetadata() {
        MultipartFile disguised = new MockMultipartFile("file", "photo.exe", "application/x-msdownload", FileTypeDetectorTest.JPEG);
        assertThat(storage.store(StorageCategory.CAR_DAMAGE, disguised)).endsWith(".jpg").startsWith("car-damages/");

        MultipartFile svg = new MockMultipartFile("file", "logo.png", "image/png", "<svg onload=alert(1)></svg>".getBytes());
        assertThatThrownBy(() -> storage.store(StorageCategory.LOGO, svg)).isInstanceOf(InvalidStoredFileException.class);
    }

    @Test
    void oversizedMultipartIsRejectedBeforeBeingRead() throws IOException {
        MultipartFile huge = mock(MultipartFile.class);
        when(huge.isEmpty()).thenReturn(false);
        when(huge.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> storage.store(StorageCategory.AVATAR, huge)).extracting("errorKey").isEqualTo("toolarge");
        verify(huge, never()).getBytes();
        verify(huge, never()).getInputStream();
    }

    @Test
    void refusesToWriteWithoutSentinelSoTheEphemeralFilesystemIsNeverUsed() throws IOException {
        Files.delete(root.resolve(LocalFileStorageService.SENTINEL));

        assertThat(storage.checkAvailability()).isEqualTo(LocalFileStorageService.Availability.SENTINEL_MISSING);
        assertThatThrownBy(() -> storage.store(StorageCategory.AVATAR, FileTypeDetectorTest.PNG)).isInstanceOf(StorageUnavailableException.class);
        assertThat(listFiles(root)).isEmpty();
    }

    @Test
    void reportsMissingOrUnconfiguredRoot() {
        properties.setRoot(root.resolve("not-mounted").toString());
        assertThat(storage.checkAvailability()).isEqualTo(LocalFileStorageService.Availability.ROOT_MISSING);
        assertThatThrownBy(() -> storage.store(StorageCategory.AVATAR, FileTypeDetectorTest.PNG)).isInstanceOf(StorageUnavailableException.class);
        assertThat(Files.exists(root.resolve("not-mounted"))).isFalse();

        properties.setRoot("relative/files");
        assertThat(storage.checkAvailability()).isEqualTo(LocalFileStorageService.Availability.NOT_CONFIGURED);
        properties.setRoot(" ");
        assertThat(storage.checkAvailability()).isEqualTo(LocalFileStorageService.Availability.NOT_CONFIGURED);
        properties.setRoot(null);
        assertThat(storage.isAvailable()).isFalse();
        assertThat(storage.exists("1672926360659_Car_11.png")).isFalse();
    }

    @Test
    void invalidContentIsRejectedEvenWhenStorageIsAvailable() {
        assertThatThrownBy(() -> storage.store(StorageCategory.AVATAR, "MZexecutable".getBytes())).isInstanceOf(InvalidStoredFileException.class);
        assertThat(listFiles(root)).containsExactly(LocalFileStorageService.SENTINEL);
    }

    @Test
    void readsLegacyKeysFromLegacyDirectoryButNeverDeletesThem() throws IOException {
        String legacyKey = "1672926360659_Car_11.png";
        Path legacyFile = root.resolve("legacy").resolve(legacyKey);
        Files.createDirectories(legacyFile.getParent());
        Files.write(legacyFile, FileTypeDetectorTest.PNG);

        assertThat(storage.exists(legacyKey)).isTrue();
        assertThat(storage.open(legacyKey).orElseThrow().getType()).contains(StoredFileType.PNG);
        assertThat(storage.delete(legacyKey)).isFalse();
        assertThat(Files.exists(legacyFile)).isTrue();
    }

    @Test
    void unrecognizedLegacyContentIsOpenedWithoutType() throws IOException {
        String legacyKey = "1765000000000_foto.heic";
        Path legacyFile = root.resolve("legacy").resolve(legacyKey);
        Files.createDirectories(legacyFile.getParent());
        Files.write(legacyFile, new byte[] { 0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' });

        assertThat(storage.open(legacyKey).orElseThrow().getType()).isEmpty();
    }

    @Test
    void deletesFilesCreatedByTheStorage() {
        String key = storage.store(StorageCategory.DOCUMENT, FileTypeDetectorTest.PDF);

        assertThat(storage.delete(key)).isTrue();
        assertThat(storage.exists(key)).isFalse();
        assertThat(storage.delete(key)).isFalse();
    }

    @Test
    void invalidKeysNeverReachTheFilesystem() throws IOException {
        Files.write(tempDir.resolve("outside.png"), FileTypeDetectorTest.PNG);

        for (String key : List.of("../outside.png", "..\\outside.png", root.resolve("../outside.png").toString(), ".frotto-storage", "")) {
            assertThat(storage.exists(key)).as(key).isFalse();
            assertThat(storage.open(key)).as(key).isEmpty();
            assertThat(storage.delete(key)).as(key).isFalse();
        }
        assertThat(Files.exists(tempDir.resolve("outside.png"))).isTrue();
        assertThat(Files.exists(root.resolve(LocalFileStorageService.SENTINEL))).isTrue();
    }

    @Test
    void filesAreInvisibleWhileTheSentinelIsMissing() throws IOException {
        String key = storage.store(StorageCategory.LOGO, FileTypeDetectorTest.WEBP);
        Files.delete(root.resolve(LocalFileStorageService.SENTINEL));

        assertThat(storage.exists(key)).isFalse();
        assertThat(storage.open(key)).isEmpty();
        assertThat(storage.delete(key)).isFalse();
    }

    @Test
    void symlinkEscapingTheRootIsNotServed() throws IOException {
        Path outside = Files.write(tempDir.resolve("secret.png"), FileTypeDetectorTest.PNG);
        Path link = root.resolve("legacy").resolve("1672926360659_link.png");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symbolic links not available: " + e.getClass().getSimpleName());
        }

        assertThat(storage.exists("1672926360659_link.png")).isFalse();
        assertThat(storage.open("1672926360659_link.png")).isEmpty();
    }

    private static List<String> listFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).map(path -> directory.relativize(path).toString()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
