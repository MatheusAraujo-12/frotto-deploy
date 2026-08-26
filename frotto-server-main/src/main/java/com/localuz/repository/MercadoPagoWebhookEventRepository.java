package com.localuz.repository;
import com.localuz.domain.MercadoPagoWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MercadoPagoWebhookEventRepository extends JpaRepository<MercadoPagoWebhookEvent,Long>{boolean existsByRequestIdAndEventTypeAndResourceId(String requestId,String eventType,String resourceId);}
