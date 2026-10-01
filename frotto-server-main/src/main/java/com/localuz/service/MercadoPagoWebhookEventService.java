package com.localuz.service;
import com.localuz.domain.MercadoPagoWebhookEvent;
import com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus;
import com.localuz.repository.MercadoPagoWebhookEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
/**
 * E2: the short, independently-committed bookkeeping around a webhook delivery. Each method runs in
 * its own REQUIRES_NEW transaction so it never shares a row lock with MercadoPagoWebhookProcessor's
 * processing transaction (see MercadoPagoWebhookDeliveryService for the ordering).
 */
@Service public class MercadoPagoWebhookEventService {
 private final MercadoPagoWebhookEventRepository repository;
 public MercadoPagoWebhookEventService(MercadoPagoWebhookEventRepository repository){this.repository=repository;}

 /**
  * Persists the delivery as RECEIVED before any processing, so a crash mid-processing leaves a
  * re-processable row. Returns false when the row already exists. A concurrent insert of the same
  * delivery surfaces as the ux_mp_webhook_delivery violation, which the caller treats as "already registered".
  */
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean registerDelivery(String requestId,String eventType,String resourceId){
  if(repository.existsByRequestIdAndEventTypeAndResourceId(requestId,eventType,resourceId))return false;
  repository.saveAndFlush(MercadoPagoWebhookEvent.received(requestId,eventType,resourceId,Instant.now()));
  return true;
 }

 /**
  * Records an attempt that ended in an exception (its processing transaction was rolled back) as
  * RETRYABLE on the same row. A terminal row is never downgraded.
  */
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public MercadoPagoWebhookEvent recordRetryableFailure(String requestId,String eventType,String resourceId,String result){
  Instant now=Instant.now();
  MercadoPagoWebhookEvent event=repository.findForProcessing(requestId,eventType,resourceId)
   .orElseGet(()->MercadoPagoWebhookEvent.received(requestId,eventType,resourceId,now));
  if(event.getProcessingStatus()!=null&&event.getProcessingStatus().isTerminal())return event;
  event.recordAttempt(MercadoPagoWebhookProcessingStatus.RETRYABLE,result,now);
  repository.saveAndFlush(event);
  return event;
 }
}
