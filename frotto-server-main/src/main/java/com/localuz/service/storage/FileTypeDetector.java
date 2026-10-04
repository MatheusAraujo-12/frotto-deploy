package com.localuz.service.storage;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Identifies a file by its content (magic bytes) only. The client's Content-Type and filename are never trusted:
 * an executable renamed to .png, an SVG or an HTML page is simply not recognized.
 */
@Component
public class FileTypeDetector {

    /** Number of leading bytes needed to recognize every supported type. */
    public static final int HEADER_LENGTH = 12;

    public Optional<StoredFileType> detect(byte[] header) {
        if (header == null || header.length < 3) {
            return Optional.empty();
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(StoredFileType.JPEG);
        }
        if (startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(StoredFileType.PNG);
        }
        if (header.length >= 12 && startsWith(header, 'R', 'I', 'F', 'F') && matchesAt(header, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(StoredFileType.WEBP);
        }
        if (startsWith(header, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(StoredFileType.PDF);
        }
        return Optional.empty();
    }

    /**
     * Validates content against the category rules and returns its real type.
     *
     * @throws InvalidStoredFileException when empty, too large, unrecognized or not allowed for the category.
     */
    public StoredFileType validate(StorageCategory category, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidStoredFileException("empty", "Arquivo vazio");
        }
        if (content.length > category.getMaxBytes()) {
            throw new InvalidStoredFileException("toolarge", "Arquivo excede o tamanho máximo permitido");
        }
        StoredFileType type = detect(content).orElseThrow(() -> new InvalidStoredFileException("invalidtype", "Tipo de arquivo não permitido"));
        if (!category.getAllowedTypes().contains(type)) {
            throw new InvalidStoredFileException("invalidtype", "Tipo de arquivo não permitido");
        }
        return type;
    }

    private static boolean startsWith(byte[] data, int... expected) {
        return matchesAt(data, 0, expected);
    }

    private static boolean matchesAt(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
