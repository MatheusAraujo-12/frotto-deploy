package com.localuz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoHttpClient implements MercadoPagoClient {
    private static final URI PREAPPROVAL = URI.create("https://api.mercadopago.com/preapproval");
    private final MercadoPagoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

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
    @Override public MercadoPagoPreapproval cancelPreapproval(String id, String idempotencyKey) {
        return exchange("PUT", resource(id), Map.of("status", "canceled"), idempotencyKey, true);
    }

    private URI resource(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid provider subscription id");
        return URI.create(PREAPPROVAL + "/" + id);
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
                throw new MercadoPagoException("Mercado Pago request failed with status " + response.statusCode(), false);
            }
            JsonNode node = mapper.readTree(response.body());
            String id = text(node, "id"); String status = text(node, "status");
            if (id == null || status == null || (mutable && "POST".equals(method) && text(node, "init_point") == null)) {
                throw new MercadoPagoException("Mercado Pago returned an invalid response", mutable);
            }
            return new MercadoPagoPreapproval(id, status, text(node, "external_reference"), text(node, "init_point"));
        } catch (MercadoPagoException exception) { throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new MercadoPagoException("Mercado Pago request timed out", mutable, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new MercadoPagoException("Mercado Pago request interrupted", mutable, exception);
        } catch (Exception exception) {
            throw new MercadoPagoException("Mercado Pago communication failed", mutable, exception);
        }
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }
}
