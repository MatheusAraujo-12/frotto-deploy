package com.localuz.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.localuz.service.MercadoPagoException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;

class ExceptionTranslatorMercadoPagoTest {
    @Test
    void hidesProviderDetailsFromFrontend() {
        ExceptionTranslator translator = new ExceptionTranslator(mock(Environment.class));
        MercadoPagoException failure = new MercadoPagoException(
            "Mercado Pago request failed with status 400: sensitive provider detail",
            false, 400, "bad_request", "sensitive provider detail"
        );

        ResponseEntity<Problem> response = translator.handleMercadoPagoFailure(failure, mock(NativeWebRequest.class));

        assertThat(response.getStatusCodeValue()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Unable to create payment checkout");
        assertThat(response.getBody().getDetail()).doesNotContain("sensitive provider detail", "bad_request");
    }
}
