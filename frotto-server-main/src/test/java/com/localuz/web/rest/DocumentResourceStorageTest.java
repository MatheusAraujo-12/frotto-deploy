package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.AWSS3FileService;
import com.localuz.service.UserService;
import com.localuz.service.dto.DocumentDTO;
import com.localuz.service.dto.DocumentGeneratePdfDTO;
import com.localuz.service.dto.DocumentSaveDTO;
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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document attachments (Etapa 5): local mode on a real temporary volume, s3 mode on the legacy service, with real
 * Spring transaction synchronization. attachments_json keeps its format (JSON array of keys).
 */
class DocumentResourceStorageTest {

    private static final String BUCKET = "https://localuz-locamais.s3.us-east-1.amazonaws.com";
    private static final String FILES = "https://api-staging.frotto.com.br";
    private static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final Long DOC_ID = 20L;
    private static final String HISTORICAL = "1774293055076_DOC_20.pdf";

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    private static final byte[] PDF = "%PDF-1.7\n%test\n".getBytes(StandardCharsets.ISO_8859_1);

    @RegisterExtension
    final StorageTestDirectory storageTestDirectory = new StorageTestDirectory();

    private Path tempDir;
    private Path root;
    private StorageProperties properties;
    private FileUrlSigner signer;
    private AWSS3FileService s3;
    private DriverDocumentRepository documents;
    private DocumentResource resource;
    private TestTransactionManager transactionManager;
    private DriverDocument document;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
        FileStorageGateway gateway = new FileStorageGateway(
            properties,
            local,
            new LegacyS3FileStorageService(s3),
            new FileUrlResolver(properties, local, signer),
            new StorageTransactionSupport()
        );

        Driver driver = new Driver();
        driver.setId(5L);
        driver.setName("Motorista");
        Car car = new Car();
        car.setId(11L);
        document = new DriverDocument();
        document.setId(DOC_ID);
        document.setType(DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST);
        document.setStatus(DocumentStatus.DRAFT);
        document.setDriver(driver);
        document.setCar(car);

        documents = mock(DriverDocumentRepository.class);
        when(documents.findByCurrentUserAndId(DOC_ID)).thenReturn(Optional.of(document));
        when(documents.save(any(DriverDocument.class))).thenAnswer(invocation -> {
            DriverDocument saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(DOC_ID);
            }
            return saved;
        });
        DriverRepository drivers = mock(DriverRepository.class);
        when(drivers.findByCurrentUserAndId(5L)).thenReturn(Optional.of(driver));
        CarRepository cars = mock(CarRepository.class);
        when(cars.findByCurrentUserAndId(11L)).thenReturn(Optional.of(car));
        UserService users = mock(UserService.class);
        when(users.getUserWithAuthorities()).thenReturn(Optional.of(new User()));
        resource = new DocumentResource(
            documents,
            drivers,
            cars,
            mock(DriverCarRepository.class),
            mock(PendencyRepository.class),
            gateway,
            users,
            objectMapper
        );
        ReflectionTestUtils.setField(resource, "applicationName", "localmaisApp");
        transactionManager = new TestTransactionManager();
    }

    @Nested
    class LocalMode {

        @Test
        void pdfAttachmentIsStoredLocallyAndReturnedAsSignedUrl() {
            DocumentDTO dto = upload(file("multa.pdf", "application/pdf", PDF));

            String key = dto.getAttachments().get(0);
            assertThat(key).matches("documents/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.pdf");
            assertThat(root.resolve(key)).exists();
            assertThat(document.getAttachmentsJson()).isEqualTo("[\"" + key + "\"]");
            assertSignedUrl(dto.getAttachmentUrls().get(key), key);
            verifyNoInteractions(s3);
        }

        @Test
        void multipleAttachmentsAreAppendedAfterTheHistoricalOnes() {
            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");

            DocumentDTO dto = upload(
                file("frente.png", "image/png", PNG),
                file("lado.jpg", "image/jpeg", JPEG),
                file("traseira.webp", "image/webp", WEBP),
                file("recibo.pdf", "application/pdf", PDF)
            );

            assertThat(dto.getAttachments()).hasSize(5).first().isEqualTo(HISTORICAL);
            assertThat(dto.getAttachments().subList(1, 5))
                .allSatisfy(key -> assertThat(key).startsWith("documents/"))
                .extracting(key -> key.substring(key.lastIndexOf('.') + 1))
                .containsExactly("png", "jpg", "webp", "pdf");
            assertThat(storedFiles()).hasSize(4);
            verifyNoInteractions(s3);
        }

        @Test
        void invalidContentIsRejectedWithoutSavingOrKeepingFiles() {
            for (MultipartFile invalid : List.of(
                file("x.pdf", "application/pdf", "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)),
                file("x.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)),
                file("x.exe", "application/octet-stream", "MZ executable".getBytes(StandardCharsets.UTF_8))
            )) {
                assertThatThrownBy(() -> inTransaction(() -> upload(invalid))).isInstanceOf(InvalidStoredFileException.class);
            }
            assertThat(storedFiles()).isEmpty();
            verify(documents, never()).save(any());
        }

        @Test
        void failureOnTheThirdFileRollsBackTheFirstTwo() {
            assertThatThrownBy(() ->
                    inTransaction(() ->
                        upload(file("a.png", "image/png", PNG), file("b.pdf", "application/pdf", PDF), file("c.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))
                    )
                )
                .isInstanceOf(InvalidStoredFileException.class);

            assertThat(storedFiles()).isEmpty();
            assertThat(document.getAttachmentsJson()).isNull();
        }

        @Test
        void attachmentsAboveTenMegabytesAreRejected() {
            assertThatThrownBy(() -> inTransaction(() -> upload(file("big.pdf", "application/pdf", Arrays.copyOf(PDF, 10 * 1024 * 1024 + 1)))))
                .extracting("errorKey")
                .isEqualTo("toolarge");
            assertThat(inTransaction(() -> upload(file("ok.pdf", "application/pdf", Arrays.copyOf(PDF, 10 * 1024 * 1024)))).getAttachments())
                .hasSize(1);
        }

        @Test
        void unavailableStorageAnswers503WithoutSaving() throws IOException {
            Files.delete(root.resolve(LocalFileStorageService.SENTINEL));

            assertThatThrownBy(() -> inTransaction(() -> upload(file("a.pdf", "application/pdf", PDF)))).isInstanceOf(StorageUnavailableException.class);

            verify(documents, never()).save(any());
            verifyNoInteractions(s3);
        }

        @Test
        void databaseFailureRollsBackAndRemovesTheNewFiles() {
            when(documents.save(any(DriverDocument.class))).thenThrow(new DataIntegrityViolationException("database down"));

            assertThatThrownBy(() -> inTransaction(() -> upload(file("a.png", "image/png", PNG), file("b.pdf", "application/pdf", PDF))))
                .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void unknownCommitOutcomeKeepsTheNewFiles() {
            transactionManager.failOnCommit = true;

            assertThatThrownBy(() -> inTransaction(() -> upload(file("a.png", "image/png", PNG)))).isInstanceOf(TransactionSystemException.class);

            assertThat(storedFiles()).hasSize(1);
        }

        @Test
        void deletingTheDocumentRemovesItsFilesOnlyAfterCommitAndNeverTheHistoricalOnes() throws IOException {
            Path legacyCopy = writeLegacy(HISTORICAL);
            DocumentDTO uploaded = inTransaction(() -> upload(file("a.png", "image/png", PNG), file("b.pdf", "application/pdf", PDF)));
            document.setAttachmentsJson(json(List.of(HISTORICAL, uploaded.getAttachments().get(0), uploaded.getAttachments().get(1))));

            inTransaction(() -> {
                resource.deleteDocument(DOC_ID);
                assertThat(storedFiles()).as("before commit").hasSize(2);
                return null;
            });

            verify(documents).delete(document);
            assertThat(storedFiles()).isEmpty();
            assertThat(legacyCopy).exists();
            verifyNoInteractions(s3);
        }

        @Test
        void rolledBackDeletionKeepsTheFiles() {
            DocumentDTO uploaded = inTransaction(() -> upload(file("a.png", "image/png", PNG)));
            document.setAttachmentsJson(json(uploaded.getAttachments()));

            new TransactionTemplate(transactionManager).execute(status -> {
                resource.deleteDocument(DOC_ID);
                status.setRollbackOnly();
                return null;
            });

            assertThat(storedFiles()).hasSize(1);
        }

        @Test
        void maliciousStoredReferencesAreNeitherServedNorDeleted() throws IOException {
            Path outside = Files.write(tempDir.resolve("outside.pdf"), PDF);
            document.setAttachmentsJson(json(List.of("../outside.pdf", "https://evil.example/x.pdf", "1774293055076_DOC_99.pdf")));

            DocumentDTO dto = resource.getDocumentById(DOC_ID);
            assertThat(dto.getAttachmentUrls()).containsEntry("../outside.pdf", "").containsEntry("https://evil.example/x.pdf", "");

            inTransaction(() -> resource.deleteDocument(DOC_ID));
            assertThat(outside).exists();
            verifyNoInteractions(s3);
        }

        @Test
        void attachmentsInjectedBeforeEtapa5WithOtherRecordsKeysAreNeverServed() throws IOException {
            writeLegacy("1771248962905_USER_4.png");
            writeLegacy("1774293055076_DOC_99.pdf");
            document.setAttachmentsJson(json(List.of("1771248962905_USER_4.png", "1774293055076_DOC_99.pdf", "documents/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.pdf")));

            Map<String, String> urls = resource.getDocumentById(DOC_ID).getAttachmentUrls();

            assertThat(urls)
                .containsEntry("1771248962905_USER_4.png", "")
                .containsEntry("1774293055076_DOC_99.pdf", "")
                .containsEntry("documents/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.pdf", "");
        }

        @Test
        void historicalAttachmentWithLocalCopyIsServedSigned() throws IOException {
            writeLegacy(HISTORICAL);
            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");

            assertSignedUrl(resource.getDocumentById(DOC_ID).getAttachmentUrls().get(HISTORICAL), HISTORICAL);
        }

        @Test
        void historicalAttachmentOnlyInS3UsesThePublicBucketOnlyWithFallback() {
            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");

            assertThat(resource.getDocumentById(DOC_ID).getAttachmentUrls()).containsEntry(HISTORICAL, "");

            properties.getLegacyS3().setReadFallbackEnabled(true);
            assertThat(resource.getDocumentById(DOC_ID).getAttachmentUrls()).containsEntry(HISTORICAL, BUCKET + "/" + HISTORICAL);
            assertThat(document.getAttachmentsJson()).isEqualTo("[\"" + HISTORICAL + "\"]");
            verifyNoInteractions(s3);
        }

        @Test
        void checklistPhotoReferencesInThePayloadOnlyResolveForFilesOfThisDocument() throws IOException {
            DocumentDTO uploaded = inTransaction(() -> upload(file("a.png", "image/png", PNG)));
            String own = uploaded.getAttachments().get(0);
            writeLegacy("1774293055000_DOC_20.png");
            document.setPayloadJson(
                objectMapper.writeValueAsString(
                    Map.of("checklistPhotoRefs", List.of(own, "1774293055000_DOC_20.png", "1774293055000_DOC_99.png", "../../etc/passwd"))
                )
            );

            Map<String, String> urls = resource.getDocumentById(DOC_ID).getAttachmentUrls();

            assertSignedUrl(urls.get(own), own);
            assertSignedUrl(urls.get("1774293055000_DOC_20.png"), "1774293055000_DOC_20.png");
            assertThat(urls).containsEntry("1774293055000_DOC_99.png", "").containsEntry("../../etc/passwd", "");
        }

        @Test
        void clientsCannotSetAttachmentsOrPdfUrl() throws URISyntaxException {
            DocumentSaveDTO create = new DocumentSaveDTO();
            create.setType(DocumentType.RECIBO_ALUGUEL);
            create.setDriverId(5L);
            create.setCarId(11L);
            create.setAttachments(List.of("../../etc/passwd", "1774293055076_DOC_99.pdf"));
            create.setPdfUrl("https://evil.example/x.pdf");
            DriverDocument[] created = new DriverDocument[1];
            when(documents.save(any(DriverDocument.class))).thenAnswer(invocation -> {
                DriverDocument saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(99L);
                    created[0] = saved;
                }
                return saved;
            });

            resource.createDocument(create);
            assertThat(created[0].getAttachmentsJson()).isNull();
            assertThat(created[0].getPdfUrl()).isNull();

            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");
            DocumentSaveDTO patch = new DocumentSaveDTO();
            patch.setAttachments(List.of("1771248962905_USER_4.png"));
            patch.setPdfUrl("https://evil.example/y.pdf");
            resource.updateDocumentDraft(DOC_ID, patch);
            DocumentGeneratePdfDTO generate = new DocumentGeneratePdfDTO();
            generate.setPdfUrl("https://evil.example/z.pdf");
            resource.markDocumentPdfGenerated(DOC_ID, generate);

            assertThat(document.getAttachmentsJson()).isEqualTo("[\"" + HISTORICAL + "\"]");
            assertThat(document.getPdfUrl()).isNull();
        }

        @Test
        void listResponsesDoNotCarryContentOrUrls() {
            when(documents.findByCurrentUserWithFilters(any(), any(), any(), any(), any())).thenReturn(List.of(document));
            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");

            DocumentDTO listed = resource.getDocuments(null, null, null, null, null, null).get(0);

            assertThat(listed.getAttachments()).isNull();
            assertThat(listed.getAttachmentUrls()).isNull();
        }
    }

    @Nested
    class S3Mode {

        @BeforeEach
        void legacyMode() {
            properties.setMode(StorageProperties.StorageMode.S3);
        }

        @Test
        void uploadUsesTheLegacyServiceWithTheDocumentIdentifier() {
            when(s3.uploadFile(any(), eq("DOC_20"))).thenReturn("1775000000000_DOC_20.pdf", "1775000000001_DOC_20.png");

            DocumentDTO dto = inTransaction(() -> upload(file("a.pdf", "application/pdf", PDF), file("b.png", "image/png", PNG)));

            verify(s3, times(2)).uploadFile(any(), eq("DOC_20"));
            assertThat(dto.getAttachments()).containsExactly("1775000000000_DOC_20.pdf", "1775000000001_DOC_20.png");
            assertThat(dto.getAttachmentUrls()).containsEntry("1775000000000_DOC_20.pdf", BUCKET + "/1775000000000_DOC_20.pdf");
            assertThat(storedFiles()).isEmpty();
        }

        @Test
        void deletionOnlyDeletesThisDocumentsOwnObjectsAndNeverBlankKeysOrPdfUrl() {
            document.setAttachmentsJson(
                json(List.of(HISTORICAL, BUCKET + "/1774293055000_DOC_20.png", "1771248962905_USER_4.png", "", "1774293055076_DOC_200.pdf"))
            );
            document.setPdfUrl("1774293055076_DOC_20.pdf");
            doThrow(new RuntimeException("AccessDenied")).when(s3).deleteFile(HISTORICAL);

            inTransaction(() -> resource.deleteDocument(DOC_ID));

            verify(s3).deleteFile(HISTORICAL);
            verify(s3).deleteFile("1774293055000_DOC_20.png");
            verify(s3, never()).deleteFile("1771248962905_USER_4.png");
            verify(s3, never()).deleteFile("1774293055076_DOC_200.pdf");
            verify(s3, never()).deleteFile("");
            verify(s3, times(2)).deleteFile(anyString());
            verify(documents).delete(document);
        }

        @Test
        void unresolvableReferencesAreOmittedSoTheClientKeepsItsLegacyResolution() throws Exception {
            document.setAttachmentsJson("[\"" + HISTORICAL + "\"]");
            document.setPayloadJson(objectMapper.writeValueAsString(Map.of("fotos", List.of("1774293055000_DOC_99.png"))));

            Map<String, String> urls = resource.getDocumentById(DOC_ID).getAttachmentUrls();

            assertThat(urls).containsEntry(HISTORICAL, BUCKET + "/" + HISTORICAL).doesNotContainKey("1774293055000_DOC_99.png");
        }

        @Test
        void injectedKeysOfOtherRecordsGetNoBucketUrl() {
            document.setAttachmentsJson(json(List.of(HISTORICAL, "1771248962905_USER_4.png")));

            Map<String, String> urls = resource.getDocumentById(DOC_ID).getAttachmentUrls();

            assertThat(urls).containsEntry(HISTORICAL, BUCKET + "/" + HISTORICAL).doesNotContainKey("1771248962905_USER_4.png");
        }
    }

    private DocumentDTO upload(MultipartFile... files) {
        return resource.uploadDocumentAttachments(DOC_ID, files).getBody();
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path writeLegacy(String key) throws IOException {
        Path file = root.resolve("legacy").resolve(key);
        Files.createDirectories(file.getParent());
        return Files.write(file, key.endsWith(".pdf") ? PDF : PNG);
    }

    private List<String> storedFiles() {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> root.relativize(path).toString().replace('\\', '/'))
                .filter(key -> key.startsWith("documents/"))
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
