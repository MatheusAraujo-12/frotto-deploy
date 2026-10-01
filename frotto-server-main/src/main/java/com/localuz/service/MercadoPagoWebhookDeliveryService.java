package com.localuz.service;

import com.localuz.domain.MercadoPagoWebhookEvent;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * E2: one Mercado Pago webhook delivery, end to end. Deliberately NOT @Transactional - each step is
 * its own transaction, in this order:
 *
 *   1. registerDelivery (REQUIRES_NEW, committed): the delivery exists as RECEIVED before any effect.
 *   2. processor.process (one transaction, delivery row locked): terminal rows are acknowledged as
 *      DUPLICATE; RECEIVED/RETRYABLE rows are processed and their status committed WITH the financial
 *      effect, atomically.
 *   3. only if step 2 threw (provider failure, persistence failure...): its transaction was rolled back
 *      and its row lock released, so recordRetryableFailure (REQUIRES_NEW) can safely mark the SAME row
 *      RETRYABLE; the exception is re-thrown so the HTTP layer answers with a retry status.
 *
 * Financial idempotency never depends on this table alone: two deliveries with different request ids
 * for the same payment are both processed, and the ingestion's subscription lock plus its
 * external_payment_id / external_authorized_payment_id upserts keep it to one invoice and one attempt.
 */
@Service
public class MercadoPagoWebhookDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoWebhookDeliveryService.class);
    private final MercadoPagoWebhookEventService events;
    private final MercadoPagoWebhookProcessor processor;

    public MercadoPagoWebhookDeliveryService(MercadoPagoWebhookEventService events, MercadoPagoWebhookProcessor processor) {
        this.events = events;
        this.processor = processor;
    }

    public MercadoPagoWebhookProcessor.Result deliver(String requestId, String eventType, String resourceId) {
        try {
            events.registerDelivery(requestId, eventType, resourceId);
        } catch (RuntimeException concurrentRegistration) {
            // Another delivery of the same event inserted the row first; step 2 locks and reads it. Matched on the
            // cause chain (translated DataIntegrityViolationException or a raw JPA PersistenceException alike) and
            // only for the delivery's own unique key - any other failure is re-thrown untouched.
            if (!isDeliveryDuplicate(concurrentRegistration)) throw concurrentRegistration;
        }
        try {
            return processor.process(requestId, eventType, resourceId);
        } catch (RuntimeException failure) {
            String result = failureResult(failure);
            try {
                MercadoPagoWebhookEvent event = events.recordRetryableFailure(requestId, eventType, resourceId, result);
                LOG.warn("Mercado Pago webhook event outcome eventId={} requestId={} eventType={} resourceId={} processingStatus={} processingResult={} attempt={} willRetry=true",
                    event.getId(), requestId, eventType, resourceId, event.getProcessingStatus(), result, event.getProcessingAttempts());
            } catch (RuntimeException bookkeepingFailure) {
                // The row stays RECEIVED/RETRYABLE (never terminal), so the next delivery still re-processes it.
                LOG.warn("Mercado Pago webhook failure bookkeeping failed requestId={} eventType={} resourceId={} processingResult={}",
                    requestId, eventType, resourceId, result);
            }
            throw failure;
        }
    }

    /** Safe, enum-derived result code: never the exception message, which may echo provider text. */
    static String failureResult(RuntimeException failure) {
        if (failure instanceof MercadoPagoException) {
            return "provider_" + ((MercadoPagoException) failure).getCategory().name().toLowerCase(Locale.ROOT);
        }
        if (failure instanceof DataAccessException) return "persistence_failure";
        return "operational_failure";
    }

    /** True only for the delivery's own unique key (request_id, event_type, resource_id) - never another constraint. */
    public static boolean isDeliveryDuplicate(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                org.hibernate.exception.ConstraintViolationException violation = (org.hibernate.exception.ConstraintViolationException) cause;
                String constraint = violation.getConstraintName();
                return ("ux_mp_webhook_delivery".equals(constraint) || "mercadopago_webhook_event.ux_mp_webhook_delivery".equals(constraint))
                    && violation.getErrorCode() == 1062;
            }
        }
        return false;
    }
}
