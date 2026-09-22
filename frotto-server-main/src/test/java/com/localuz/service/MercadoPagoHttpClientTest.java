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
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked") // Mockito cannot preserve HttpClient.send's generic BodyHandler type token.
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
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/json");
        String body = body(sent); JsonNode json = mapper.readTree(body);
        assertThat(json.path("reason").asText()).isEqualTo("Frotto GOLD");
        assertThat(json.path("external_reference").asText()).isEqualTo("ref-1");
        assertThat(json.path("payer_email").asText()).isEqualTo("payer@example.com");
        assertThat(json.path("back_url").asText()).isEqualTo("https://frotto.test/menu/meu-plano");
        assertThat(json.path("status").asText()).isEqualTo("pending");
        assertThat(json.path("auto_recurring").path("frequency").asInt()).isEqualTo(1);
        assertThat(json.path("auto_recurring").path("frequency_type").asText()).isEqualTo("months");
        assertThat(json.path("auto_recurring").path("transaction_amount").decimalValue()).isEqualByComparingTo("79.90");
        // The configured ObjectMapper emits a JSON number and normalizes the scale (79.90 -> 79.9).
        assertThat(json.path("auto_recurring").path("transaction_amount").toString()).isEqualTo("79.9");
        assertThat(json.path("auto_recurring").path("currency_id").asText()).isEqualTo("BRL");
        assertThat(body).doesNotContain("userId", "secret-token", "vehicleCount");
        assertThat(result.getId()).isEqualTo("pre-1"); assertThat(result.getInitPoint()).isEqualTo("https://mp.test/checkout");
    }

    static Stream<Integer> errorStatuses() { return Stream.of(400, 401, 403, 404, 409, 429, 500, 503); }

    @ParameterizedTest @MethodSource("errorStatuses")
    void mapsHttpErrorsWithoutLeakingUnstructuredProviderBody(int status) {
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn("provider-secret-body");
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1"))
            .isInstanceOf(MercadoPagoException.class).hasMessage("Mercado Pago request failed with status " + status)
            .hasMessageNotContaining("provider-secret-body");
    }

    @Test
    void preservesStructuredProviderErrorAndSanitizesSecrets() {
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"message\":\"payer invalid; Authorization: Bearer secret-token; eyJabc.def.ghi\",\"error\":\"bad_request\",\"cause\":[{\"code\":\"PA400\",\"description\":\"invalid payer\"}],\"access_token\":\"must-not-be-retained\"}");
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, exception -> {
                assertThat(exception.getHttpStatus()).isEqualTo(400);
                assertThat(exception.getProviderCode()).isEqualTo("bad_request, PA400");
                assertThat(exception.getProviderMessage()).contains("payer invalid", "invalid payer", "[REDACTED]")
                    .doesNotContain("secret-token", "eyJabc.def.ghi", "must-not-be-retained");
                assertThat(exception.getMessage()).doesNotContain("secret-token", "eyJabc.def.ghi", "must-not-be-retained");
            });
    }

    @Test
    void timeoutRemainsAmbiguousForMutableRequest() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timed out"));
        assertThatThrownBy(() -> client.createPreapproval(request(), "idem-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, exception -> {
                assertThat(exception.isAmbiguous()).isTrue();
                assertThat(exception.getMessage()).isEqualTo("Mercado Pago request timed out");
            });
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

    @Test
    void getsAuthorizedPaymentFromOfficialResource() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pay-1\",\"status\":\"processed\",\"preapproval_id\":\"pre-1\",\"payment\":{\"status\":\"approved\"}}");
        MercadoPagoAuthorizedPayment payment = client.getAuthorizedPayment("pay-1");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.mercadopago.com/authorized_payments/pay-1");
        assertThat(payment.getPreapprovalId()).isEqualTo("pre-1");
        assertThat(payment.getStatus()).isEqualTo("processed");
        assertThat(payment.getPaymentStatus()).isEqualTo("approved");
    }


    /**
     * 400 is deliberately excluded from this set: it now triggers the "canceled"/"cancelled"
     * compatibility fallback (see the dedicated tests below) instead of throwing immediately from
     * a single PUT - every OTHER status here must still throw right away, with exactly one PUT and
     * the unchanged official payload, never touching the fallback path.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"401,false", "403,false", "404,false", "408,true", "409,false", "500,true", "502,true", "503,true"})
    void cancellationClassifiesHttpFailuresWithoutChangingOfficialPayload(int status, boolean ambiguous) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        assertThatThrownBy(() -> client.cancelPreapproval("pre-1", "cancel-pre-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, error -> {
                assertThat(error.getHttpStatus()).isEqualTo(status);
                assertThat(error.isAmbiguous()).isEqualTo(ambiguous);
            });
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().method()).isEqualTo("PUT");
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval/pre-1");
        assertThat(body(captor.getValue())).isEqualTo("{\"status\":\"canceled\"}");
        assertThat(captor.getValue().headers().firstValue("X-Idempotency-Key")).contains("cancel-pre-1");
    }

    // --- "canceled"/"cancelled" HTTP 400 compatibility fallback -------------------------------

    @Test
    void officialCanceledValueAcceptedDirectlyNeverTriggersTheFallback() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"canceled\"}");

        MercadoPagoPreapproval result = client.cancelPreapproval("pre-1", "cancel-pre-1");

        assertThat(result.getStatus()).isEqualTo("canceled");
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().method()).isEqualTo("PUT");
        assertThat(body(captor.getValue())).isEqualTo("{\"status\":\"canceled\"}");
    }

    @Test
    void officialValueRejectedWith400ButConfirmingGetAlreadyShowsCancelledSkipsSecondPut() throws Exception {
        HttpResponse<String> putResponse = mock(HttpResponse.class);
        when(putResponse.statusCode()).thenReturn(400);
        when(putResponse.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        HttpResponse<String> getResponse = mock(HttpResponse.class);
        when(getResponse.statusCode()).thenReturn(200);
        when(getResponse.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"cancelled\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(putResponse, getResponse);

        MercadoPagoPreapproval result = client.cancelPreapproval("pre-1", "cancel-pre-1");

        assertThat(result.getStatus()).isEqualTo("cancelled");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        java.util.List<HttpRequest> sent = captor.getAllValues();
        assertThat(sent.get(0).method()).isEqualTo("PUT");
        assertThat(body(sent.get(0))).isEqualTo("{\"status\":\"canceled\"}");
        assertThat(sent.get(1).method()).isEqualTo("GET");
        assertThat(sent.get(1).uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval/pre-1");
        assertThat(sent.stream().filter(request -> "PUT".equals(request.method()))).hasSize(1);
    }

    @Test
    void officialValueRejectedWith400ThenLegacySpellingConfirmsCancellation() throws Exception {
        HttpResponse<String> firstPut = mock(HttpResponse.class);
        when(firstPut.statusCode()).thenReturn(400);
        when(firstPut.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        HttpResponse<String> firstGet = mock(HttpResponse.class);
        when(firstGet.statusCode()).thenReturn(200);
        when(firstGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\"}");
        HttpResponse<String> secondPut = mock(HttpResponse.class);
        when(secondPut.statusCode()).thenReturn(200);
        when(secondPut.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"cancelled\"}");
        HttpResponse<String> secondGet = mock(HttpResponse.class);
        when(secondGet.statusCode()).thenReturn(200);
        when(secondGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"cancelled\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(firstPut, firstGet, secondPut, secondGet);

        MercadoPagoPreapproval result = client.cancelPreapproval("pre-1", "cancel-pre-1");

        assertThat(result.getStatus()).isEqualTo("cancelled");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        java.util.List<HttpRequest> sent = captor.getAllValues();
        java.util.List<HttpRequest> puts = sent.stream().filter(request -> "PUT".equals(request.method())).toList();
        // At most two PUTs, ever: the official value, then the legacy spelling.
        assertThat(puts).hasSize(2);
        assertThat(body(puts.get(0))).isEqualTo("{\"status\":\"canceled\"}");
        assertThat(body(puts.get(1))).isEqualTo("{\"status\":\"cancelled\"}");
        // A different body must not reuse the first PUT's idempotency key (Mercado Pago could
        // legitimately replay the first, failed response instead of processing the retry).
        assertThat(puts.get(0).headers().firstValue("X-Idempotency-Key")).contains("cancel-pre-1");
        assertThat(puts.get(1).headers().firstValue("X-Idempotency-Key")).isNotEqualTo(puts.get(0).headers().firstValue("X-Idempotency-Key"));
        java.util.List<HttpRequest> gets = sent.stream().filter(request -> "GET".equals(request.method())).toList();
        assertThat(gets).hasSize(2);
    }

    @Test
    void secondPutAlsoFailingKeepsTheOriginalErrorWithoutAThirdAttempt() throws Exception {
        HttpResponse<String> firstPut = mock(HttpResponse.class);
        when(firstPut.statusCode()).thenReturn(400);
        when(firstPut.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        HttpResponse<String> firstGet = mock(HttpResponse.class);
        when(firstGet.statusCode()).thenReturn(200);
        when(firstGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"paused\"}");
        HttpResponse<String> secondPut = mock(HttpResponse.class);
        when(secondPut.statusCode()).thenReturn(409);
        when(secondPut.body()).thenReturn("{\"message\":\"conflict\",\"status\":409}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(firstPut, firstGet, secondPut);

        assertThatThrownBy(() -> client.cancelPreapproval("pre-1", "cancel-pre-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, error -> {
                assertThat(error.getHttpStatus()).isEqualTo(409);
                assertThat(error.isAmbiguous()).isFalse();
            });
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        java.util.List<HttpRequest> puts = captor.getAllValues().stream().filter(request -> "PUT".equals(request.method())).toList();
        assertThat(puts).hasSize(2); // no third attempt after the second PUT also fails
    }

    @Test
    void confirmingGetStillNotCancelledAfterTheLegacyRetryPreservesTheOriginal400() throws Exception {
        HttpResponse<String> firstPut = mock(HttpResponse.class);
        when(firstPut.statusCode()).thenReturn(400);
        when(firstPut.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        HttpResponse<String> firstGet = mock(HttpResponse.class);
        when(firstGet.statusCode()).thenReturn(200);
        when(firstGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\"}");
        HttpResponse<String> secondPut = mock(HttpResponse.class);
        when(secondPut.statusCode()).thenReturn(200);
        when(secondPut.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\"}");
        HttpResponse<String> secondGet = mock(HttpResponse.class);
        when(secondGet.statusCode()).thenReturn(200);
        when(secondGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(firstPut, firstGet, secondPut, secondGet);

        assertThatThrownBy(() -> client.cancelPreapproval("pre-1", "cancel-pre-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, error -> {
                assertThat(error.getHttpStatus()).isEqualTo(400);
                assertThat(error.isAmbiguous()).isFalse();
            });
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        java.util.List<HttpRequest> puts = captor.getAllValues().stream().filter(request -> "PUT".equals(request.method())).toList();
        assertThat(puts).hasSize(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pending", "rejected"})
    void confirmingGetShowingNeitherCancelledNorRetryableStatusSkipsTheSecondPut(String status) throws Exception {
        HttpResponse<String> firstPut = mock(HttpResponse.class);
        when(firstPut.statusCode()).thenReturn(400);
        when(firstPut.body()).thenReturn("{\"message\":\"Invalid preapproval status param: canceled\",\"status\":400}");
        HttpResponse<String> firstGet = mock(HttpResponse.class);
        when(firstGet.statusCode()).thenReturn(200);
        when(firstGet.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"" + status + "\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(firstPut, firstGet);

        assertThatThrownBy(() -> client.cancelPreapproval("pre-1", "cancel-pre-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, error -> assertThat(error.getHttpStatus()).isEqualTo(400));
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getAllValues().stream().filter(request -> "PUT".equals(request.method()))).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void cancellationTimeoutAndConnectionFailureRemainAmbiguous(boolean timeout) throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(timeout ? new HttpTimeoutException("timeout") : new java.io.IOException("connection failed"));
        assertThatThrownBy(() -> client.cancelPreapproval("pre-1", "cancel-pre-1"))
            .isInstanceOfSatisfying(MercadoPagoException.class, error -> assertThat(error.isAmbiguous()).isTrue());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"canceled", "cancelled"})
    void cancellationSendsCanceledAndRecognizesBothTerminalResponses(String status) throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"" + status + "\"}");

        MercadoPagoPreapproval result = client.cancelPreapproval("pre-1", "cancel-pre-1");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo("PUT");
        assertThat(sent.uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval/pre-1");
        assertThat(body(sent)).isEqualTo("{\"status\":\"canceled\"}").doesNotContain("\"cancelled\"");
        assertThat(sent.headers().firstValue("Authorization")).contains("Bearer secret-token");
        assertThat(sent.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(sent.headers().firstValue("X-Idempotency-Key")).contains("cancel-pre-1");
        assertThat(sent.headers().firstValue("X-scope")).isEmpty();
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(SubscriptionCancellationSteps.isTerminalCancelled(result.getStatus())).isTrue();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"canceled,true", "cancelled,true", "authorized,false", "paused,false"})
    void getPreapprovalPreservesStatusAndRecognizesCancellation(String status, boolean cancelled) throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"" + status + "\"}");

        MercadoPagoPreapproval result = client.getPreapproval("pre-1");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().method()).isEqualTo("GET");
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval/pre-1");
        assertThat(result.getStatus()).isEqualTo(status);
        assertThat(SubscriptionCancellationSteps.isTerminalCancelled(result.getStatus())).isEqualTo(cancelled);
    }

    // --- 5G.12: PUT /preapproval/{id} auto_recurring.transaction_amount/currency_id -------------

    @Test
    void updatePreapprovalAmountSendsOnlyAutoRecurringAmountAndCurrency() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\",\"auto_recurring\":{\"transaction_amount\":44.9,\"currency_id\":\"BRL\"}}");

        MercadoPagoPreapproval result = client.updatePreapprovalAmount("pre-1", new BigDecimal("44.90"), "BRL", "change-plan-pre-1-SILVER");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo("PUT");
        assertThat(sent.uri().toString()).isEqualTo("https://api.mercadopago.com/preapproval/pre-1");
        JsonNode body = mapper.readTree(body(sent));
        assertThat(body.path("auto_recurring").path("transaction_amount").decimalValue()).isEqualByComparingTo("44.90");
        assertThat(body.path("auto_recurring").path("currency_id").asText()).isEqualTo("BRL");
        assertThat(body.has("status")).isFalse();
        assertThat(body.has("frequency")).isFalse();
        assertThat(sent.headers().firstValue("X-Idempotency-Key")).contains("change-plan-pre-1-SILVER");
        assertThat(result.getTransactionAmount()).isEqualByComparingTo("44.90");
        assertThat(result.getCurrencyId()).isEqualTo("BRL");
    }

    @Test
    void getPreapprovalParsesTransactionAmountAndCurrencyForAuthoritativeConfirmation() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\",\"auto_recurring\":{\"transaction_amount\":15.9,\"currency_id\":\"BRL\"}}");

        MercadoPagoPreapproval result = client.getPreapproval("pre-1");

        assertThat(result.getTransactionAmount()).isEqualByComparingTo("15.90");
        assertThat(result.getCurrencyId()).isEqualTo("BRL");
    }

    @Test
    void missingAutoRecurringAmountLeavesItNullRatherThanGuessing() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\"}");

        MercadoPagoPreapproval result = client.getPreapproval("pre-1");

        assertThat(result.getTransactionAmount()).isNull();
        assertThat(result.getCurrencyId()).isNull();
    }

    @ParameterizedTest @MethodSource("errorStatuses")
    void updatePreapprovalAmountMapsHttpErrorsWithoutLeakingUnstructuredProviderBody(int status) {
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn("provider-secret-body");
        assertThatThrownBy(() -> client.updatePreapprovalAmount("pre-1", new BigDecimal("44.90"), "BRL", "key-1"))
            .isInstanceOf(MercadoPagoException.class).hasMessageNotContaining("provider-secret-body");
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
