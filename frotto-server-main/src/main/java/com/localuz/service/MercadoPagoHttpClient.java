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
        String returnedId = text(node, "id"); String status = text(node, "status"); String preapprovalId = text(node, "preapproval_id");
        if (returnedId == null || status == null || preapprovalId == null) throw new MercadoPagoException("Mercado Pago returned an invalid response", false);
        return new MercadoPagoAuthorizedPayment(returnedId, status, preapprovalId, text(node.path("payment"), "status"));
    }
    @Override public MercadoPagoPreapproval cancelPreapproval(String id, String idempotencyKey) {
        return exchange("PUT", resource(id), Map.of("status", "canceled"), idempotencyKey, true);
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
                throw providerError(response);
            }
            JsonNode node = mapper.readTree(response.body());
            String id = text(node, "id"); String status = text(node, "status");
            if (id == null || status == null || (mutable && "POST".equals(method) && text(node, "init_point") == null)) {
                throw new MercadoPagoException("Mercado Pago returned an invalid response", mutable);
            }
            return new MercadoPagoPreapproval(id, status, text(node, "external_reference"), text(node, "init_point"), instant(node, "date_created"), instant(node, "next_payment_date"), instant(node, "last_modified"));
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
        String value = text(node, field); if (value == null) return null;
        try { return java.time.OffsetDateTime.parse(value).toInstant(); } catch (java.time.format.DateTimeParseException ignored) { return null; }
    }

    private MercadoPagoException providerError(HttpResponse<String> response) {
        ProviderError details = providerError(response.body());
        String message = "Mercado Pago request failed with status " + response.statusCode();
        if (details.code != null) message += " [code=" + details.code + "]";
        if (details.message != null) message += ": " + details.message;
        return new MercadoPagoException(message, false, response.statusCode(), details.code, details.message);
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
