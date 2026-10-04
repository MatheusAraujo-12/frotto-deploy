package com.localuz.service.storage;

import java.util.EnumSet;
import java.util.Set;

/** Kinds of stored files: key prefix, maximum size and accepted types. */
public enum StorageCategory {
    AVATAR("avatars", 5L * 1024 * 1024, EnumSet.of(StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    LOGO("logos", 5L * 1024 * 1024, EnumSet.of(StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    CAR_DAMAGE("car-damages", 10L * 1024 * 1024, EnumSet.of(StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP)),
    DOCUMENT(
        "documents",
        10L * 1024 * 1024,
        EnumSet.of(StoredFileType.JPEG, StoredFileType.PNG, StoredFileType.WEBP, StoredFileType.PDF)
    );

    private final String prefix;
    private final long maxBytes;
    private final Set<StoredFileType> allowedTypes;

    StorageCategory(String prefix, long maxBytes, Set<StoredFileType> allowedTypes) {
        this.prefix = prefix;
        this.maxBytes = maxBytes;
        this.allowedTypes = allowedTypes;
    }

    public String getPrefix() { return prefix; }
    public long getMaxBytes() { return maxBytes; }
    public Set<StoredFileType> getAllowedTypes() { return allowedTypes; }
}
