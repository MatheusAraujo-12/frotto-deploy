package com.localuz.service;

import java.util.regex.Pattern;

public class MercadoPagoException extends RuntimeException {
    /**
     * Mercado Pago error/cause codes are short enum-like tokens (e.g. "bad_request", "PA400"), never
     * free text - unlike providerMessage, which echoes provider-authored descriptions and must never be
     * logged. A providerCode is only safe to log when it matches this shape; anything else (unexpected
     * punctuation, embedded emails/ids, oversized strings) is treated as unsafe and withheld.
     */
    private static final Pattern SAFE_PROVIDER_CODE = Pattern.compile("[A-Za-z0-9_-]{1,40}(?:, [A-Za-z0-9_-]{1,40}){0,4}");

    private final boolean ambiguous;
    private final Integer httpStatus;
    private final String providerCode;
    private final String providerMessage;
    private final Integer retryAfterSeconds;
    private final Category category;
    private final String safeProviderErrorCode;

    /**
     * Safe-to-log failure bucket, derived only from the HTTP status (never the response body) and
     * the cause's Java type - never from exception messages, which may still echo provider text.
     * Lets callers distinguish "the provider rejected/rate-limited us" from "we never got a usable
     * response" (timeout/network/interrupted) from "we got a 2xx we could not trust" (parsing the
     * body failed, or it failed our own schema/business validation) without risking a secret leak.
     */
    public enum Category { HTTP_400, HTTP_401, HTTP_403, HTTP_404, HTTP_429, HTTP_CLIENT_ERROR, HTTP_5XX,
        TIMEOUT, NETWORK_ERROR, PARSING_ERROR, INTERRUPTED, CONTRACT_ERROR }

    public MercadoPagoException(String message, boolean ambiguous) { this(message, ambiguous, null, null, null); }
    public MercadoPagoException(String message, boolean ambiguous, Throwable cause) { super(message, cause); this.ambiguous = ambiguous; this.httpStatus = null; this.providerCode = null; this.providerMessage = null; this.retryAfterSeconds = null; this.category = category(null, cause); this.safeProviderErrorCode = null; }
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
        this.category = category(httpStatus, null);
        this.safeProviderErrorCode = safeProviderErrorCode(providerCode);
    }
    public boolean isAmbiguous() { return ambiguous; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public Category getCategory() { return category; }
    /** The providerCode, but only when it matches the safe enum-like shape; null otherwise. See SAFE_PROVIDER_CODE. */
    public String getSafeProviderErrorCode() { return safeProviderErrorCode; }

    private static String safeProviderErrorCode(String providerCode) {
        return providerCode != null && SAFE_PROVIDER_CODE.matcher(providerCode).matches() ? providerCode : null;
    }

    private static Category category(Integer httpStatus, Throwable cause) {
        if (httpStatus != null) {
            switch (httpStatus) {
                case 400: return Category.HTTP_400;
                case 401: return Category.HTTP_401;
                case 403: return Category.HTTP_403;
                case 404: return Category.HTTP_404;
                case 429: return Category.HTTP_429;
                default: return httpStatus >= 500 ? Category.HTTP_5XX : Category.HTTP_CLIENT_ERROR;
            }
        }
        if (cause instanceof java.net.http.HttpTimeoutException) return Category.TIMEOUT;
        if (cause instanceof InterruptedException) return Category.INTERRUPTED;
        if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) return Category.PARSING_ERROR;
        if (cause instanceof java.io.IOException) return Category.NETWORK_ERROR;
        if (cause != null) return Category.NETWORK_ERROR;
        return Category.CONTRACT_ERROR;
    }
}
