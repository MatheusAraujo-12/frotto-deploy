package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.localuz.service.dto.RecurringReconciliationResult.Outcome.*;

import com.localuz.config.RecurringBillingReconciliationProperties;
import com.localuz.service.RecurringBillingReservationService.Candidate;
import com.localuz.service.dto.MercadoPagoAuthorizedPaymentPage;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class RecurringBillingReconciliationServiceTest {
    private final MercadoPagoClient client = mock(MercadoPagoClient.class);
    private final MercadoPagoFinancialIngestion ingestion = mock(MercadoPagoFinancialIngestion.class);
    private final RecurringBillingReservationService reservations = mock(RecurringBillingReservationService.class);
    private final RecurringBillingReconciliationProperties config = new RecurringBillingReconciliationProperties();
    private final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    private final Candidate candidate = new Candidate(1L, "pre-1");
    private final RecurringBillingReconciliationService service = new RecurringBillingReconciliationService(client, ingestion, reservations,
        config, Clock.fixed(now, ZoneOffset.UTC));

    RecurringBillingReconciliationServiceTest() {
        when(reservations.reserve(eq(candidate), any(), any())).thenReturn(true);
    }
    private MercadoPagoAuthorizedPaymentPage page(int offset, int limit, int total, String... ids) {
        return new MercadoPagoAuthorizedPaymentPage(offset, limit, total, List.of(ids));
    }

    @Test void emptyDiscoveryReservesBeforeHttpAndDoesNotWriteFinancialData() {
        when(client.searchAuthorizedPayments("pre-1", 0, 20)).thenReturn(page(0,20,0));
        var result = service.reconcile(candidate, new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(COMPLETE);
        assertThat(result.discovered()).isZero();
        var order = inOrder(reservations, client);
        order.verify(reservations).reserve(candidate, now, now.minusSeconds(3600));
        order.verify(client).searchAuthorizedPayments("pre-1",0,20);
        verifyNoInteractions(ingestion);
    }

    @Test void rejectedReservationNeverCallsProvider() {
        when(reservations.reserve(any(),any(),any())).thenReturn(false);
        assertThat(service.reconcile(candidate,new RecurringReconciliationBudget(10)).outcome()).isEqualTo(SKIP_ALREADY_RESERVED);
        verifyNoInteractions(client,ingestion);
    }

    @Test void missingProviderReferenceIsSkipped() {
        assertThat(service.reconcile(new Candidate(1L,""),new RecurringReconciliationBudget(10)).outcome()).isEqualTo(INVALID_CANDIDATE);
        verifyNoInteractions(client, reservations, ingestion);
    }

    @Test void pagesAreBoundedDeduplicatedAndProcessedInLocalOrder() {
        config.setPageSize(2);
        when(client.searchAuthorizedPayments("pre-1",0,2)).thenReturn(page(0,2,4,"b","a"));
        when(client.searchAuthorizedPayments("pre-1",2,2)).thenReturn(page(2,2,4,"c","a"));
        var result = service.reconcile(candidate,new RecurringReconciliationBudget(20));
        assertThat(result.discovered()).isEqualTo(3);
        var order = inOrder(ingestion);
        for (String id : List.of("a","b","c")) order.verify(ingestion).fetch(eq("subscription_authorized_payment"),eq(id),eq("pre-1"),any());
    }

    @ParameterizedTest @ValueSource(strings={"pages","items","http"})
    void exhaustedBudgetIsExplicitlyIncomplete(String limit) {
        config.setPageSize(1);
        if (limit.equals("pages")) config.setMaxPages(1);
        if (limit.equals("items")) config.setMaxDiscoveredItems(1);
        when(client.searchAuthorizedPayments("pre-1",0,1)).thenReturn(page(0,1,2,"a"));
        var result = service.reconcile(candidate,new RecurringReconciliationBudget(limit.equals("http") ? 1 : 10));
        assertThat(result.outcome()).isEqualTo(DISCOVERY_INCOMPLETE);
    }

    @ParameterizedTest @ValueSource(ints={404,429,500})
    void providerErrorIsOperationalAndReservationRemains(int status) {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",true,status,null,null));
        var budget=new RecurringReconciliationBudget(10);
        var result = service.reconcile(candidate,budget);
        assertThat(result.outcome()).isEqualTo(status==429 ? RATE_LIMITED : PROVIDER_FAILURE);
        assertThat(budget.rateLimited()).isEqualTo(status==429);
        // The safe category/http status must reach the result even though it never appears in the outcome enum.
        assertThat(result.failureHttpStatus()).isEqualTo(status);
        assertThat(result.failureCategory()).isEqualTo(new MercadoPagoException("x",true,status,null,null).getCategory().name());
        verify(reservations).reserve(any(),any(),any());
        verifyNoMoreInteractions(reservations);
        verifyNoInteractions(ingestion);
    }

    @Test void timeoutAndNetworkFailuresCarryASafeCategoryWithoutAnHttpStatus() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("timeout", true, new java.net.http.HttpTimeoutException("t")));
        var result = service.reconcile(candidate, new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(PROVIDER_FAILURE);
        assertThat(result.failureHttpStatus()).isNull();
        assertThat(result.failureCategory()).isEqualTo("TIMEOUT");
    }

    @Test void successfulOutcomesNeverCarryAFailureCategory() {
        when(client.searchAuthorizedPayments("pre-1", 0, 20)).thenReturn(page(0,20,0));
        var result = service.reconcile(candidate, new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(COMPLETE);
        assertThat(result.failureCategory()).isNull();
        assertThat(result.failureHttpStatus()).isNull();
        assertThat(result.providerErrorCode()).isNull();
    }

    @Test void safeStructuredProviderCodeReachesTheResult() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",false,400,"bad_request, PA400","payer invalid"));
        var result = service.reconcile(candidate,new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(PROVIDER_FAILURE);
        assertThat(result.providerErrorCode()).isEqualTo("bad_request, PA400");
    }

    @Test void unsafeShapedProviderCodeIsWithheldFromTheResult() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",false,400,
                "invalid parameter: payer_email=someone@example.com","payer invalid"));
        var result = service.reconcile(candidate,new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(PROVIDER_FAILURE);
        assertThat(result.providerErrorCode()).isNull();
    }

    @Test void missingProviderCodeLeavesResultFieldNull() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",false,400,null,null));
        var result = service.reconcile(candidate,new RecurringReconciliationBudget(10));
        assertThat(result.outcome()).isEqualTo(PROVIDER_FAILURE);
        assertThat(result.providerErrorCode()).isNull();
    }

    @Test void rateLimitPropagatesRetryAfterSecondsToTheBudget() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",true,429,null,null,120));
        var budget=new RecurringReconciliationBudget(10);
        assertThat(service.reconcile(candidate,budget).outcome()).isEqualTo(RATE_LIMITED);
        assertThat(budget.retryAfterSeconds()).isEqualTo(120);
    }

    @Test void rateLimitWithoutRetryAfterLeavesBudgetRetryAfterNull() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt()))
            .thenThrow(new MercadoPagoException("secret body must not be logged",true,429,null,null));
        var budget=new RecurringReconciliationBudget(10);
        assertThat(service.reconcile(candidate,budget).outcome()).isEqualTo(RATE_LIMITED);
        assertThat(budget.retryAfterSeconds()).isNull();
    }

    @Test void timeoutLeavesFinancialStateUntouched() {
        when(client.searchAuthorizedPayments(anyString(),anyInt(),anyInt())).thenThrow(new MercadoPagoException("timeout",true));
        assertThat(service.reconcile(candidate,new RecurringReconciliationBudget(10)).outcome()).isEqualTo(PROVIDER_FAILURE);
        verifyNoInteractions(ingestion);
    }

    @ParameterizedTest @ValueSource(strings={"offset","zero_limit","short_page","no_progress"})
    void invalidPagingDoesNotPretendCompletion(String invalid) {
        config.setPageSize(1);
        switch(invalid) {
            case "offset": when(client.searchAuthorizedPayments("pre-1",0,1)).thenReturn(page(1,1,1,"a")); break;
            case "zero_limit": when(client.searchAuthorizedPayments("pre-1",0,1)).thenReturn(page(0,0,1)); break;
            case "short_page": when(client.searchAuthorizedPayments("pre-1",0,1)).thenReturn(page(0,1,2)); break;
            default:
                when(client.searchAuthorizedPayments("pre-1",0,1)).thenReturn(page(0,1,3,"a"));
                when(client.searchAuthorizedPayments("pre-1",1,1)).thenReturn(page(1,1,3,"a"));
        }
        assertThat(service.reconcile(candidate,new RecurringReconciliationBudget(10)).outcome()).isEqualTo(INVALID_RESPONSE);
        verifyNoInteractions(ingestion);
    }

    @Test void invalidConfigurationFailsBeforeReservationOrProvider() {
        config.setBatchSize(0);
        assertThatThrownBy(()->service.reconcile(candidate,new RecurringReconciliationBudget(10))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reservations,client,ingestion);
    }
}
