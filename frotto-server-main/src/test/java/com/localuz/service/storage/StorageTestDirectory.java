package com.localuz.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Per-test temporary directory for storage tests ({@code @RegisterExtension}), replacing {@code @TempDir}.
 *
 * <p>On Windows, an antivirus scanning freshly written files can keep them in "delete pending" state for a moment, and
 * {@code @TempDir} then fails the test during cleanup although every assertion passed. Cleanup here retries briefly and
 * never fails the test.
 */
public final class StorageTestDirectory implements BeforeEachCallback, AfterEachCallback {

    private static final int CLEANUP_ATTEMPTS = 20;
    private static final long CLEANUP_PAUSE_MILLIS = 100;

    private Path path;

    public Path path() {
        if (path == null) {
            throw new IllegalStateException("Directory is only available while a test runs");
        }
        return path;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        path = Files.createTempDirectory("frotto-storage-test-");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Path directory = path;
        path = null;
        for (int attempt = 0; attempt < CLEANUP_ATTEMPTS && directory != null && Files.exists(directory); attempt++) {
            deleteRecursively(directory);
            if (Files.exists(directory)) {
                pause();
            }
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(StorageTestDirectory::deleteQuietly);
        } catch (IOException e) {
            // retried by the caller
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // retried by the caller
        }
    }

    private static void pause() {
        try {
            Thread.sleep(CLEANUP_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
