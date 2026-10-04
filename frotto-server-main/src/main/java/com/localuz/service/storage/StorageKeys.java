package com.localuz.service.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Generation and validation of storage keys. The key is what the database stores; it is never an absolute path.
 *
 * <ul>
 *   <li>New keys: {@code {prefix}/{yyyy}/{MM}/{uuid}.{ext}}, generated only by the server.</li>
 *   <li>Legacy keys: historical S3 object names, kept unchanged ({@code {13-digit millis}_...}, e.g.
 *       {@code 1672926360659_Car_11.png}, {@code 1771248962905_USER_4.png}). They have no directory part and live
 *       under {@code legacy/} locally once copied.</li>
 * </ul>
 *
 * Anything else (absolute URLs, {@code ../}, backslashes, control characters, hidden names) is rejected.
 */
public final class StorageKeys {

    public static final String LEGACY_DIRECTORY = "legacy";

    private static final Pattern NEW_KEY = Pattern.compile(
        "^(avatars|logos|car-damages|documents)/[0-9]{4}/(0[1-9]|1[0-2])/" +
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)$"
    );
    // Historical formats start with System.currentTimeMillis(); the rest may hold an original client filename
    // (spaces, accents...). No separators, no ':' and no control characters, so it can never leave legacy/.
    private static final Pattern LEGACY_KEY = Pattern.compile("^[0-9]{13}_[^/\\\\:\\x00-\\x1F\\x7F]{1,240}$");

    private StorageKeys() {}

    public static String newKey(StorageCategory category, StoredFileType type, Instant now) {
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        return String.format(
            "%s/%04d/%02d/%s.%s",
            category.getPrefix(),
            utc.getYear(),
            utc.getMonthValue(),
            UUID.randomUUID(),
            type.getExtension()
        );
    }

    public static Optional<StorageKey> parse(String key) {
        if (key == null || key.isEmpty() || key.length() > 255) {
            return Optional.empty();
        }
        if (NEW_KEY.matcher(key).matches()) {
            return Optional.of(new StorageKey(key, false, key));
        }
        if (LEGACY_KEY.matcher(key).matches()) {
            return Optional.of(new StorageKey(key, true, LEGACY_DIRECTORY + "/" + key));
        }
        return Optional.empty();
    }

    /** A validated key and its path relative to the storage root. */
    public static final class StorageKey {

        private final String key;
        private final boolean legacy;
        private final String relativePath;

        private StorageKey(String key, boolean legacy, String relativePath) {
            this.key = key;
            this.legacy = legacy;
            this.relativePath = relativePath;
        }

        public String getKey() { return key; }
        public boolean isLegacy() { return legacy; }
        public String getRelativePath() { return relativePath; }
    }
}
