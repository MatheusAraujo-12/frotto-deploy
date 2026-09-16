package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.config.MercadoPagoProperties;
import java.net.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class MercadoPagoFinancialHttpClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private MercadoPagoHttpClient client;
    private static final String CHARGE = "{\"id\":10,\"preapproval_id\":\"pre-1\",\"status\":\"processed\","
        + "\"transaction_amount\":\"100.00\",\"currency_id\":\"BRL\",\"debit_date\":\"2026-10-01T09:00:00-03:00\","
        + "\"last_modified\":\"2026-10-01T12:01:00Z\",\"payment\":{\"id\":20,\"status\":\"approved\"}}";

    @BeforeEach void setup() throws Exception {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAccessToken("test-secret");
        client = new MercadoPagoHttpClient(properties, new ObjectMapper(), http);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
    }

    @Test void discoveryUsesOnlyConfirmedParametersAndReturnsOnlyIds() throws Exception {
        when(response.body()).thenReturn("{\"paging\":{\"offset\":2,\"limit\":2,\"total\":4},\"results\":[{\"id\":12},{\"id\":11,\"status\":\"approved\"}]}");
        var page = client.searchAuthorizedPayments("pre-1", 2, 2);
        assertThat(page.ids()).containsExactly("12", "11");
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo(
            "https://api.mercadopago.com/authorized_payments/search?preapproval_id=pre-1&offset=2&limit=2");
        assertThat(request.getValue().method()).isEqualTo("GET");
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"paging\":null,\"results\":[]}",
        "{\"paging\":{\"offset\":0,\"limit\":0,\"total\":0},\"results\":[]}",
        "{\"paging\":{\"offset\":1,\"limit\":2,\"total\":0},\"results\":[]}",
        "{\"paging\":{\"offset\":0,\"limit\":2,\"total\":2},\"results\":[]}",
        "{\"paging\":{\"offset\":0,\"limit\":2,\"total\":1},\"results\":[{}]}",
        "{\"paging\":{\"offset\":0,\"limit\":2,\"total\":1},\"results\":[{\"id\":\"../secret\"}]}"
    })
    void discoveryRejectsInvalidOrInconsistentPages(String body) {
        when(response.body()).thenReturn(body);
        assertThatThrownBy(() -> client.searchAuthorizedPayments("pre-1", 0, 2)).isInstanceOf(MercadoPagoException.class);
    }

    @Test void parsesMinimalPaymentAndUsesFixedGetEndpoint() throws Exception {
        when(response.body()).thenReturn("{\"id\":20,\"status\":\"approved\",\"status_detail\":\"accredited\","
            + "\"transaction_amount\":\"100.00\",\"currency_id\":\"BRL\",\"transaction_amount_refunded\":10.25,"
            + "\"date_approved\":\"2026-10-01T09:00:00-03:00\",\"date_last_updated\":\"2026-10-01T12:01:00Z\","
            + "\"payer\":{\"email\":\"private@example.com\"},\"card\":{\"number\":\"private\"}}");
        var payment = client.getPayment("20");
        assertThat(payment.getTransactionAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("10.25");
        assertThat(payment.getDateApproved()).isEqualTo(java.time.Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(payment))
            .doesNotContain("payer", "card", "private", "test-secret");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getValue().uri().toString()).isEqualTo("https://api.mercadopago.com/v1/payments/20");
        assertThat(requests.getValue().method()).isEqualTo("GET");
    }

    @ParameterizedTest @ValueSource(strings = {"\"bad\"", "{}", "true", "[]"})
    void malformedRefundAmountIsNotSilentlyTreatedAsAbsent(String refund) {
        when(response.body()).thenReturn("{\"id\":20,\"status\":\"approved\",\"transaction_amount_refunded\":" + refund + "}");
        assertThatThrownBy(() -> client.getPayment("20")).isInstanceOf(MercadoPagoException.class);
    }

    @Test void enrichesAuthorizedPaymentWithoutInventingServiceDates() {
        when(response.body()).thenReturn(CHARGE);
        var charge = client.getAuthorizedPayment("10");
        assertThat(charge.getPaymentId()).isEqualTo("20");
        assertThat(charge.getPreapprovalId()).isEqualTo("pre-1");
        assertThat(charge.getTransactionAmount()).isEqualByComparingTo("100.00");
        assertThat(charge.getDebitDate()).isEqualTo(java.time.Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(charge.getDebitDateWithOffset()).isEqualTo(java.time.OffsetDateTime.parse("2026-10-01T09:00:00-03:00"));
        assertThat(charge.getDebitDateWithOffset().getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(-3));
    }

    @Test void preapprovalParsesContractualFrequencyWithoutAdditionalHttp() throws Exception {
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\",\"auto_recurring\":{\"frequency\":2,\"frequency_type\":\"months\"},\"next_payment_date\":\"2026-12-01T12:00:00Z\"}");
        var contract = client.getPreapproval("pre-1");
        assertThat(contract.getFrequency()).isEqualTo(2);
        assertThat(contract.getFrequencyType()).isEqualTo("months");
        assertThat(contract.getNextPaymentDate()).isEqualTo(java.time.Instant.parse("2026-12-01T12:00:00Z"));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest @ValueSource(strings = {"null", "0", "-1", "1.5", "2147483648", "\"1\"", "true", "{}"})
    void invalidFrequencyIsNotGuessed(String frequency) {
        when(response.body()).thenReturn("{\"id\":\"pre-1\",\"status\":\"authorized\",\"auto_recurring\":{\"frequency\":" + frequency + ",\"frequency_type\":\"months\"}}");
        assertThat(client.getPreapproval("pre-1").getFrequency()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"null", "\"invalid\"", "\"2026-01-31T12:00:00\""})
    void invalidOrOffsetlessDebitRemainsUnknown(String debit) {
        when(response.body()).thenReturn("{\"id\":10,\"preapproval_id\":\"pre-1\",\"status\":\"processed\",\"debit_date\":" + debit + "}");
        var charge = client.getAuthorizedPayment("10");
        assertThat(charge.getDebitDate()).isNull();
        assertThat(charge.getDebitDateWithOffset()).isNull();
    }

    @Test void correlatesOnlyUniqueSearchResultAndConfirmsIndividualResource() throws Exception {
        when(response.body()).thenReturn("{\"paging\":{\"total\":1},\"results\":[" + CHARGE + "]}", CHARGE);
        assertThat(client.findAuthorizedPaymentByPaymentId("20")).get()
            .extracting(com.localuz.service.dto.MercadoPagoAuthorizedPayment::getId).isEqualTo("10");
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues()).extracting(r -> r.uri().toString()).containsExactly(
            "https://api.mercadopago.com/authorized_payments/search?payment_id=20&limit=2&offset=0",
            "https://api.mercadopago.com/authorized_payments/10");
    }

    @ParameterizedTest @ValueSource(strings = {"payment", "preapproval", "charge"})
    void individualGetMustAgreeWithUniqueSearch(String conflict) {
        String confirmed = CHARGE;
        if ("payment".equals(conflict)) confirmed = confirmed.replace("\"id\":20", "\"id\":21");
        if ("preapproval".equals(conflict)) confirmed = confirmed.replace("pre-1", "pre-other");
        if ("charge".equals(conflict)) confirmed = confirmed.replace("\"id\":10", "\"id\":11");
        when(response.body()).thenReturn("{\"paging\":{\"total\":1},\"results\":[" + CHARGE + "]}", confirmed);
        assertThatThrownBy(() -> client.findAuthorizedPaymentByPaymentId("20")).isInstanceOf(MercadoPagoException.class);
    }

    @Test void emptySearchDoesNotGuessAnInvoice() {
        when(response.body()).thenReturn("{\"paging\":{\"total\":0},\"results\":[]}");
        assertThat(client.findAuthorizedPaymentByPaymentId("20")).isEmpty();
    }

    @Test void rejectsAmbiguousSearch() {
        when(response.body()).thenReturn("{\"paging\":{\"total\":2},\"results\":[" + CHARGE + "," + CHARGE + "]}");
        assertThatThrownBy(() -> client.findAuthorizedPaymentByPaymentId("20")).isInstanceOf(MercadoPagoException.class);
    }

    @Test void rejectsSearchOrGetBelongingToDifferentPayment() {
        when(response.body()).thenReturn("{\"paging\":{\"total\":1},\"results\":[" + CHARGE + "]}");
        assertThatThrownBy(() -> client.findAuthorizedPaymentByPaymentId("21")).isInstanceOf(MercadoPagoException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"../20", "20?x=1", "https://attacker.test", "20&payer_id=1"})
    void rejectsUnsafeIdsBeforeHttp(String id) {
        assertThatThrownBy(() -> client.getPayment(id)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.findAuthorizedPaymentByPaymentId(id)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(http);
    }

    @Test void rejectsWrongReturnedResourceId() {
        when(response.body()).thenReturn("{\"id\":21,\"status\":\"approved\"}");
        assertThatThrownBy(() -> client.getPayment("20")).isInstanceOf(MercadoPagoException.class);
    }

    @Test void invalidFinancialAmountIsNotSilentlyReplacedWithZero() {
        when(response.body()).thenReturn("{\"id\":20,\"status\":\"approved\",\"transaction_amount\":\"garbage\"}");
        assertThatThrownBy(() -> client.getPayment("20")).isInstanceOf(MercadoPagoException.class);
    }

    @Test void providerFailureDoesNotFabricatePayment() {
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("private-raw-response");
        assertThatThrownBy(() -> client.getPayment("20")).isInstanceOf(MercadoPagoException.class)
            .hasMessageNotContaining("private-raw-response");
    }
}
