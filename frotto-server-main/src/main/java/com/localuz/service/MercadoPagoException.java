package com.localuz.service;

public class MercadoPagoException extends RuntimeException {
    private final boolean ambiguous;
    private final Integer httpStatus;
    private final String providerCode;
    private final String providerMessage;
    public MercadoPagoException(String message, boolean ambiguous) { this(message, ambiguous, null, null, null); }
    public MercadoPagoException(String message, boolean ambiguous, Throwable cause) { super(message, cause); this.ambiguous = ambiguous; this.httpStatus = null; this.providerCode = null; this.providerMessage = null; }
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage) {
        super(message);
        this.ambiguous = ambiguous;
        this.httpStatus = httpStatus;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
    }
    public boolean isAmbiguous() { return ambiguous; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
}
