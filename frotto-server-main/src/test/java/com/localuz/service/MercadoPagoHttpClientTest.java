package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({ "rawtypes", "unchecked" }) // Mockito cannot preserve HttpClient.send's generic BodyHandler type token.
class MercadoPagoHttpClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MercadoPagoHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAccessToken("secret-token"); properties.setReadTimeoutMillis(500);
        client = new MercadoPagoHttpClient(properties, mapper, http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void createsPendingPreapprovalWithExactHeadersAndPayload() throws Exception {
        when(response.statusCode()).thenReturn(201);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"pending\",\"external_reference\":\"ref-1\",\"init_point\":\"https://mp.test/checkout\"}");
        MercadoPagoPreapproval result = client.createPreapproval(request(), "idem-1");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer secret-token");
        assertThat(sent.headers().firstValue("X-Idempotency-Key")).contains("idem-1");
        String body = body(sent); JsonNode json = mapper.readTree(body);
        assertThat(json.path("reason").asText()).isEqualTo("Frotto GOLD");
        assertThat(json.path("external_reference").asText()).isEqualTo("ref-1");
        assertThat(json.path("payer_email").asText()).isEqualTo("payer@example.com");
        assertThat(json.path("back_url").asText()).isEqualTo("https://frotto.test/menu/meu-plano");
        assertThat(json.path("status").asText()).isEqualTo("pending");
        assertThat(json.path("auto_recurring").path("frequency").asInt()).isEqualTo(1);
        assertThat(json.path("auto_recurring").path("frequency_type").asText()).isEqualTo("months");
        assertThat(json.path("auto_recurring").path("transaction_amount").decimalValue()).isEqualByComparingTo("79.90");
        assertThat(json.path("auto_recurring").path("currency_id").asText()).isEqualTo("BRL");
        assertThat(body).doesNotContain("userId", "secret-token", "vehicleCount");
        assertThat(result.getId()).isEqualTo("pre-1"); assertThat(result.getInitPoint()).isEqualTo("https://mp.test/checkout");
    }

    static Stream<Integer> errorStatuses() { return Stream.of(400, 401, 403, 404, 409, 429, 500, 503); }

    @ParameterizedTest @MethodSource("errorStatuses")
    void mapsHttpErrorsWithoutLeakingProviderBody(int status) {
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn("provider-secret-body");
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1"))
            .isInstanceOf(MercadoPagoException.class).hasMessage("Mercado Pago request failed with status " + status)
            .hasMessageNotContaining("provider-secret-body");
    }

    @Test
    void rejectsMalformedAndIncompleteResponsesWithoutRetry() throws Exception {
        when(response.statusCode()).thenReturn(201); when(response.body()).thenReturn("not-json");
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1")).isInstanceOf(MercadoPagoException.class);
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void requiresIdStatusAndInitPointForCreation() {
        when(response.statusCode()).thenReturn(201); when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"pending\"}");
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1"))
            .isInstanceOf(MercadoPagoException.class).hasMessageContaining("invalid response");
    }

    private MercadoPagoPreapprovalRequest request() {
        return new MercadoPagoPreapprovalRequest("ref-1", "payer@example.com", "Frotto GOLD", new BigDecimal("79.90"), "BRL", "https://frotto.test/menu/meu-plano");
    }
    private String body(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        BodySubscriber subscriber = new BodySubscriber(); publisher.subscribe(subscriber); return subscriber.text();
    }
    private static final class BodySubscriber implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final StringBuilder value = new StringBuilder();
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
        public void onNext(java.nio.ByteBuffer b) { byte[] bytes = new byte[b.remaining()]; b.get(bytes); value.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)); }
        public void onError(Throwable t) { throw new AssertionError(t); } public void onComplete() {} String text(){return value.toString();}
    }
}
