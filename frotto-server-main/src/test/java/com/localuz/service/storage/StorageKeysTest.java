package com.localuz.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageKeysTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void newKeysAreServerGeneratedPrefixedAndParseable() {
        for (StorageCategory category : StorageCategory.values()) {
            String key = StorageKeys.newKey(category, StoredFileType.PNG, NOW);
            assertThat(key).matches(category.getPrefix() + "/2026/10/[0-9a-f-]{36}\\.png");
            StorageKeys.StorageKey parsed = StorageKeys.parse(key).orElseThrow();
            assertThat(parsed.isLegacy()).isFalse();
            assertThat(parsed.getRelativePath()).isEqualTo(key);
        }
    }

    @Test
    void newKeysUseUtcMonthAndAreUnique() {
        Instant lastSecondOfYearUtc = Instant.parse("2026-12-31T23:59:59Z");
        String first = StorageKeys.newKey(StorageCategory.DOCUMENT, StoredFileType.PDF, lastSecondOfYearUtc);
        String second = StorageKeys.newKey(StorageCategory.DOCUMENT, StoredFileType.PDF, lastSecondOfYearUtc);
        assertThat(first).startsWith("documents/2026/12/").endsWith(".pdf");
        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "1672926360659_Car_11.png",
            "1771248962905_USER_4.png",
            "1774293055076_DOC_20.pdf",
            "1694012345678_01.jpg",
            "1694012345678_LOGO_7.webp",
            "1765000000000_5_Foto da batida çãé (1).jpeg",
            "1765000000000_noextension",
        }
    )
    void historicalS3KeysArePreservedUnderLegacy(String key) {
        StorageKeys.StorageKey parsed = StorageKeys.parse(key).orElseThrow();
        assertThat(parsed.isLegacy()).isTrue();
        assertThat(parsed.getKey()).isEqualTo(key);
        assertThat(parsed.getRelativePath()).isEqualTo("legacy/" + key);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "",
            " ",
            "..",
            ".",
            "../etc/passwd",
            "/etc/passwd",
            "C:\\Windows\\win.ini",
            "1672926360659_../../etc/passwd",
            "1672926360659_..\\..\\x.png",
            "1672926360659_a/b.png",
            "1672926360659_C:x.png",
            "1672926360659_a\u0000.png",
            "1672926360659_a\n.png",
            "167292636065_short.png",
            "legacy/1672926360659_Car_11.png",
            ".frotto-storage",
            ".tmp/upload-1.part",
            "https://localuz-locamais.s3.us-east-1.amazonaws.com/1672926360659_Car_11.png",
            "avatars/2026/10/../../../etc/passwd",
            "avatars/2026/13/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png",
            "avatars/2026/10/3F2A8C1E-0B6D-4C5E-9A1F-2B3C4D5E6F70.png",
            "avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.svg",
            "avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png.exe",
            "others/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png",
            "/avatars/2026/10/3f2a8c1e-0b6d-4c5e-9a1f-2b3c4d5e6f70.png",
        }
    )
    void rejectsAnythingThatIsNotAKnownKeyFormat(String key) {
        assertThat(StorageKeys.parse(key)).isEmpty();
    }

    @Test
    void rejectsNullAndOverlongKeys() {
        assertThat(StorageKeys.parse(null)).isEmpty();
        assertThat(StorageKeys.parse("1672926360659_" + "a".repeat(242))).isEmpty();
    }
}
