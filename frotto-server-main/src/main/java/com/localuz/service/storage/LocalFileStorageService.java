package com.localuz.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link FileStorageService} backed by a persistent directory (the Coolify bind mount).
 *
 * <p>The root must be an absolute path that contains the sentinel file {@value #SENTINEL}, created by hand when
 * the volume is provisioned. Without it nothing is written: this is what prevents files from silently landing on
 * the container's ephemeral filesystem when the bind mount is missing.
 *
 * <p>Writes go to {@code .tmp/}, are fsynced and then renamed into place without replacing existing files.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    public static final String SENTINEL = ".frotto-storage";
    static final String TEMP_DIRECTORY = ".tmp";
    private static final int MAX_KEY_ATTEMPTS = 3;

    private final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final StorageProperties properties;
    private final FileTypeDetector detector;
    private final Clock clock;

    @Autowired
    public LocalFileStorageService(StorageProperties properties, FileTypeDetector detector) {
        this(properties, detector, Clock.systemUTC());
    }

    LocalFileStorageService(StorageProperties properties, FileTypeDetector detector, Clock clock) {
        this.properties = properties;
        this.detector = detector;
        this.clock = clock;
    }

    public enum Availability {
        NOT_CONFIGURED,
        ROOT_MISSING,
        SENTINEL_MISSING,
        NOT_WRITABLE,
        AVAILABLE,
    }

    public Availability checkAvailability() {
        Optional<Path> configured = configuredRoot();
        if (configured.isEmpty()) {
            return Availability.NOT_CONFIGURED;
        }
        Path root = configured.get();
        if (!Files.isDirectory(root)) {
            return Availability.ROOT_MISSING;
        }
        if (!Files.isRegularFile(root.resolve(SENTINEL))) {
            return Availability.SENTINEL_MISSING;
        }
        if (!Files.isWritable(root)) {
            return Availability.NOT_WRITABLE;
        }
        return Availability.AVAILABLE;
    }

    @Override
    public boolean isAvailable() {
        return checkAvailability() == Availability.AVAILABLE;
    }

    @Override
    public String store(StorageCategory category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidStoredFileException("empty", "Arquivo vazio");
        }
        if (file.getSize() > category.getMaxBytes()) {
            throw new InvalidStoredFileException("toolarge", "Arquivo excede o tamanho máximo permitido");
        }
        try {
            return store(category, file.getBytes());
        } catch (IOException e) {
            throw new InvalidStoredFileException("unreadable", "Não foi possível ler o arquivo enviado");
        }
    }

    @Override
    public String store(StorageCategory category, byte[] content) {
        StoredFileType type = detector.validate(category, content);
        Availability availability = checkAvailability();
        if (availability != Availability.AVAILABLE) {
            log.error("Local file storage unavailable ({}); upload refused", availability);
            throw new StorageUnavailableException("Armazenamento de arquivos indisponível");
        }
        Path root = configuredRoot().orElseThrow();
        Path temp = null;
        try {
            Path tempDirectory = root.resolve(TEMP_DIRECTORY);
            Files.createDirectories(tempDirectory);
            temp = Files.createTempFile(tempDirectory, "upload-", ".part");
            writeAndSync(temp, content);
            for (int attempt = 0; attempt < MAX_KEY_ATTEMPTS; attempt++) {
                String key = StorageKeys.newKey(category, type, clock.instant());
                Path target = resolveInside(root, StorageKeys.parse(key).orElseThrow());
                Files.createDirectories(target.getParent());
                try {
                    // No REPLACE_EXISTING: an existing file is never overwritten.
                    Files.move(temp, target);
                } catch (FileAlreadyExistsException collision) {
                    continue;
                }
                temp = null;
                restrictPermissions(target);
                return key;
            }
            throw new StorageUnavailableException("Não foi possível gerar um nome único para o arquivo");
        } catch (IOException e) {
            log.error("Local file storage write failed: {}", e.getClass().getSimpleName());
            throw new StorageUnavailableException("Falha ao gravar arquivo no armazenamento", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public boolean exists(String key) {
        return locate(key).isPresent();
    }

    @Override
    public Optional<StoredFileContent> open(String key) {
        Optional<Path> located = locate(key);
        if (located.isEmpty()) {
            return Optional.empty();
        }
        Path path = located.get();
        try {
            byte[] header = new byte[FileTypeDetector.HEADER_LENGTH];
            int read;
            try (InputStream input = Files.newInputStream(path)) {
                read = input.readNBytes(header, 0, header.length);
            }
            byte[] actualHeader = read == header.length ? header : Arrays.copyOf(header, read);
            StoredFileType type = detector.detect(actualHeader).orElse(null);
            return Optional.of(new StoredFileContent(new FileSystemResource(path), Files.size(path), type));
        } catch (IOException e) {
            log.warn("Local file storage read failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String key) {
        Optional<StorageKeys.StorageKey> parsed = StorageKeys.parse(key);
        if (parsed.isEmpty()) {
            return false;
        }
        if (parsed.get().isLegacy()) {
            // Historical objects are only removed by the migration tooling, never by application flows.
            log.debug("Legacy storage key not deleted");
            return false;
        }
        Optional<Path> located = locate(key);
        if (located.isEmpty()) {
            return false;
        }
        try {
            return Files.deleteIfExists(located.get());
        } catch (IOException e) {
            log.warn("Local file storage delete failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Existing regular file for a valid key, confined to the root (symlinks included). */
    private Optional<Path> locate(String key) {
        Optional<StorageKeys.StorageKey> parsed = StorageKeys.parse(key);
        Optional<Path> configured = configuredRoot();
        if (parsed.isEmpty() || configured.isEmpty()) {
            return Optional.empty();
        }
        Path root = configured.get();
        if (!Files.isRegularFile(root.resolve(SENTINEL))) {
            return Optional.empty();
        }
        try {
            Path path = resolveInside(root, parsed.get());
            if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) {
                return Optional.empty();
            }
            return Optional.of(path);
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> configuredRoot() {
        String configured = properties.getRoot();
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        try {
            Path root = Path.of(configured.trim());
            return root.isAbsolute() ? Optional.of(root.normalize()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static Path resolveInside(Path root, StorageKeys.StorageKey key) throws IOException {
        Path path = root.resolve(key.getRelativePath()).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IOException("Path outside storage root");
        }
        return path;
    }

    private static void writeAndSync(Path temp, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            }
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort: the directory permissions of the volume still apply.
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Leftover temp files are harmless and live outside every key namespace.
        }
    }
}
