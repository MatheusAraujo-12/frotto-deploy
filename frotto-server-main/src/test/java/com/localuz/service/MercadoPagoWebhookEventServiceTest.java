package com.localuz.service;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;
import com.localuz.domain.MercadoPagoWebhookEvent;import com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus;
import com.localuz.repository.MercadoPagoWebhookEventRepository;import java.time.Instant;import java.util.Optional;import org.junit.jupiter.api.Test;import org.mockito.ArgumentCaptor;
class MercadoPagoWebhookEventServiceTest{
 private final MercadoPagoWebhookEventRepository repo=mock(MercadoPagoWebhookEventRepository.class);
 private final MercadoPagoWebhookEventService service=new MercadoPagoWebhookEventService(repo);

 @Test void duplicateDeliveryIsRegisteredOnlyOnceAsReceived(){
  when(repo.existsByRequestIdAndEventTypeAndResourceId("req","subscription_preapproval","123")).thenReturn(false,true);
  assertThat(service.registerDelivery("req","subscription_preapproval","123")).isTrue();
  assertThat(service.registerDelivery("req","subscription_preapproval","123")).isFalse();
  ArgumentCaptor<MercadoPagoWebhookEvent> saved=ArgumentCaptor.forClass(MercadoPagoWebhookEvent.class);
  verify(repo,times(1)).saveAndFlush(saved.capture());
  assertThat(saved.getValue().getProcessingStatus()).isEqualTo(MercadoPagoWebhookProcessingStatus.RECEIVED);
  assertThat(saved.getValue().getProcessingAttempts()).isZero();
  assertThat(saved.getValue().getProcessedAt()).isNull();
 }

 @Test void failureMarksTheSameRowRetryableAndCountsTheAttempt(){
  MercadoPagoWebhookEvent row=MercadoPagoWebhookEvent.received("req","payment","pay-1",Instant.now());
  when(repo.findForProcessing("req","payment","pay-1")).thenReturn(Optional.of(row));
  MercadoPagoWebhookEvent result=service.recordRetryableFailure("req","payment","pay-1","provider_http_5xx");
  assertThat(result).isSameAs(row);
  assertThat(row.getProcessingStatus()).isEqualTo(MercadoPagoWebhookProcessingStatus.RETRYABLE);
  assertThat(row.getProcessingResult()).isEqualTo("provider_http_5xx");
  assertThat(row.getProcessingAttempts()).isEqualTo(1);
  assertThat(row.getProcessedAt()).isNull();
  verify(repo).saveAndFlush(row);
 }

 @Test void failureNeverDowngradesATerminalRow(){
  MercadoPagoWebhookEvent row=MercadoPagoWebhookEvent.received("req","payment","pay-1",Instant.now());
  row.recordAttempt(MercadoPagoWebhookProcessingStatus.PROCESSED,"ingested",Instant.now());
  when(repo.findForProcessing("req","payment","pay-1")).thenReturn(Optional.of(row));
  service.recordRetryableFailure("req","payment","pay-1","provider_http_5xx");
  assertThat(row.getProcessingStatus()).isEqualTo(MercadoPagoWebhookProcessingStatus.PROCESSED);
  assertThat(row.getProcessingAttempts()).isEqualTo(1);
  verify(repo,never()).saveAndFlush(any());
 }
}
