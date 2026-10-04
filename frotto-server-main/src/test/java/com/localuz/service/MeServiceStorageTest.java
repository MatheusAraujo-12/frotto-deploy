package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.User;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.MeResponseDTO;
import com.localuz.service.dto.UpdatePersonalDTO;
import com.localuz.service.mapper.MeMapper;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.service.storage.FileTypeDetector;
import com.localuz.service.storage.FileUrlResolver;
import com.localuz.service.storage.FileUrlSigner;
import com.localuz.service.storage.InvalidStoredFileException;
import com.localuz.service.storage.LegacyS3FileStorageService;
import com.localuz.service.storage.LocalFileStorageService;
import com.localuz.service.storage.StorageProperties;
import com.localuz.service.storage.StorageTestDirectory;
import com.localuz.service.storage.StorageTransactionSupport;
import com.localuz.service.storage.StorageUnavailableException;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Avatar and logo storage (Etapa 3): local mode on a real temporary volume, s3 mode on the legacy service, with real
 * Spring transaction synchronization (commit, rollback, unknown commit outcome).
 */
class MeServiceStorageTest {

    static final String BUCKET = "https://localuz-locamais.s3.us-east-1.amazonaws.com";
    static final String FILES = "https://arquivos-staging.frotto.com.br";
    static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    static final String LOGIN = "owner@frotto.test";
    static final String LEGACY_AVATAR = "1771248962905_USER_7.png";
    static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1);

    /** Gateway in the default (s3) mode around a legacy service mock; also used by MeServiceLogoTest. */
    static FileStorageGateway s3ModeGateway(AWSS3FileService s3) {
        StorageProperties properties = new StorageProperties();
        properties.getLegacyS3().setPublicBaseUrl(BUCKET);
        LocalFileStorageService local = new LocalFileStorageService(properties, new FileTypeDetector());
        return new FileStorageGateway(
            properties,
            local,
            new LegacyS3FileStorageService(s3),
            new FileUrlResolver(properties, local, new FileUrlSigner(properties)),
            new StorageTransactionSupport()
        );
    }

    @RegisterExtension
    final StorageTestDirectory storageTestDirectory = new StorageTestDirectory();

    Path tempDir;

    private Path root;
    private StorageProperties properties;
    private FileUrlSigner signer;
    private FileStorageGateway gateway;
    private AWSS3FileService s3;
    private UserRepository userRepository;
    private User user;
    private MeService service;
    private TestTransactionManager transactionManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = storageTestDirectory.path();
        root = tempDir.resolve("files");
        Files.createDirectories(root);
        Files.createFile(root.resolve(LocalFileStorageService.SENTINEL));

        properties = new StorageProperties();
        properties.setMode(StorageProperties.StorageMode.LOCAL);
        properties.setRoot(root.toString());
        properties.getFiles().setBaseUrl(FILES);
        properties.getFiles().setSigningSecret(SECRET);
        properties.getLegacyS3().setPublicBaseUrl(BUCKET);

        s3 = mock(AWSS3FileService.class);
        LocalFileStorageService local = new LocalFileStorageService(properties, new FileTypeDetector());
        signer = new FileUrlSigner(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        gateway =
            new FileStorageGateway(
                properties,
                local,
                new LegacyS3FileStorageService(s3),
                new FileUrlResolver(properties, local, signer),
                new StorageTransactionSupport()
            );

        userRepository = mock(UserRepository.class);
        user = new User();
        user.setId(7L);
        user.setLogin(LOGIN);
        when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MeService(userRepository, mock(PasswordEncoder.class), new MeMapper(), gateway);
        transactionManager = new TestTransactionManager();
    }

    @Nested
    class LocalMode {

        @ParameterizedTest
        @ValueSource(strings = { "png", "jpg", "webp" })
        void avatarUploadStoresLocallyAndReturnsSignedUrl(String format) {
            MeResponseDTO result = inTransaction(() -> service.uploadAvatar(LOGIN, image(format)));

            String key = user.getImageUrl();
            assertThat(key).matches("avatars/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\." + format);
            assertThat(root.resolve(key)).exists();
            assertThat(result.getImageUrl()).isEqualTo(key);
            assertSignedUrl(result.getAvatarUrl(), key);
            verifyNoInteractions(s3);
        }

        @ParameterizedTest
        @ValueSource(strings = { "png", "jpg", "webp" })
        void logoUploadStoresLocallyWithoutTouchingTheAvatar(String format) {
            user.setImageUrl(LEGACY_AVATAR);

            MeResponseDTO result = inTransaction(() -> service.uploadLogo(LOGIN, image(format)));

            String key = user.getLogoUrl();
            assertThat(key).matches("logos/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\." + format);
            assertThat(root.resolve(key)).exists();
            assertSignedUrl(result.getLogoAccessUrl(), key);
            assertThat(user.getImageUrl()).isEqualTo(LEGACY_AVATAR);
            verifyNoInteractions(s3);
        }

        @Test
        void reloadReturnsAValidSignedUrlThatLaterExpires() {
            inTransaction(() -> service.uploadLogo(LOGIN, image("png")));

            MeResponseDTO reloaded = service.getMe(LOGIN);

            assertSignedUrl(reloaded.getLogoAccessUrl(), user.getLogoUrl());
            String[] parts = signatureParts(reloaded.getLogoAccessUrl());
            FileUrlSigner afterExpiry = new FileUrlSigner(properties, Clock.fixed(Instant.ofEpochSecond(Long.parseLong(parts[0]) + 1), ZoneOffset.UTC));
            assertThat(afterExpiry.verify(user.getLogoUrl(), parts[0], parts[1])).isFalse();
        }

        @Test
        void contentThatIsNotAnAllowedImageIsRejectedWithoutChangingAnything() {
            user.setImageUrl(LEGACY_AVATAR);
            MockMultipartFile disguisedPdf = new MockMultipartFile("file", "avatar.png", "image/png", PDF);
            MockMultipartFile script = new MockMultipartFile("file", "avatar.png", "image/png", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));
            MockMultipartFile declaredPdf = new MockMultipartFile("file", "logo.pdf", "application/pdf", PDF);

            assertThatThrownBy(() -> inTransaction(() -> service.uploadAvatar(LOGIN, disguisedPdf))).isInstanceOf(InvalidStoredFileException.class);
            assertThatThrownBy(() -> inTransaction(() -> service.uploadLogo(LOGIN, disguisedPdf))).isInstanceOf(InvalidStoredFileException.class);
            assertThatThrownBy(() -> inTransaction(() -> service.uploadAvatar(LOGIN, script))).isInstanceOf(InvalidStoredFileException.class);
            assertThatThrownBy(() -> inTransaction(() -> service.uploadLogo(LOGIN, declaredPdf))).isInstanceOf(BadRequestAlertException.class);

            assertThat(user.getImageUrl()).isEqualTo(LEGACY_AVATAR);
            assertThat(user.getLogoUrl()).isNull();
            assertThat(storedFiles()).isEmpty();
            verify(userRepository, never()).save(any());
            verifyNoInteractions(s3);
        }

        @Test
        void fiveMegabytesIsTheLimitForAvatarAndLogo() {
            byte[] exactly5Mb = Arrays.copyOf(PNG, 5 * 1024 * 1024);
            byte[] over5Mb = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);

            inTransaction(() -> service.uploadAvatar(LOGIN, new MockMultipartFile("file", "a.png", "image/png", exactly5Mb)));
            assertThat(user.getImageUrl()).startsWith("avatars/");

            assertThatThrownBy(() -> inTransaction(() -> service.uploadLogo(LOGIN, new MockMultipartFile("file", "l.png", "image/png", over5Mb))))
                .isInstanceOf(InvalidStoredFileException.class)
                .extracting("errorKey")
                .isEqualTo("toolarge");
            assertThat(user.getLogoUrl()).isNull();
        }

        @Test
        void replacementKeepsThePreviousFileUntilCommitThenDeletesIt() {
            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));
            String previous = user.getImageUrl();

            inTransaction(() -> {
                MeResponseDTO result = service.uploadAvatar(LOGIN, image("jpg"));
                assertThat(root.resolve(previous)).as("previous file before commit").exists();
                assertThat(root.resolve(user.getImageUrl())).as("new file before commit").exists();
                return result;
            });

            assertThat(root.resolve(previous)).doesNotExist();
            assertThat(root.resolve(user.getImageUrl())).exists();
            assertThat(storedFiles()).containsExactly(user.getImageUrl());
        }

        @Test
        void databaseFailureAfterUploadRollsBackAndRemovesOnlyTheNewFile() {
            inTransaction(() -> service.uploadLogo(LOGIN, image("png")));
            String previous = user.getLogoUrl();
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("database down"));

            assertThatThrownBy(() -> inTransaction(() -> service.uploadLogo(LOGIN, image("webp")))).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedFiles()).containsExactly(previous);
        }

        @Test
        void unknownCommitOutcomeKeepsBothFilesSoNoRowPointsToAMissingFile() {
            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));
            String previous = user.getImageUrl();
            transactionManager.failOnCommit = true;

            assertThatThrownBy(() -> inTransaction(() -> service.uploadAvatar(LOGIN, image("jpg")))).isInstanceOf(TransactionSystemException.class);

            assertThat(storedFiles()).hasSize(2).contains(previous);
        }

        @Test
        void removalDeletesTheFileOnlyAfterCommit() {
            inTransaction(() -> service.uploadLogo(LOGIN, image("png")));
            String key = user.getLogoUrl();

            MeResponseDTO result = inTransaction(() -> {
                MeResponseDTO removed = service.removeLogo(LOGIN);
                assertThat(root.resolve(key)).as("before commit").exists();
                return removed;
            });

            assertThat(result.getLogoUrl()).isNull();
            assertThat(result.getLogoAccessUrl()).isEmpty();
            assertThat(root.resolve(key)).doesNotExist();
            verifyNoInteractions(s3);
        }

        @Test
        void rolledBackRemovalKeepsTheFile() {
            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));
            String key = user.getImageUrl();
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("database down"));

            assertThatThrownBy(() -> inTransaction(() -> service.removeAvatar(LOGIN))).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(root.resolve(key)).exists();
        }

        @Test
        void unavailableStorageAnswers503WithoutTouchingDatabaseOrPreviousFile() throws IOException {
            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));
            String previous = user.getImageUrl();
            Files.delete(root.resolve(LocalFileStorageService.SENTINEL));
            org.mockito.Mockito.clearInvocations(userRepository);

            assertThatThrownBy(() -> inTransaction(() -> service.uploadAvatar(LOGIN, image("jpg")))).isInstanceOf(StorageUnavailableException.class);

            assertThat(user.getImageUrl()).isEqualTo(previous);
            assertThat(root.resolve(previous)).exists();
            verify(userRepository, never()).save(any());
            verifyNoInteractions(s3);
        }

        @Test
        void missingVolumeNeverCreatesDirectoriesOrFallsBackToS3() {
            properties.setRoot(tempDir.resolve("not-mounted").toString());

            assertThatThrownBy(() -> inTransaction(() -> service.uploadLogo(LOGIN, image("png")))).isInstanceOf(StorageUnavailableException.class);

            assertThat(tempDir.resolve("not-mounted")).doesNotExist();
            verifyNoInteractions(s3);
        }

        @Test
        void historicalKeyPresentLocallyIsServedThroughASignedUrl() throws IOException {
            writeLegacy(LEGACY_AVATAR);
            user.setImageUrl(LEGACY_AVATAR);

            assertSignedUrl(service.getMe(LOGIN).getAvatarUrl(), LEGACY_AVATAR);
        }

        @Test
        void historicalKeyOnlyInS3UsesThePublicBucketOnlyWhenFallbackIsEnabled() {
            user.setImageUrl(LEGACY_AVATAR);
            user.setLogoUrl("1771248962905_LOGO_7.webp");

            MeResponseDTO withoutFallback = service.getMe(LOGIN);
            assertThat(withoutFallback.getAvatarUrl()).isEmpty();
            assertThat(withoutFallback.getLogoAccessUrl()).isEmpty();
            assertThat(withoutFallback.getImageUrl()).isEqualTo(LEGACY_AVATAR);

            properties.getLegacyS3().setReadFallbackEnabled(true);
            MeResponseDTO withFallback = service.getMe(LOGIN);
            assertThat(withFallback.getAvatarUrl()).isEqualTo(BUCKET + "/" + LEGACY_AVATAR);
            assertThat(withFallback.getLogoAccessUrl()).isEqualTo(BUCKET + "/1771248962905_LOGO_7.webp");
            verifyNoInteractions(s3);
        }

        @Test
        void replacingOrRemovingHistoricalFilesNeverDeletesFromS3OrTheLegacyCopy() throws IOException {
            Path legacyCopy = writeLegacy(LEGACY_AVATAR);
            user.setImageUrl(LEGACY_AVATAR);
            user.setLogoUrl("1771248962905_LOGO_7.png");

            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));
            inTransaction(() -> service.removeLogo(LOGIN));

            assertThat(legacyCopy).exists();
            assertThat(user.getLogoUrl()).isNull();
            verifyNoInteractions(s3);
        }

        @Test
        void arbitraryStoredValuesAreNeitherServedNorDeleted() throws IOException {
            Path outside = Files.write(tempDir.resolve("outside.png"), PNG);
            user.setImageUrl("../outside.png");
            user.setLogoUrl("https://evil.example/logo.png");

            MeResponseDTO dto = service.getMe(LOGIN);
            assertThat(dto.getAvatarUrl()).isEmpty();
            assertThat(dto.getLogoAccessUrl()).isEmpty();

            inTransaction(() -> service.removeAvatar(LOGIN));
            inTransaction(() -> service.removeLogo(LOGIN));

            assertThat(outside).exists();
            verifyNoInteractions(s3);
        }

        @Test
        void historicalFullBucketUrlsAreReadAsTheirKeys() throws IOException {
            writeLegacy(LEGACY_AVATAR);
            user.setImageUrl(BUCKET + "/" + LEGACY_AVATAR);
            user.setLogoUrl("https://localuz-locamais.s3.amazonaws.com/1771248962905_LOGO_7.png");

            assertSignedUrl(service.getMe(LOGIN).getAvatarUrl(), LEGACY_AVATAR);
            assertThat(service.getMe(LOGIN).getLogoAccessUrl()).isEmpty();

            properties.getLegacyS3().setReadFallbackEnabled(true);
            assertThat(service.getMe(LOGIN).getLogoAccessUrl()).isEqualTo(BUCKET + "/1771248962905_LOGO_7.png");

            user.setLogoUrl("https://another-bucket.s3.amazonaws.com/1771248962905_LOGO_7.png");
            assertThat(service.getMe(LOGIN).getLogoAccessUrl()).isEmpty();
            verifyNoInteractions(s3);
        }

        @Test
        void gatewayRefusesS3DeletesInLocalMode() {
            assertThatThrownBy(() -> gateway.deleteFromLegacyS3(LEGACY_AVATAR)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(s3);
        }
    }

    @Nested
    class S3Mode {

        @BeforeEach
        void legacyMode() {
            properties.setMode(StorageProperties.StorageMode.S3);
        }

        @Test
        void uploadUsesTheLegacyServiceAndHistoricalKeyFormat() {
            when(s3.uploadFile(any(), eq("USER_7"))).thenReturn(LEGACY_AVATAR);

            MeResponseDTO result = inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));

            assertThat(user.getImageUrl()).isEqualTo(LEGACY_AVATAR);
            assertThat(result.getAvatarUrl()).isEqualTo(BUCKET + "/" + LEGACY_AVATAR);
            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void legacyValidationIsUnchangedSoHeicIsStillAccepted() {
            when(s3.uploadFile(any(), eq("LOGO_7"))).thenReturn("1771248962905_LOGO_7.heic");

            inTransaction(() -> service.uploadLogo(LOGIN, new MockMultipartFile("file", "logo.heic", "image/heic", new byte[] { 1, 2, 3 })));

            assertThat(user.getLogoUrl()).isEqualTo("1771248962905_LOGO_7.heic");
        }

        @Test
        void replacementKeepsTheLegacyBehaviorOfNotDeletingThePreviousObject() {
            user.setImageUrl("1700000000000_USER_7.png");
            when(s3.uploadFile(any(), eq("USER_7"))).thenReturn(LEGACY_AVATAR);

            inTransaction(() -> service.uploadAvatar(LOGIN, image("png")));

            verify(s3, never()).deleteFile(anyString());
        }

        @Test
        void removalDeletesFromS3ImmediatelyAndIgnoresFailuresAsBefore() {
            user.setImageUrl(LEGACY_AVATAR);
            doThrow(new RuntimeException("s3 down")).when(s3).deleteFile(LEGACY_AVATAR);

            MeResponseDTO result = inTransaction(() -> service.removeAvatar(LOGIN));

            verify(s3).deleteFile(LEGACY_AVATAR);
            assertThat(result.getImageUrl()).isNull();
            assertThat(result.getAvatarUrl()).isNull();
        }

        @Test
        void unresolvableValuesAreOmittedSoTheClientKeepsItsLegacyResolution() throws Exception {
            user.setImageUrl("https://cdn.example/avatar.png");
            user.setLogoUrl(BUCKET + "/1771248962905_LOGO_7.png");

            MeResponseDTO dto = service.getMe(LOGIN);
            String json = new ObjectMapper().writeValueAsString(dto);

            assertThat(dto.getAvatarUrl()).isNull();
            assertThat(json).doesNotContain("\"avatarUrl\"").contains("\"imageUrl\":\"https://cdn.example/avatar.png\"");
            assertThat(dto.getLogoAccessUrl()).isEqualTo(BUCKET + "/1771248962905_LOGO_7.png");
        }

        @Test
        void localStorageIsNeverUsedEvenWhenAVolumeIsAvailable() {
            when(s3.uploadFile(any(), anyString())).thenReturn("1771248962905_LOGO_7.png");

            inTransaction(() -> service.uploadLogo(LOGIN, image("png")));

            assertThat(storedFiles()).isEmpty();
        }
    }

    @Test
    void localModeSerializesNoImageAsEmptyStringSoTheClientShowsThePlaceholder() throws Exception {
        user.setImageUrl(LEGACY_AVATAR);

        String json = new ObjectMapper().writeValueAsString(service.getMe(LOGIN));

        assertThat(json).contains("\"avatarUrl\":\"\"").contains("\"logoAccessUrl\":\"\"");
    }

    @Test
    void updatePersonalIgnoresClientSuppliedImageUrl() {
        user.setImageUrl(LEGACY_AVATAR);
        for (String attempt : List.of("https://evil.example/x.png", "../../etc/passwd", "1672926360659_Car_11.png", "")) {
            UpdatePersonalDTO dto = new UpdatePersonalDTO();
            dto.setFirstName("Ana");
            dto.setImageUrl(attempt);

            MeResponseDTO result = service.updatePersonal(LOGIN, dto);

            assertThat(user.getImageUrl()).as(attempt).isEqualTo(LEGACY_AVATAR);
            assertThat(result.getImageUrl()).isEqualTo(LEGACY_AVATAR);
            assertThat(user.getFirstName()).isEqualTo("Ana");
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static MockMultipartFile image(String format) {
        switch (format) {
            case "png":
                return new MockMultipartFile("file", "image.png", "image/png", PNG);
            case "jpg":
                return new MockMultipartFile("file", "image.jpg", "image/jpeg", JPEG);
            case "webp":
                return new MockMultipartFile("file", "image.webp", "image/webp", WEBP);
            default:
                throw new IllegalArgumentException(format);
        }
    }

    private Path writeLegacy(String key) throws IOException {
        Path file = root.resolve("legacy").resolve(key);
        Files.createDirectories(file.getParent());
        return Files.write(file, PNG);
    }

    /** Keys of the files stored under avatars/ and logos/. */
    private List<String> storedFiles() {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(key -> key.startsWith("avatars/") || key.startsWith("logos/"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertSignedUrl(String url, String key) {
        assertThat(url).startsWith(FILES + "/files/");
        String[] parts = signatureParts(url);
        assertThat(signer.verify(key, parts[0], parts[1])).as("signature of " + url).isTrue();
    }

    private static String[] signatureParts(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        String exp = query.replaceAll("^exp=([0-9]+)&sig=.*$", "$1");
        String sig = query.replaceAll("^.*&sig=", "");
        return new String[] { exp, sig };
    }

    /** Transaction manager without a resource: exercises the real synchronization callbacks. */
    static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        boolean failOnCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failOnCommit) {
                throw new TransactionSystemException("simulated commit failure");
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
