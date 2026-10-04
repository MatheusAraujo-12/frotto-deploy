package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.localuz.domain.Car;
import com.localuz.domain.CarBodyDamage;
import com.localuz.repository.CarBodyDamageRepository;
import com.localuz.repository.CarRepository;
import com.localuz.service.AWSS3FileService;
import com.localuz.service.dto.BodyDamageDTO;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
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
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Damage photos (Etapa 4): local mode on a real temporary volume, s3 mode on the legacy service, with real Spring
 * transaction synchronization (commit, rollback, unknown commit outcome).
 */
class CarBodyDamageResourceStorageTest {

    private static final String BUCKET = "https://localuz-locamais.s3.us-east-1.amazonaws.com";
    private static final String FILES = "https://api-staging.frotto.com.br";
    private static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final String LEGACY = "1672926360659_Car_11.png";
    private static final Long CAR_ID = 11L;
    private static final Long DAMAGE_ID = 31L;

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1);

    @RegisterExtension
    final StorageTestDirectory storageTestDirectory = new StorageTestDirectory();

    Path tempDir;

    private Path root;
    private StorageProperties properties;
    private FileUrlSigner signer;
    private FileStorageGateway gateway;
    private AWSS3FileService s3;
    private CarBodyDamageRepository damages;
    private CarBodyDamageResource resource;
    private TestTransactionManager transactionManager;
    private CarBodyDamage stored;

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
        signer = new FileUrlSigner(properties, Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));
        gateway =
            new FileStorageGateway(
                properties,
                local,
                new LegacyS3FileStorageService(s3),
                new FileUrlResolver(properties, local, signer),
                new StorageTransactionSupport()
            );

        Car car = new Car();
        car.setId(CAR_ID);
        CarRepository cars = mock(CarRepository.class);
        when(cars.findByCurrentUserAndId(CAR_ID)).thenReturn(Optional.of(car));
        damages = mock(CarBodyDamageRepository.class);
        when(damages.save(any(CarBodyDamage.class))).thenAnswer(invocation -> {
            CarBodyDamage damage = invocation.getArgument(0);
            if (damage.getId() == null) {
                damage.setId(DAMAGE_ID);
            }
            return damage;
        });
        stored = new CarBodyDamage();
        stored.setId(DAMAGE_ID);
        stored.setCar(car);
        stored.setPart("Porta");
        when(damages.findByCurrentUserAndCarBdId(DAMAGE_ID)).thenReturn(Optional.of(stored));
        resource = new CarBodyDamageResource(damages, cars, gateway);
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
        transactionManager = new TestTransactionManager();
    }

    @Nested
    class LocalMode {

        @Test
        void bothPhotoPositionsAreStoredLocallyAndReturnedAsSignedUrls() {
            BodyDamageDTO dto = newDamage();
            dto.setFile(multipart("frente.png", "image/png", PNG));
            dto.setFile2Base64("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG));

            CarBodyDamage created = inTransaction(() -> create(dto));

            assertThat(created.getImagePath()).matches("car-damages/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.png");
            assertThat(created.getImagePath2()).matches("car-damages/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.jpg");
            assertThat(root.resolve(created.getImagePath())).exists();
            assertThat(root.resolve(created.getImagePath2())).exists();
            assertSignedUrl(created.getImageUrl(), created.getImagePath());
            assertSignedUrl(created.getImageUrl2(), created.getImagePath2());
            verifyNoInteractions(s3);
        }

        @ParameterizedTest
        @ValueSource(strings = { "png", "jpg", "webp" })
        void acceptsTheAllowedImageFormatsByContent(String format) {
            BodyDamageDTO dto = newDamage();
            dto.setFile(image(format));

            CarBodyDamage created = inTransaction(() -> create(dto));

            assertThat(created.getImagePath()).endsWith("." + format);
            assertThat(created.getImagePath2()).isEmpty();
            assertThat(created.getImageUrl2()).isEmpty();
        }

        @Test
        void invalidContentIsRejectedWithoutSavingOrKeepingFiles() {
            for (BodyDamageDTO dto : List.of(
                damageWithFile(multipart("foto.png", "image/png", PDF)),
                damageWithFile(multipart("foto.png", "image/png", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))),
                damageWithBase64("data:image/png;base64," + Base64.getEncoder().encodeToString("MZ executable".getBytes(StandardCharsets.UTF_8))),
                damageWithBase64("isto-nao-e-base64!!")
            )) {
                assertThatThrownBy(() -> inTransaction(() -> create(dto)))
                    .isInstanceOf(InvalidStoredFileException.class);
            }
            assertThat(storedFiles()).isEmpty();
            verify(damages, never()).save(any());
            verifyNoInteractions(s3);
        }

        @Test
        void photosAboveTenMegabytesAreRejected() {
            BodyDamageDTO multipartTooLarge = damageWithFile(multipart("big.png", "image/png", Arrays.copyOf(PNG, 10 * 1024 * 1024 + 1)));
            BodyDamageDTO base64TooLarge = damageWithBase64(Base64.getEncoder().encodeToString(Arrays.copyOf(PNG, 10 * 1024 * 1024 + 3)));

            assertThatThrownBy(() -> inTransaction(() -> create(multipartTooLarge)))
                .extracting("errorKey")
                .isEqualTo("toolarge");
            assertThatThrownBy(() -> inTransaction(() -> create(base64TooLarge)))
                .extracting("errorKey")
                .isEqualTo("toolarge");

            BodyDamageDTO exactly10Mb = damageWithFile(multipart("ok.png", "image/png", Arrays.copyOf(PNG, 10 * 1024 * 1024)));
            assertThat(inTransaction(() -> create(exactly10Mb)).getImagePath()).endsWith(".png");
        }

        @Test
        void unavailableStorageAnswers503WithoutSaving() throws IOException {
            Files.delete(root.resolve(LocalFileStorageService.SENTINEL));

            assertThatThrownBy(() -> inTransaction(() -> create(damageWithFile(image("png")))))
                .isInstanceOf(StorageUnavailableException.class);

            verify(damages, never()).save(any());
            verifyNoInteractions(s3);
        }

        @Test
        void damageWithoutPhotosKeepsTheEmptyPathsAndNoUrls() {
            CarBodyDamage created = inTransaction(() -> create(newDamage()));

            assertThat(created.getImagePath()).isEmpty();
            assertThat(created.getImagePath2()).isEmpty();
            assertThat(created.getImageUrl()).isEmpty();
            assertThat(created.getImageUrl2()).isEmpty();
        }

        @Test
        void databaseFailureRollsBackAndRemovesBothNewFiles() {
            when(damages.save(any(CarBodyDamage.class))).thenThrow(new DataIntegrityViolationException("database down"));
            BodyDamageDTO dto = damageWithFile(image("png"));
            dto.setFile2(image("webp"));

            assertThatThrownBy(() -> inTransaction(() -> create(dto)))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void invalidSecondPhotoRollsBackTheFirstOne() {
            BodyDamageDTO dto = damageWithFile(image("png"));
            dto.setFile2(multipart("x.png", "image/png", PDF));

            assertThatThrownBy(() -> inTransaction(() -> create(dto)))
                .isInstanceOf(InvalidStoredFileException.class);

            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void replacementKeepsThePreviousFileUntilCommitAndLeavesTheOtherPositionAlone() {
            CarBodyDamage created = createWithTwoPhotos();
            String first = created.getImagePath();
            String previousSecond = created.getImagePath2();

            CarBodyDamage updated = inTransaction(() -> {
                CarBodyDamage response = resource.partialUpdateCarBodyDamage(DAMAGE_ID, patchWithFile2(image("jpg"))).getBody();
                assertThat(root.resolve(previousSecond)).as("previous before commit").exists();
                return response;
            });

            assertThat(updated.getImagePath()).isEqualTo(first);
            assertThat(updated.getImagePath2()).endsWith(".jpg").isNotEqualTo(previousSecond);
            assertThat(root.resolve(previousSecond)).doesNotExist();
            assertThat(storedFiles()).containsExactlyInAnyOrder(first, updated.getImagePath2());
            assertSignedUrl(updated.getImageUrl2(), updated.getImagePath2());
            verifyNoInteractions(s3);
        }

        @Test
        void rolledBackReplacementRemovesOnlyTheNewFile() {
            CarBodyDamage created = createWithTwoPhotos();
            List<String> before = storedFiles();
            when(damages.save(any(CarBodyDamage.class))).thenThrow(new DataIntegrityViolationException("database down"));

            assertThatThrownBy(() -> inTransaction(() -> resource.partialUpdateCarBodyDamage(DAMAGE_ID, patchWithFile(image("webp")))))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedFiles()).containsExactlyInAnyOrderElementsOf(before);
            assertThat(root.resolve(created.getImagePath())).exists();
        }

        @Test
        void unknownCommitOutcomeDeletesNothing() {
            createWithTwoPhotos();
            transactionManager.failOnCommit = true;

            assertThatThrownBy(() -> inTransaction(() -> resource.partialUpdateCarBodyDamage(DAMAGE_ID, patchWithFile(image("jpg")))))
                .isInstanceOf(TransactionSystemException.class);

            assertThat(storedFiles()).hasSize(3);
        }

        @Test
        void deletingTheDamageRemovesItsFilesOnlyAfterCommit() {
            CarBodyDamage created = createWithTwoPhotos();

            inTransaction(() -> {
                resource.deleteCarBodyDamage(DAMAGE_ID);
                assertThat(root.resolve(created.getImagePath())).as("before commit").exists();
                return null;
            });

            verify(damages).deleteById(DAMAGE_ID);
            assertThat(storedFiles()).isEmpty();
            verifyNoInteractions(s3);
        }

        @Test
        void rolledBackDeletionKeepsTheFiles() {
            createWithTwoPhotos();

            new TransactionTemplate(transactionManager).execute(status -> {
                resource.deleteCarBodyDamage(DAMAGE_ID);
                status.setRollbackOnly();
                return null;
            });

            assertThat(storedFiles()).hasSize(2);
        }

        @Test
        void damageWithoutPhotosCanBeDeleted() {
            stored.setImagePath("");
            stored.setImagePath2(null);

            inTransaction(() -> resource.deleteCarBodyDamage(DAMAGE_ID));

            verify(damages).deleteById(DAMAGE_ID);
            verifyNoInteractions(s3);
        }

        @Test
        void historicalKeyWithLocalCopyIsServedSigned() throws IOException {
            writeLegacy(LEGACY);
            stored.setImagePath(LEGACY);

            assertSignedUrl(resource.getCarBodyDamageById(DAMAGE_ID).getImageUrl(), LEGACY);
        }

        @Test
        void historicalKeyOnlyInS3UsesThePublicBucketOnlyWithFallback() {
            stored.setImagePath(LEGACY);
            stored.setImagePath2("1694012345678_02.jpg");

            CarBodyDamage withoutFallback = resource.getCarBodyDamageById(DAMAGE_ID);
            assertThat(withoutFallback.getImageUrl()).isEmpty();
            assertThat(withoutFallback.getImageUrl2()).isEmpty();
            assertThat(withoutFallback.getImagePath()).isEqualTo(LEGACY);

            properties.getLegacyS3().setReadFallbackEnabled(true);
            CarBodyDamage withFallback = resource.getCarBodyDamageById(DAMAGE_ID);
            assertThat(withFallback.getImageUrl()).isEqualTo(BUCKET + "/" + LEGACY);
            assertThat(withFallback.getImageUrl2()).isEqualTo(BUCKET + "/1694012345678_02.jpg");
            verifyNoInteractions(s3);
        }

        @Test
        void listsCarryResolvedUrls() throws IOException {
            writeLegacy(LEGACY);
            stored.setImagePath(LEGACY);
            when(damages.findByCurrentUserAndCarIdByDate(CAR_ID)).thenReturn(List.of(stored));
            when(damages.findActiveByCurrentUserAndCarIdByDate(CAR_ID)).thenReturn(List.of(stored));

            assertSignedUrl(resource.getCarBodyDamagesByCar(CAR_ID).get(0).getImageUrl(), LEGACY);
            assertSignedUrl(resource.getActiveCarBodyDamagesByCar(CAR_ID).get(0).getImageUrl(), LEGACY);
        }

        @Test
        void replacingOrDeletingHistoricalPhotosNeverTouchesS3OrTheLegacyCopy() throws IOException {
            Path legacyCopy = writeLegacy(LEGACY);
            stored.setImagePath(LEGACY);
            stored.setImagePath2("1694012345678_02.jpg");

            inTransaction(() -> resource.partialUpdateCarBodyDamage(DAMAGE_ID, patchWithFile(image("png"))));
            inTransaction(() -> resource.deleteCarBodyDamage(DAMAGE_ID));

            assertThat(legacyCopy).exists();
            verifyNoInteractions(s3);
        }

        @Test
        void maliciousValuesNeverBecomePathsOrUrls() throws IOException {
            Path outside = Files.write(tempDir.resolve("outside.png"), PNG);
            stored.setImagePath("../outside.png");
            stored.setImagePath2("https://evil.example/x.png");

            CarBodyDamage read = resource.getCarBodyDamageById(DAMAGE_ID);
            assertThat(read.getImageUrl()).isEmpty();
            assertThat(read.getImageUrl2()).isEmpty();
            inTransaction(() -> resource.deleteCarBodyDamage(DAMAGE_ID));
            assertThat(outside).exists();

            BodyDamageDTO dto = damageWithFile(multipart("../../../outside.png", "image/png", PNG));
            dto.setImagePath("../../etc/passwd");
            CarBodyDamage created = inTransaction(() -> create(dto));
            assertThat(created.getImagePath()).startsWith("car-damages/").doesNotContain("..");
            verifyNoInteractions(s3);
        }

        @Test
        void resolvedUrlsAreReadOnlyInJson() throws Exception {
            ObjectMapper mapper = apiObjectMapper();
            CarBodyDamage parsed = mapper.readValue(
                "{\"id\":1,\"imagePath\":\"" + LEGACY + "\",\"imageUrl\":\"https://evil.example/x.png\",\"imageUrl2\":\"x\"}",
                CarBodyDamage.class
            );

            assertThat(parsed.getImageUrl()).isNull();
            assertThat(parsed.getImageUrl2()).isNull();
        }

        @Test
        void gatewayRefusesS3DeletesInLocalMode() {
            assertThatThrownBy(() -> gateway.deleteFromLegacyS3(LEGACY)).isInstanceOf(IllegalStateException.class);
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
        void createUsesTheLegacyServiceWithHistoricalIdentifiers() {
            when(s3.uploadFile(any(), eq("01"))).thenReturn("1771000000000_01.png");
            when(s3.uploadBase64(anyString(), eq("02"))).thenReturn("1771000000000_02.jpg");
            BodyDamageDTO dto = damageWithFile(image("png"));
            dto.setFile2Base64("data:image/jpeg;base64,AAAA");

            CarBodyDamage created = inTransaction(() -> create(dto));

            assertThat(created.getImagePath()).isEqualTo("1771000000000_01.png");
            assertThat(created.getImagePath2()).isEqualTo("1771000000000_02.jpg");
            assertThat(created.getImageUrl()).isEqualTo(BUCKET + "/1771000000000_01.png");
            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void replacementKeepsTheLegacyOrderDeleteThenUpload() {
            stored.setImagePath("1700000000000_01.png");
            when(s3.uploadFile(any(), eq("01"))).thenReturn("1771000000000_01.png");

            inTransaction(() -> resource.partialUpdateCarBodyDamage(DAMAGE_ID, patchWithFile(image("png"))));

            InOrder order = inOrder(s3);
            order.verify(s3).deleteFile("1700000000000_01.png");
            order.verify(s3).uploadFile(any(), eq("01"));
            assertThat(stored.getImagePath()).isEqualTo("1771000000000_01.png");
        }

        @Test
        void deletionKeepsTheLegacyImmediateS3Deletes() {
            stored.setImagePath("1700000000000_01.png");
            stored.setImagePath2("");

            inTransaction(() -> resource.deleteCarBodyDamage(DAMAGE_ID));

            verify(s3).deleteFile("1700000000000_01.png");
            // A photo-less position never reaches S3 (an empty key would be a DELETE on the bucket root).
            verify(s3, never()).deleteFile("");
        }

        @Test
        void legacyDeleteFailureStillFailsTheRequestAsBefore() {
            stored.setImagePath("1700000000000_01.png");
            doThrow(new RuntimeException("AccessDenied")).when(s3).deleteFile("1700000000000_01.png");

            assertThatThrownBy(() -> inTransaction(() -> resource.deleteCarBodyDamage(DAMAGE_ID))).hasMessage("AccessDenied");
        }

        @Test
        void unresolvableValuesAreOmittedFromJsonSoTheClientKeepsItsLegacyResolution() throws Exception {
            stored.setImagePath("https://cdn.example/x.png");
            stored.setImagePath2(BUCKET + "/" + LEGACY);

            CarBodyDamage read = resource.getCarBodyDamageById(DAMAGE_ID);
            String json = apiObjectMapper().writeValueAsString(read);

            assertThat(read.getImageUrl()).isNull();
            assertThat(json).doesNotContain("\"imageUrl\":").contains("\"imageUrl2\":\"" + BUCKET + "/" + LEGACY + "\"");
        }

        @Test
        void localStorageIsNeverUsed() {
            when(s3.uploadFile(any(), anyString())).thenReturn("1771000000000_01.png");

            inTransaction(() -> create(damageWithFile(image("png"))));

            assertThat(storedFiles()).isEmpty();
        }
    }

    private CarBodyDamage createWithTwoPhotos() {
        BodyDamageDTO dto = damageWithFile(image("png"));
        dto.setFile2(image("webp"));
        CarBodyDamage created = inTransaction(() -> create(dto));
        stored.setImagePath(created.getImagePath());
        stored.setImagePath2(created.getImagePath2());
        return created;
    }

    /** Same modules as JacksonConfiguration (the Hibernate5Module drops JPA @Transient properties). */
    private static ObjectMapper apiObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new Hibernate5Module());
    }

    private CarBodyDamage create(BodyDamageDTO dto) {
        try {
            return resource.createCarBodyDamageByCar(CAR_ID, dto).getBody();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static BodyDamageDTO newDamage() {
        BodyDamageDTO dto = new BodyDamageDTO();
        dto.setPart("Porta");
        dto.setCost(BigDecimal.TEN);
        return dto;
    }

    private static BodyDamageDTO damageWithFile(MockMultipartFile file) {
        BodyDamageDTO dto = newDamage();
        dto.setFile(file);
        return dto;
    }

    private static BodyDamageDTO damageWithBase64(String base64) {
        BodyDamageDTO dto = newDamage();
        dto.setFileBase64(base64);
        return dto;
    }

    private static BodyDamageDTO patchWithFile(MockMultipartFile file) {
        BodyDamageDTO dto = new BodyDamageDTO();
        dto.setId(DAMAGE_ID);
        dto.setFile(file);
        return dto;
    }

    private static BodyDamageDTO patchWithFile2(MockMultipartFile file) {
        BodyDamageDTO dto = new BodyDamageDTO();
        dto.setId(DAMAGE_ID);
        dto.setFile2(file);
        return dto;
    }

    private static MockMultipartFile multipart(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static MockMultipartFile image(String format) {
        switch (format) {
            case "png":
                return multipart("foto.png", "image/png", PNG);
            case "jpg":
                return multipart("foto.jpg", "image/jpeg", JPEG);
            case "webp":
                return multipart("foto.webp", "image/webp", WEBP);
            default:
                throw new IllegalArgumentException(format);
        }
    }

    private Path writeLegacy(String key) throws IOException {
        Path file = root.resolve("legacy").resolve(key);
        Files.createDirectories(file.getParent());
        return Files.write(file, PNG);
    }

    private List<String> storedFiles() {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(key -> key.startsWith("car-damages/"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertSignedUrl(String url, String key) {
        assertThat(url).startsWith(FILES + "/files/");
        String query = url.substring(url.indexOf('?') + 1);
        String exp = query.replaceAll("^exp=([0-9]+)&sig=.*$", "$1");
        String sig = query.replaceAll("^.*&sig=", "");
        assertThat(signer.verify(key, exp, sig)).as("signature of " + key).isTrue();
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
