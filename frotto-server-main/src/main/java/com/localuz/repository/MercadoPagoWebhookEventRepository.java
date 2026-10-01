package com.localuz.repository;
import com.localuz.domain.MercadoPagoWebhookEvent;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MercadoPagoWebhookEventRepository extends JpaRepository<MercadoPagoWebhookEvent,Long>{
 boolean existsByRequestIdAndEventTypeAndResourceId(String requestId,String eventType,String resourceId);
 /** Row lock on the delivery: concurrent deliveries of the same event are serialized; the later one sees the earlier one's committed status. */
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select e from MercadoPagoWebhookEvent e where e.requestId=:requestId and e.eventType=:eventType and e.resourceId=:resourceId")
 Optional<MercadoPagoWebhookEvent> findForProcessing(@Param("requestId")String requestId,@Param("eventType")String eventType,@Param("resourceId")String resourceId);
}
