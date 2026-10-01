package com.localuz.domain;
import com.localuz.domain.enumeration.MercadoPagoWebhookProcessingStatus;
import java.time.Instant;
import javax.persistence.*;
@Entity @Table(name="mercadopago_webhook_event",uniqueConstraints=@UniqueConstraint(name="ux_mp_webhook_delivery",columnNames={"request_id","event_type","resource_id"}))
public class MercadoPagoWebhookEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="request_id",length=128,nullable=false) private String requestId;
 @Column(name="event_type",length=64,nullable=false) private String eventType;
 @Column(name="resource_id",length=128,nullable=false) private String resourceId;
 @Column(name="received_at",nullable=false) private Instant receivedAt;
 /** Set only when the delivery reaches a terminal status (PROCESSED / IGNORED_FINAL). */
 @Column(name="processed_at") private Instant processedAt;
 @Enumerated(EnumType.STRING) @Column(name="processing_status",length=16,nullable=false) private MercadoPagoWebhookProcessingStatus processingStatus;
 /** Safe, enum-derived code of the last attempt (e.g. "ingested", "subscription_not_found", "provider_http_5xx"). */
 @Column(name="processing_result",length=64) private String processingResult;
 @Column(name="processing_attempts",nullable=false) private int processingAttempts;
 @Column(name="last_processing_at") private Instant lastProcessingAt;

 public static MercadoPagoWebhookEvent received(String requestId,String eventType,String resourceId,Instant now){
  MercadoPagoWebhookEvent event=new MercadoPagoWebhookEvent();
  event.requestId=requestId;event.eventType=eventType;event.resourceId=resourceId;event.receivedAt=now;
  event.processingStatus=MercadoPagoWebhookProcessingStatus.RECEIVED;event.processingAttempts=0;
  return event;
 }
 /** Records one finished processing attempt; processedAt is stamped only for a terminal status. */
 public void recordAttempt(MercadoPagoWebhookProcessingStatus status,String result,Instant now){
  processingStatus=status;processingResult=result;processingAttempts++;lastProcessingAt=now;
  if(status.isTerminal()&&processedAt==null)processedAt=now;
 }
 public Long getId(){return id;} public String getRequestId(){return requestId;} public String getEventType(){return eventType;} public String getResourceId(){return resourceId;}
 public Instant getReceivedAt(){return receivedAt;} public Instant getProcessedAt(){return processedAt;}
 public MercadoPagoWebhookProcessingStatus getProcessingStatus(){return processingStatus;} public String getProcessingResult(){return processingResult;}
 public int getProcessingAttempts(){return processingAttempts;} public Instant getLastProcessingAt(){return lastProcessingAt;}
 public void setRequestId(String v){requestId=v;} public void setEventType(String v){eventType=v;} public void setResourceId(String v){resourceId=v;} public void setReceivedAt(Instant v){receivedAt=v;}
 public void setProcessedAt(Instant v){processedAt=v;}
 public void setProcessingStatus(MercadoPagoWebhookProcessingStatus v){processingStatus=v;}
}
