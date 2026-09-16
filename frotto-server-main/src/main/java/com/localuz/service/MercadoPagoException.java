package com.localuz.service;

public class MercadoPagoException extends RuntimeException {
    private final boolean ambiguous;
    private final Integer httpStatus;
    private final String providerCode;
    private final String providerMessage;
    private final Integer retryAfterSeconds;
    public MercadoPagoException(String message, boolean ambiguous) { this(message, ambiguous, null, null, null); }
    public MercadoPagoException(String message, boolean ambiguous, Throwable cause) { super(message, cause); this.ambiguous = ambiguous; this.httpStatus = null; this.providerCode = null; this.providerMessage = null; this.retryAfterSeconds = null; }
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage) {
        this(message, ambiguous, httpStatus, providerCode, providerMessage, null);
    }
    /** retryAfterSeconds is only meaningful for httpStatus=429; null when the provider did not send a usable Retry-After header. */
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage, Integer retryAfterSeconds) {
        super(message);
        this.ambiguous = ambiguous;
        this.httpStatus = httpStatus;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public boolean isAmbiguous() { return ambiguous; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}
