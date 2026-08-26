package com.localuz.service;
import com.localuz.domain.MercadoPagoWebhookEvent;
import com.localuz.repository.MercadoPagoWebhookEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class MercadoPagoWebhookEventService {
 private final MercadoPagoWebhookEventRepository repository;
 public MercadoPagoWebhookEventService(MercadoPagoWebhookEventRepository repository){this.repository=repository;}
 @Transactional public boolean registerDelivery(String requestId,String eventType,String resourceId){
  if(repository.existsByRequestIdAndEventTypeAndResourceId(requestId,eventType,resourceId))return false;
  MercadoPagoWebhookEvent event=new MercadoPagoWebhookEvent();event.setRequestId(requestId);event.setEventType(eventType);event.setResourceId(resourceId);event.setReceivedAt(Instant.now());repository.save(event);return true;
 }
}
