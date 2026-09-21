package com.localuz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoHttpClient implements MercadoPagoClient {
    private static final URI PREAPPROVAL = URI.create("https://api.mercadopago.com/preapproval");
    private static final URI AUTHORIZED_PAYMENTS = URI.create("https://api.mercadopago.com/authorized_payments");
    private static final URI PAYMENTS = URI.create("https://api.mercadopago.com/v1/payments");
    private final MercadoPagoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,;]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    @Autowired
    public MercadoPagoHttpClient(MercadoPagoProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build());
    }

    MercadoPagoHttpClient(MercadoPagoProperties properties, ObjectMapper mapper, HttpClient client) {
        this.properties = properties; this.mapper = mapper; this.client = client;
    }

    @Override
    public MercadoPagoPreapproval createPreapproval(MercadoPagoPreapprovalRequest request, String idempotencyKey) {
        Map<String, Object> recurring = new HashMap<>();
        recurring.put("frequency", 1); recurring.put("frequency_type", "months");
        recurring.put("transaction_amount", request.getTransactionAmount()); recurring.put("currency_id", request.getCurrencyId());
        Map<String, Object> body = new HashMap<>();
        body.put("reason", request.getReason()); body.put("external_reference", request.getExternalReference());
        body.put("payer_email", request.getPayerEmail()); body.put("back_url", request.getBackUrl());
        body.put("auto_recurring", recurring); body.put("status", "pending");
        return exchange("POST", PREAPPROVAL, body, idempotencyKey, true);
    }

    @Override public MercadoPagoPreapproval getPreapproval(String id) {
        return exchange("GET", resource(id), null, null, false);
    }
    @Override public MercadoPagoAuthorizedPayment getAuthorizedPayment(String id) {
        JsonNode node = getJson(resource(AUTHORIZED_PAYMENTS, id));
        MercadoPagoAuthorizedPayment result = authorizedPayment(node);
        if (!id.equals(result.getId())) throw invalidFinancialResponse();
        return result;
    }

    @Override public com.localuz.service.dto.MercadoPagoPayment getPayment(String id) {
        JsonNode node = getJson(resource(PAYMENTS, id));
        if (!id.equals(text(node, "id")) || text(node, "status") == null) throw invalidFinancialResponse();
        if (node.hasNonNull("transaction_amount_refunded") && decimal(node, "transaction_amount_refunded") == null) {
            throw invalidFinancialResponse();
        }
        return new com.localuz.service.dto.MercadoPagoPayment(id, text(node, "status"), safeCode(node, "status_detail"),
            decimal(node, "transaction_amount"), text(node, "currency_id"), instant(node, "date_created"),
            instant(node, "date_approved"), instant(node, "date_last_updated"), text(node, "external_reference"),
            decimal(node, "transaction_amount_refunded"));
    }

    @Override public java.util.Optional<MercadoPagoAuthorizedPayment> findAuthorizedPaymentByPaymentId(String id) {
        resource(PAYMENTS, id); // Validate before constructing the query; never interpolate an arbitrary URL.
        JsonNode node = getJson(URI.create(AUTHORIZED_PAYMENTS + "/search?payment_id=" + id + "&limit=2&offset=0"));
        JsonNode results = node.path("results");
        if (!results.isArray() || !node.path("paging").path("total").canConvertToInt()) throw invalidFinancialResponse();
        int total = node.path("paging").path("total").asInt();
        if (total == 0 && results.isEmpty()) return java.util.Optional.empty();
        if (total != 1 || results.size() != 1) throw invalidFinancialResponse();
        MercadoPagoAuthorizedPayment match = authorizedPayment(results.get(0));
        if (!id.equals(match.getPaymentId())) throw invalidFinancialResponse();
        // Confirm the search result with the authoritative individual resource.
        MercadoPagoAuthorizedPayment confirmed = getAuthorizedPayment(match.getId());
        if (!id.equals(confirmed.getPaymentId()) || !match.getPreapprovalId().equals(confirmed.getPreapprovalId())) {
            throw invalidFinancialResponse();
        }
        return java.util.Optional.of(confirmed);
    }

    @Override public com.localuz.service.dto.MercadoPagoAuthorizedPaymentPage searchAuthorizedPayments(String preapprovalId, int offset, int limit) {
        resource(PREAPPROVAL, preapprovalId);
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid discovery bounds");
        JsonNode node = getJson(URI.create(AUTHORIZED_PAYMENTS + "/search?preapproval_id=" + preapprovalId
            + "&offset=" + offset + "&limit=" + limit));
        JsonNode paging = node.path("paging"), results = node.path("results");
        for (String field : List.of("offset", "limit", "total")) {
            if (!paging.path(field).isIntegralNumber() || !paging.path(field).canConvertToInt()) throw invalidFinancialResponse();
        }
        int returnedOffset = paging.path("offset").asInt(), returnedLimit = paging.path("limit").asInt(), total = paging.path("total").asInt();
        if (returnedOffset != offset || returnedLimit < 1 || returnedLimit > limit || total < 0 || !results.isArray()
            || results.size() != Math.min(returnedLimit, Math.max(0, (long) total - offset))) throw invalidFinancialResponse();
        List<String> ids = new ArrayList<>();
        for (JsonNode result : results) {
            String id = text(result, "id");
            if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw invalidFinancialResponse();
            ids.add(id);
        }
        return new com.localuz.service.dto.MercadoPagoAuthorizedPaymentPage(returnedOffset, returnedLimit, total, ids);
    }

    private MercadoPagoAuthorizedPayment authorizedPayment(JsonNode node) {
        String id = text(node, "id"), status = text(node, "status"), preapprovalId = text(node, "preapproval_id");
        if (id == null || status == null || preapprovalId == null) throw invalidFinancialResponse();
        resource(AUTHORIZED_PAYMENTS, id);
        resource(PREAPPROVAL, preapprovalId);
        return new MercadoPagoAuthorizedPayment(id, status, preapprovalId, text(node.path("payment"), "status"),
            text(node.path("payment"), "id"), decimal(node, "transaction_amount"), text(node, "currency_id"),
            instant(node, "date_created"), instant(node, "last_modified"), instant(node, "debit_date"), text(node, "external_reference"), offsetDateTime(node, "debit_date"));
    }

    private java.math.BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) return null;
        try { return new java.math.BigDecimal(value); }
        catch (NumberFormatException invalid) { throw invalidFinancialResponse(); }
    }

    private String safeCode(JsonNode node, String field) {
        String value = text(node, field);
        return value != null && value.matches("[A-Za-z0-9_-]{1,128}") ? value : null;
    }

    private MercadoPagoException invalidFinancialResponse() {
        return new MercadoPagoException("Mercado Pago returned an invalid financial response", false);
    }
    /**
     * Mercado Pago's documented preapproval-cancellation value is "canceled", but the sandbox has
     * been observed rejecting it with a definite HTTP 400 while accepting the legacy "cancelled"
     * spelling instead (confirmed empirically in staging). This compatibility fallback triggers
     * ONLY on that exact, unambiguous HTTP 400 - never on 401/403/404/409/429, a timeout, or a 5xx,
     * where whether the provider already processed the original request is unknown and guessing
     * would risk a false confirmation or a false rollback (see SubscriptionCancellationService,
     * which already handles those other cases via its own ambiguous/ resolveAfterUnconfirmedResponse
     * path).
     *
     * On a 400, an authoritative GET decides everything - never assumed:
     * - already canceled/cancelled: treat as confirmed, no second PUT.
     * - still authorized/paused: exactly one more PUT using "cancelled", then a fresh confirming
     *   GET; success only if that GET itself reports canceled/cancelled.
     * - anything else (including the second PUT itself failing, or the final GET still not
     *   showing a cancelled state): the original 400 is preserved and thrown, so the caller's
     *   existing "definite rejection -> rollback" handling is unchanged.
     * At most two PUTs are ever sent. The second PUT uses its own idempotency key - reusing the
     * first key with a different body would let Mercado Pago legitimately replay the first (failed)
     * response instead of processing the retry.
     */
    @Override public MercadoPagoPreapproval cancelPreapproval(String id, String idempotencyKey) {
        try {
            return exchange("PUT", resource(id), Map.of("status", "canceled"), idempotencyKey, true);
        } catch (MercadoPagoException officialRejected) {
            if (!Integer.valueOf(400).equals(officialRejected.getHttpStatus())) {
                throw officialRejected;
            }
            MercadoPagoPreapproval confirmed = getPreapproval(id);
            if (SubscriptionCancellationSteps.isTerminalCancelled(confirmed.getStatus())) {
                return confirmed;
            }
            String normalizedStatus = confirmed.getStatus() == null ? "" : confirmed.getStatus().toLowerCase(java.util.Locale.ROOT);
            if (!"authorized".equals(normalizedStatus) && !"paused".equals(normalizedStatus)) {
                throw officialRejected;
            }
            exchange("PUT", resource(id), Map.of("status", "cancelled"), idempotencyKey + "-legacy", true);
            MercadoPagoPreapproval reconfirmed = getPreapproval(id);
            if (SubscriptionCancellationSteps.isTerminalCancelled(reconfirmed.getStatus())) {
                return reconfirmed;
            }
            throw officialRejected;
        }
    }

    private URI resource(String id) {
        return resource(PREAPPROVAL, id);
    }
    private URI resource(URI base, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid provider resource id");
        return URI.create(base + "/" + id);
    }

    private MercadoPagoPreapproval exchange(String method, URI uri, Object body, String key, boolean mutable) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getAccessToken())
                .header("Content-Type", "application/json");
            if (key != null) builder.header("X-Idempotency-Key", key);
            String json = body == null ? "" : mapper.writeValueAsString(body);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                MercadoPagoException failure = providerError(response);
                // A failed cancellation PUT may have been applied before a timeout/5xx response.
                // Keep the existing classification for creation and read-only requests.
                if ("PUT".equals(method) && (response.statusCode() >= 500 || response.statusCode() == 408)) {
                    throw new MercadoPagoException(failure.getMessage(), true, failure.getHttpStatus(), failure.getProviderCode(), failure.getProviderMessage());
                }
                throw failure;
            }
            JsonNode node = mapper.readTree(response.body());
            String id = text(node, "id"); String status = text(node, "status");
            if (id == null || status == null || (mutable && "POST".equals(method) && text(node, "init_point") == null)) {
                throw new MercadoPagoException("Mercado Pago returned an invalid response", mutable);
            }
            return new MercadoPagoPreapproval(id, status, text(node, "external_reference"), text(node, "init_point"), instant(node, "date_created"), instant(node, "next_payment_date"), instant(node, "last_modified"),
                frequency(node.path("auto_recurring")), text(node.path("auto_recurring"), "frequency_type"));
        } catch (MercadoPagoException exception) { throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new MercadoPagoException("Mercado Pago request timed out", mutable, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new MercadoPagoException("Mercado Pago request interrupted", mutable, exception);
        } catch (Exception exception) {
            throw new MercadoPagoException("Mercado Pago communication failed", mutable, exception);
        }
    }
    private JsonNode getJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getAccessToken()).header("Content-Type", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerError(response);
            return mapper.readTree(response.body());
        } catch (MercadoPagoException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new MercadoPagoException("Mercado Pago request interrupted", false, exception);
        } catch (Exception exception) { throw new MercadoPagoException("Mercado Pago communication failed", false, exception); }
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }
    private java.time.Instant instant(JsonNode node, String field) {
        java.time.OffsetDateTime value = offsetDateTime(node, field);
        return value == null ? null : value.toInstant();
    }
    private java.time.OffsetDateTime offsetDateTime(JsonNode node, String field) {
        String value = text(node, field); if (value == null) return null;
        try { return java.time.OffsetDateTime.parse(value); } catch (java.time.format.DateTimeParseException ignored) { return null; }
    }
    private Integer frequency(JsonNode recurring) {
        JsonNode value = recurring.path("frequency");
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0 ? value.intValue() : null;
    }

    private MercadoPagoException providerError(HttpResponse<String> response) {
        ProviderError details = providerError(response.body());
        String message = "Mercado Pago request failed with status " + response.statusCode();
        if (details.code != null) message += " [code=" + details.code + "]";
        if (details.message != null) message += ": " + details.message;
        Integer retryAfterSeconds = response.statusCode() == 429 ? retryAfterSeconds(response) : null;
        return new MercadoPagoException(message, false, response.statusCode(), details.code, details.message, retryAfterSeconds);
    }

    /** Seconds-only per current policy; an HTTP-date Retry-After value is intentionally not parsed. */
    private Integer retryAfterSeconds(HttpResponse<String> response) {
        try {
            java.net.http.HttpHeaders headers = response.headers();
            if (headers == null) return null;
            return headers.firstValue("Retry-After")
                .map(String::trim)
                .filter(value -> value.matches("[0-9]{1,9}"))
                .map(Long::parseLong)
                .filter(value -> value > 0)
                .map(Long::intValue)
                .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Only known Mercado Pago error fields are retained; arbitrary response JSON is never logged. */
    private ProviderError providerError(String body) {
        if (body == null || body.isBlank()) return new ProviderError(null, null);
        try {
            JsonNode root = mapper.readTree(body);
            List<String> codes = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            add(codes, text(root, "error")); add(codes, text(root, "code"));
            add(messages, text(root, "message"));
            JsonNode causes = root.path("cause");
            if (causes.isArray()) for (JsonNode cause : causes) {
                add(codes, text(cause, "code")); add(messages, text(cause, "description"));
            }
            return new ProviderError(sanitize(String.join(", ", codes)), sanitize(String.join("; ", messages)));
        } catch (Exception ignored) {
            return new ProviderError(null, null);
        }
    }

    private void add(List<String> values, String value) { if (value != null && !value.isBlank()) values.add(value); }
    private String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        sanitized = JWT.matcher(sanitized).replaceAll("[REDACTED]");
        String token = properties.getAccessToken();
        if (token != null && !token.isBlank()) sanitized = sanitized.replace(token, "[REDACTED]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
    private static final class ProviderError {
        private final String code; private final String message;
        private ProviderError(String code, String message) { this.code = code; this.message = message; }
    }
}
