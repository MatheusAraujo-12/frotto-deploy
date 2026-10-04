package com.localuz.service.storage;

import java.util.Optional;
import org.springframework.core.io.Resource;

/** Content returned by {@link FileStorageService#open(String)}. */
public class StoredFileContent {

    private final Resource resource;
    private final long size;
    private final StoredFileType type;

    public StoredFileContent(Resource resource, long size, StoredFileType type) {
        this.resource = resource;
        this.size = size;
        this.type = type;
    }

    public Resource getResource() { return resource; }
    public long getSize() { return size; }

    /** Type detected from the stored bytes; empty for unrecognized (e.g. legacy HEIC) content. */
    public Optional<StoredFileType> getType() { return Optional.ofNullable(type); }
}
