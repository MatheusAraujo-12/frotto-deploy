package com.localuz.service;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;
import com.localuz.repository.MercadoPagoWebhookEventRepository;import org.junit.jupiter.api.Test;
class MercadoPagoWebhookEventServiceTest{
 @Test void duplicateDeliveryIsIgnored(){MercadoPagoWebhookEventRepository repo=mock(MercadoPagoWebhookEventRepository.class);when(repo.existsByRequestIdAndEventTypeAndResourceId("req","subscription_preapproval","123")).thenReturn(false,true);MercadoPagoWebhookEventService service=new MercadoPagoWebhookEventService(repo);assertThat(service.registerDelivery("req","subscription_preapproval","123")).isTrue();assertThat(service.registerDelivery("req","subscription_preapproval","123")).isFalse();verify(repo,times(1)).save(any());}
}
