package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Safe provider-failure diagnostics: useful context is kept, secrets/PII/log injection never are. */
@SuppressWarnings("unchecked") // Mockito cannot preserve HttpClient.send's generic BodyHandler type token.
class MercadoPagoProviderDiagnosticsTest {
    private static final String TOKEN = "APP_USR-diagnostics-secret-token";
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private MercadoPagoHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAccessToken(TOKEN);
        properties.setReadTimeoutMillis(500);
        client = new MercadoPagoHttpClient(properties, new ObjectMapper(), http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        headers(Map.of());
    }

    private void headers(Map<String, List<String>> values) {
        when(response.headers()).thenReturn(HttpHeaders.of(values, (name, value) -> true));
    }

    private MercadoPagoException searchFails(int status, String body) {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return catchThrowableOfType(() -> client.searchAuthorizedPayments("pre-1"), MercadoPagoException.class);
    }

    private static void assertNoSecretOrPii(MercadoPagoException failure) {
        assertThat(failure.diagnostics()).doesNotContain(TOKEN, "Bearer", "Authorization", "@", "\n", "\r");
        assertThat(failure.getMessage()).doesNotContain(TOKEN);
    }

    // MP-1
    @Test
    void badRequestWithHumanReadableErrorIsNoLongerLostEntirely() {
        MercadoPagoException failure = searchFails(400, "{\"error\":\"Bad Request\",\"message\":\"Invalid preapproval_id\"}");

        // The legacy enum-like code stays withheld (unchanged contract) ...
        assertThat(failure.getSafeProviderErrorCode()).isNull();
        // ... but the safe diagnostic fields now carry the useful context.
        assertThat(failure.getOperation()).isEqualTo("authorized_payments.search");
        assertThat(failure.getProviderError()).isEqualTo("Bad Request");
        assertThat(failure.getSafeProviderMessage()).isEqualTo("Invalid preapproval_id");
        assertThat(failure.diagnostics()).isEqualTo(
            "operation=authorized_payments.search httpStatus=400 providerError=\"Bad Request\" providerMessage=\"Invalid preapproval_id\"");
        assertNoSecretOrPii(failure);
    }

    // M2-12: the exact staging failure that motivated M2 stays fully diagnosable after the paging fix.
    @Test
    void stagingInvalidLimitFailureIsStillDiagnosedSafely() {
        headers(Map.of("x-request-id", List.of("6f1c2a7e-0b9d-4c55-9f0e-1a2b3c4d5e6f")));
        MercadoPagoException failure = searchFails(400,
            "{\"message\":\"Invalid value for limit\",\"error\":\"bad_request\",\"status\":400,\"cause\":[]}");

        assertThat(failure.getSafeProviderErrorCode()).isEqualTo("bad_request");
        assertThat(failure.diagnostics()).isEqualTo("operation=authorized_payments.search httpStatus=400 providerError=\"bad_request\" "
            + "providerRequestId=6f1c2a7e-0b9d-4c55-9f0e-1a2b3c4d5e6f providerMessage=\"Invalid value for limit\"");
        assertNoSecretOrPii(failure);
    }

    // MP-2
    @Test
    void firstCauseCodeIsCapturedEvenWithoutTopLevelError() {
        MercadoPagoException failure = searchFails(400, "{\"message\":\"Validation error\",\"cause\":[{\"code\":\"invalid_preapproval\"},{\"code\":\"second\"}]}");

        assertThat(failure.getFirstCauseCode()).isEqualTo("invalid_preapproval");
        assertThat(failure.getProviderError()).isNull(); // nothing invented when the provider sends no error/code
        assertThat(failure.diagnostics()).contains("firstCauseCode=\"invalid_preapproval\"", "providerMessage=\"Validation error\"");
    }

    @Test
    void firstCauseCodeIsCapturedWhenTheCombinedCodeFailsTheLegacyShape() {
        MercadoPagoException failure = searchFails(400,
            "{\"error\":\"Bad Request\",\"message\":\"Validation error\",\"cause\":[{\"code\":\"invalid_preapproval\"}]}");

        assertThat(failure.getSafeProviderErrorCode()).isNull(); // "Bad Request, invalid_preapproval" has a space
        assertThat(failure.getProviderError()).isEqualTo("Bad Request");
        assertThat(failure.getFirstCauseCode()).isEqualTo("invalid_preapproval");
    }

    @Test
    void objectShapedCauseIsAlsoRead() {
        MercadoPagoException failure = searchFails(400, "{\"message\":\"x\",\"cause\":{\"code\":\"single_cause\"}}");

        assertThat(failure.getFirstCauseCode()).isEqualTo("single_cause");
    }

    // MP-3
    @Test
    void providerRequestIdHeaderIsCaptured() {
        headers(Map.of("x-request-id", List.of("abc-123")));
        MercadoPagoException failure = searchFails(400, "{\"error\":\"bad_request\"}");

        assertThat(failure.getProviderRequestId()).isEqualTo("abc-123");
        assertThat(failure.diagnostics()).contains("providerRequestId=abc-123");
    }

    @Test
    void malformedProviderRequestIdIsWithheldNotLogged() {
        headers(Map.of("x-request-id", List.of("abc-123 FAKE=1")));
        MercadoPagoException failure = searchFails(400, "{\"error\":\"bad_request\"}");

        assertThat(failure.getProviderRequestId()).isNull();
        assertThat(failure.diagnostics()).doesNotContain("FAKE");
    }

    // MP-4
    @ParameterizedTest
    @ValueSource(strings = {"<html><body>gateway secret-body</body></html>", "plain text secret-body", "[\"secret-body\"]", "\"secret-body\"", ""})
    void nonJsonOrNonObjectBodiesFailSafelyWithoutEchoingTheBody(String body) {
        headers(Map.of("x-request-id", List.of("req-502")));
        MercadoPagoException failure = searchFails(502, body);

        assertThat(failure.getCategory()).isEqualTo(MercadoPagoException.Category.HTTP_5XX);
        assertThat(failure.diagnostics()).isEqualTo("operation=authorized_payments.search httpStatus=502 providerRequestId=req-502");
        assertThat(failure.getMessage()).doesNotContain("secret-body", "html");
    }

    // MP-5
    @ParameterizedTest
    @ValueSource(strings = {
        "invalid header Authorization: Bearer " + TOKEN,
        "token " + TOKEN + " rejected",
        "jwt eyJhbGciOi.eyJzdWIiOi.c2lnbmF0dXJl rejected",
        "payer someone@example.com is invalid",
        "document 123.456.789-09 is invalid",
        "card 4111 1111 1111 1111 declined",
        "value=1 injected",
        "quote \" injected",
        "<script>alert(1)</script>",
    })
    void unsafeProviderMessageIsWithheldAndFlaggedUnavailable(String message) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("error", "bad_request", "message", message));
        MercadoPagoException failure = searchFails(400, body);

        assertThat(failure.getSafeProviderMessage()).isNull();
        assertThat(failure.diagnostics()).endsWith("providerMessageUnavailable").doesNotContain(message);
        assertNoSecretOrPii(failure);
    }

    @Test
    void lineBreaksInProviderTextCannotForgeExtraLogLines() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("error", "bad\nrequest", "message", "first line\nINFO forged entry"));
        MercadoPagoException failure = searchFails(400, body);

        assertThat(failure.diagnostics()).doesNotContain("\n", "\r")
            .contains("providerError=\"bad request\"", "providerMessage=\"first line INFO forged entry\"");
    }

    @Test
    void unsafeErrorAndCauseCodesAreWithheld() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
            "error", "x=1", "message", "ok", "cause", List.of(Map.of("code", "user@example.com"))));
        MercadoPagoException failure = searchFails(400, body);

        assertThat(failure.getProviderError()).isNull();
        assertThat(failure.getFirstCauseCode()).isNull();
        assertThat(failure.diagnostics()).isEqualTo("operation=authorized_payments.search httpStatus=400 providerMessage=\"ok\"");
    }

    @Test
    void oversizedProviderMessageIsBoundedNotPrintedWhole() throws Exception {
        String longMessage = "a".repeat(500);
        MercadoPagoException failure = searchFails(400, new ObjectMapper().writeValueAsString(Map.of("message", longMessage)));

        assertThat(failure.getSafeProviderMessage()).hasSize(160);
    }

    @Test
    void ambiguousPutFailureKeepsItsDiagnostics() {
        headers(Map.of("x-request-id", List.of("put-503")));
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("{\"error\":\"service_unavailable\",\"message\":\"Try again later\"}");

        MercadoPagoException failure = catchThrowableOfType(
            () -> client.updatePreapprovalAmount("pre-1", new BigDecimal("99.90"), "BRL", "change-1"), MercadoPagoException.class);

        assertThat(failure.isAmbiguous()).isTrue();
        assertThat(failure.diagnostics()).isEqualTo(
            "operation=preapproval.update httpStatus=503 providerError=\"service_unavailable\" providerRequestId=put-503 providerMessage=\"Try again later\"");
    }

    @Test
    void operationLabelsComeFromFixedEndpointsNeverFromResourceIds() {
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("{}");

        assertThat(catchThrowableOfType(() -> client.getPreapproval("pre-1"), MercadoPagoException.class).getOperation())
            .isEqualTo("preapproval.get");
        assertThat(catchThrowableOfType(() -> client.getPayment("123"), MercadoPagoException.class).getOperation())
            .isEqualTo("payments.get");
        assertThat(catchThrowableOfType(() -> client.getAuthorizedPayment("10"), MercadoPagoException.class).getOperation())
            .isEqualTo("authorized_payments.get");
    }

    @Test
    void nonHttpFailuresHaveNoDiagnosticsLine() {
        assertThat(new MercadoPagoException("timeout", true, new java.net.http.HttpTimeoutException("t")).diagnostics()).isNull();
    }
}
