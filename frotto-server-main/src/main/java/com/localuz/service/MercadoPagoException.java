package com.localuz.service;

public class MercadoPagoException extends RuntimeException {
    private final boolean ambiguous;
    public MercadoPagoException(String message, boolean ambiguous) { super(message); this.ambiguous = ambiguous; }
    public MercadoPagoException(String message, boolean ambiguous, Throwable cause) { super(message, cause); this.ambiguous = ambiguous; }
    public boolean isAmbiguous() { return ambiguous; }
}
