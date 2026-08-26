package com.localuz.domain;
import java.time.Instant;
import javax.persistence.*;
@Entity @Table(name="mercadopago_webhook_event",uniqueConstraints=@UniqueConstraint(name="ux_mp_webhook_delivery",columnNames={"request_id","event_type","resource_id"}))
public class MercadoPagoWebhookEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="request_id",length=128,nullable=false) private String requestId;
 @Column(name="event_type",length=64,nullable=false) private String eventType;
 @Column(name="resource_id",length=128,nullable=false) private String resourceId;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 public void setRequestId(String v){requestId=v;} public void setEventType(String v){eventType=v;} public void setResourceId(String v){resourceId=v;} public void setReceivedAt(Instant v){receivedAt=v;}
}
