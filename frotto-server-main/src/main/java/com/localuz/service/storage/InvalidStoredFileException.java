package com.localuz.service.storage;

/** The uploaded content was rejected (empty, too large or type not allowed). Mapped to HTTP 400. */
public class InvalidStoredFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorKey;

    public InvalidStoredFileException(String errorKey, String message) {
        super(message);
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
