package com.localuz.service.storage;

/** File types accepted by the storage, identified by their real content (magic bytes). */
public enum StoredFileType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String mimeType;
    private final String extension;

    StoredFileType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String getMimeType() { return mimeType; }
    public String getExtension() { return extension; }
    public boolean isImage() { return this != PDF; }
}
