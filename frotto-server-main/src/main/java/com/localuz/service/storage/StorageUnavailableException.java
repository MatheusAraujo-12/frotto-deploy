package com.localuz.service.storage;

/** The persistent storage is not mounted, not initialized (no sentinel) or not writable. Mapped to HTTP 503. */
public class StorageUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
