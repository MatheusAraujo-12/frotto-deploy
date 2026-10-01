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
    private final String operation;
    private final String providerRequestId;
    private final String providerError;
    private final String firstCauseCode;
    private final String safeProviderMessage;
    private final boolean providerMessageWithheld;

    /**
     * Diagnostic-only fields. Each one is filtered by its own whitelist here (never trusted from the
     * caller): no '@', '=', quotes, brackets or control characters, and at most 10 digits in total,
     * so e-mails, CPF/CNPJ, card numbers, redacted tokens ("[REDACTED]") and log-line injection are
     * all rejected - a rejected value is withheld (null), never partially printed.
     */
    private static final Pattern OPERATION = Pattern.compile("[a-z_]{1,32}\\.[a-z_]{1,16}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern DIAGNOSTIC_TOKEN = Pattern.compile("[A-Za-z0-9 _.:/-]{1,64}");
    private static final Pattern DIAGNOSTIC_MESSAGE = Pattern.compile("[\\p{L}\\p{N} _.,:;()'/-]{1,160}");
    private static final int MAX_DIAGNOSTIC_DIGITS = 10;

    /** Raw provider failure context as read by MercadoPagoHttpClient; filtered by the exception before it is kept. */
    public static final class ProviderDiagnostics {
        private final String operation, requestId, error, firstCauseCode, message;
        public ProviderDiagnostics(String operation, String requestId, String error, String firstCauseCode, String message) {
            this.operation = operation; this.requestId = requestId; this.error = error; this.firstCauseCode = firstCauseCode; this.message = message;
        }
    }

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
    public MercadoPagoException(String message, boolean ambiguous, Throwable cause) {
        super(message, cause); this.ambiguous = ambiguous; this.httpStatus = null; this.providerCode = null; this.providerMessage = null;
        this.retryAfterSeconds = null; this.category = category(null, cause); this.safeProviderErrorCode = null;
        this.operation = null; this.providerRequestId = null; this.providerError = null; this.firstCauseCode = null;
        this.safeProviderMessage = null; this.providerMessageWithheld = false;
    }
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage) {
        this(message, ambiguous, httpStatus, providerCode, providerMessage, null);
    }
    /** retryAfterSeconds is only meaningful for httpStatus=429; null when the provider did not send a usable Retry-After header. */
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage, Integer retryAfterSeconds) {
        this(message, ambiguous, httpStatus, providerCode, providerMessage, retryAfterSeconds, null);
    }
    public MercadoPagoException(String message, boolean ambiguous, Integer httpStatus, String providerCode, String providerMessage,
        Integer retryAfterSeconds, ProviderDiagnostics diagnostics) {
        super(message);
        this.ambiguous = ambiguous;
        this.httpStatus = httpStatus;
        this.providerCode = providerCode;
        this.providerMessage = providerMessage;
        this.retryAfterSeconds = retryAfterSeconds;
        this.category = category(httpStatus, null);
        this.safeProviderErrorCode = safeProviderErrorCode(providerCode);
        this.operation = diagnostics == null ? null : matching(diagnostics.operation, OPERATION);
        this.providerRequestId = diagnostics == null ? null : matching(trimmed(diagnostics.requestId), REQUEST_ID);
        this.providerError = diagnostics == null ? null : diagnosticToken(diagnostics.error);
        this.firstCauseCode = diagnostics == null ? null : diagnosticToken(diagnostics.firstCauseCode);
        String rawMessage = diagnostics == null ? null : trimmed(diagnostics.message);
        this.safeProviderMessage = diagnosticMessage(rawMessage);
        this.providerMessageWithheld = rawMessage != null && safeProviderMessage == null;
    }

    /** Same failure, reclassified as ambiguous (e.g. a PUT that may have been applied) - diagnostics are preserved. */
    public MercadoPagoException asAmbiguous() {
        return new MercadoPagoException(getMessage(), true, httpStatus, providerCode, providerMessage, retryAfterSeconds,
            new ProviderDiagnostics(operation, providerRequestId, providerError, firstCauseCode, safeProviderMessage));
    }
    public boolean isAmbiguous() { return ambiguous; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getProviderCode() { return providerCode; }
    public String getProviderMessage() { return providerMessage; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public Category getCategory() { return category; }
    /** The providerCode, but only when it matches the safe enum-like shape; null otherwise. See SAFE_PROVIDER_CODE. */
    public String getSafeProviderErrorCode() { return safeProviderErrorCode; }

    /** Logical provider operation, e.g. "authorized_payments.search"; never a URL or resource id. */
    public String getOperation() { return operation; }
    /** Mercado Pago's own x-request-id response header (not Frotto's request id), when present and well-formed. */
    public String getProviderRequestId() { return providerRequestId; }
    /** The provider's "error" (or, absent that, "code") field, e.g. "Bad Request"; null when absent or unsafe. */
    public String getProviderError() { return providerError; }
    public String getFirstCauseCode() { return firstCauseCode; }
    /** The provider's top-level "message", only when it passes DIAGNOSTIC_MESSAGE; null otherwise. */
    public String getSafeProviderMessage() { return safeProviderMessage; }

    /**
     * Single-line, log-safe summary of an HTTP provider failure, built only from the whitelisted
     * fields above - never from providerCode/providerMessage/getMessage() or the response body.
     * Returns null when this failure carries no HTTP status (timeout, network, contract error).
     */
    public String diagnostics() {
        if (httpStatus == null) return null;
        StringBuilder line = new StringBuilder();
        if (operation != null) line.append("operation=").append(operation).append(' ');
        line.append("httpStatus=").append(httpStatus);
        if (providerError != null) line.append(" providerError=\"").append(providerError).append('"');
        if (firstCauseCode != null) line.append(" firstCauseCode=\"").append(firstCauseCode).append('"');
        if (providerRequestId != null) line.append(" providerRequestId=").append(providerRequestId);
        if (safeProviderMessage != null) line.append(" providerMessage=\"").append(safeProviderMessage).append('"');
        else if (providerMessageWithheld) line.append(" providerMessageUnavailable");
        return line.toString();
    }

    private static String safeProviderErrorCode(String providerCode) {
        return providerCode != null && SAFE_PROVIDER_CODE.matcher(providerCode).matches() ? providerCode : null;
    }

    private static String trimmed(String value) {
        if (value == null) return null;
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }
    private static String matching(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches() ? value : null;
    }
    private static boolean fewDigits(String value) {
        return value.chars().filter(Character::isDigit).count() <= MAX_DIAGNOSTIC_DIGITS;
    }
    private static String diagnosticToken(String raw) {
        String value = trimmed(raw);
        return value != null && DIAGNOSTIC_TOKEN.matcher(value).matches() && fewDigits(value) ? value : null;
    }
    private static String diagnosticMessage(String value) {
        if (value == null) return null;
        // Bounded length first: an oversized message is cut, never printed whole.
        String bounded = value.length() > 160 ? value.substring(0, 160).trim() : value;
        return DIAGNOSTIC_MESSAGE.matcher(bounded).matches() && fewDigits(bounded) ? bounded : null;
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
