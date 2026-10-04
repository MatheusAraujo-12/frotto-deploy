package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1);

    private final FileTypeDetector detector = new FileTypeDetector();

    @Test
    void detectsSupportedTypesByContent() {
        assertThat(detector.detect(JPEG)).contains(StoredFileType.JPEG);
        assertThat(detector.detect(PNG)).contains(StoredFileType.PNG);
        assertThat(detector.detect(WEBP)).contains(StoredFileType.WEBP);
        assertThat(detector.detect(PDF)).contains(StoredFileType.PDF);
    }

    @Test
    void doesNotRecognizeActiveOrUnsupportedContent() {
        assertThat(detector.detect(bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"))).isEmpty();
        assertThat(detector.detect(bytes("<!DOCTYPE html><html><script>alert(1)</script>"))).isEmpty();
        assertThat(detector.detect(bytes("MZ\u0090\u0000executable"))).isEmpty();
        assertThat(detector.detect(bytes("#!/bin/sh\nrm -rf /"))).isEmpty();
        assertThat(detector.detect(bytes("GIF89a......"))).isEmpty();
        assertThat(detector.detect(bytes("RIFF....WAVEfmt "))).isEmpty();
        assertThat(detector.detect(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
        assertThat(detector.detect(null)).isEmpty();
    }

    @Test
    void validateReturnsRealTypeWhenAllowedForCategory() {
        assertThat(detector.validate(StorageCategory.AVATAR, PNG)).isEqualTo(StoredFileType.PNG);
        assertThat(detector.validate(StorageCategory.LOGO, WEBP)).isEqualTo(StoredFileType.WEBP);
        assertThat(detector.validate(StorageCategory.CAR_DAMAGE, JPEG)).isEqualTo(StoredFileType.JPEG);
        assertThat(detector.validate(StorageCategory.DOCUMENT, PDF)).isEqualTo(StoredFileType.PDF);
    }

    @Test
    void pdfIsOnlyAcceptedForDocuments() {
        for (StorageCategory category : new StorageCategory[] { StorageCategory.AVATAR, StorageCategory.LOGO, StorageCategory.CAR_DAMAGE }) {
            assertThatThrownBy(() -> detector.validate(category, PDF))
                .isInstanceOf(InvalidStoredFileException.class)
                .extracting("errorKey")
                .isEqualTo("invalidtype");
        }
    }

    @Test
    void rejectsEmptyAndUnrecognizedContent() {
        assertThatThrownBy(() -> detector.validate(StorageCategory.AVATAR, new byte[0])).extracting("errorKey").isEqualTo("empty");
        assertThatThrownBy(() -> detector.validate(StorageCategory.AVATAR, null)).extracting("errorKey").isEqualTo("empty");
        assertThatThrownBy(() -> detector.validate(StorageCategory.DOCUMENT, bytes("<svg/>"))).extracting("errorKey").isEqualTo("invalidtype");
    }

    @Test
    void enforcesSizeLimitsPerCategory() {
        assertThat(detector.validate(StorageCategory.AVATAR, padded(PNG, 5 * 1024 * 1024))).isEqualTo(StoredFileType.PNG);
        assertThatThrownBy(() -> detector.validate(StorageCategory.AVATAR, padded(PNG, 5 * 1024 * 1024 + 1)))
            .extracting("errorKey")
            .isEqualTo("toolarge");
        assertThatThrownBy(() -> detector.validate(StorageCategory.LOGO, padded(PNG, 5 * 1024 * 1024 + 1)))
            .extracting("errorKey")
            .isEqualTo("toolarge");
        assertThat(detector.validate(StorageCategory.CAR_DAMAGE, padded(JPEG, 10 * 1024 * 1024))).isEqualTo(StoredFileType.JPEG);
        assertThatThrownBy(() -> detector.validate(StorageCategory.DOCUMENT, padded(PDF, 10 * 1024 * 1024 + 1)))
            .extracting("errorKey")
            .isEqualTo("toolarge");
    }

    static byte[] padded(byte[] header, int size) {
        return Arrays.copyOf(header, size);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
