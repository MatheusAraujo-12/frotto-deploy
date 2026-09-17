package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import com.localuz.service.MercadoPagoException.Category;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MercadoPagoException.Category is the only safe diagnostic signal the recurring reconciliation
 * scheduler is allowed to log for a PROVIDER_FAILURE (see RecurringBillingReconciliationScheduler).
 * It must be derivable purely from the HTTP status and the cause's Java type - never from the
 * exception message or the provider response body, both of which may still carry provider text.
 */
class MercadoPagoExceptionTest {

    @ParameterizedTest
    @CsvSource({"400,HTTP_400", "401,HTTP_401", "403,HTTP_403", "404,HTTP_404", "429,HTTP_429",
        "409,HTTP_CLIENT_ERROR", "422,HTTP_CLIENT_ERROR", "500,HTTP_5XX", "503,HTTP_5XX"})
    void classifiesHttpStatusesIntoSafeCategories(int status, Category expected) {
        assertThat(new MercadoPagoException("msg", false, status, null, null).getCategory()).isEqualTo(expected);
    }

    @Test void timeoutIsDistinguishedFromNetworkFailure() {
        assertThat(new MercadoPagoException("timeout", true, new HttpTimeoutException("t")).getCategory()).isEqualTo(Category.TIMEOUT);
    }

    @Test void jsonParsingFailureIsDistinguishedFromNetworkFailure() {
        assertThat(new MercadoPagoException("bad json", false, new JsonParseException(null, "bad", (com.fasterxml.jackson.core.JsonLocation) null)).getCategory()).isEqualTo(Category.PARSING_ERROR);
    }

    @Test void plainIoFailureIsClassifiedAsNetworkError() {
        assertThat(new MercadoPagoException("conn refused", true, new java.io.IOException("conn refused")).getCategory()).isEqualTo(Category.NETWORK_ERROR);
    }

    @Test void interruptedRequestIsItsOwnCategory() {
        assertThat(new MercadoPagoException("interrupted", true, new InterruptedException()).getCategory()).isEqualTo(Category.INTERRUPTED);
    }

    @Test void validationFailureWithNoCauseAndNoHttpStatusIsAContractError() {
        assertThat(new MercadoPagoException("Mercado Pago returned an invalid financial response", false).getCategory()).isEqualTo(Category.CONTRACT_ERROR);
    }

    @Test void categoryNeverLeaksIntoTheExceptionMessage() {
        MercadoPagoException exception = new MercadoPagoException("secret-provider-body-must-not-appear-here", false, 400, null, null);
        assertThat(exception.getCategory().name()).isEqualTo("HTTP_400");
        assertThat(exception.getCategory().name()).doesNotContain("secret-provider-body-must-not-appear-here");
    }

    @ParameterizedTest
    @CsvSource({"bad_request", "PA400", "'bad_request, PA400'", "invalid-parameter", "A", "'a_b-C1, d_E-2'"})
    void providerCodesMatchingTheSafeEnumLikeShapeAreKept(String code) {
        assertThat(new MercadoPagoException("x", false, 400, code, null).getSafeProviderErrorCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @CsvSource({
        "'invalid parameter: payer_email=someone@example.com'",
        "'preapproval_id 1234567890abcdef is not eligible'",
        "'code with spaces'",
        "'trailing, '",
        "', leading'",
    })
    void providerCodesThatDoNotMatchTheSafeShapeAreWithheld(String code) {
        assertThat(new MercadoPagoException("x", false, 400, code, null).getSafeProviderErrorCode()).isNull();
    }

    @Test void oversizedProviderCodeIsWithheld() {
        String tooLong = "a".repeat(41);
        assertThat(new MercadoPagoException("x", false, 400, tooLong, null).getSafeProviderErrorCode()).isNull();
    }

    @Test void tooManyJoinedCodesAreWithheld() {
        String sixCodes = String.join(", ", java.util.Collections.nCopies(6, "code"));
        assertThat(new MercadoPagoException("x", false, 400, sixCodes, null).getSafeProviderErrorCode()).isNull();
    }

    @Test void absentProviderCodeStaysNullRatherThanFabricated() {
        assertThat(new MercadoPagoException("x", false, 400, null, null).getSafeProviderErrorCode()).isNull();
    }

    @Test void safeProviderErrorCodeIsNullWhenThereIsNoHttpStatus() {
        assertThat(new MercadoPagoException("timeout", true, new HttpTimeoutException("t")).getSafeProviderErrorCode()).isNull();
    }
}
