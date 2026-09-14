package com.localuz.web.rest;

import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.MercadoPagoException;
import com.localuz.service.MercadoPagoWebhookProcessor;
import com.localuz.service.MercadoPagoWebhookSignatureValidator;
import com.localuz.service.dto.MercadoPagoWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class MercadoPagoWebhookResource {
    private static final Logger LOG=LoggerFactory.getLogger(MercadoPagoWebhookResource.class);
    private final MercadoPagoProperties properties; private final MercadoPagoWebhookSignatureValidator validator; private final MercadoPagoWebhookProcessor processor;
    public MercadoPagoWebhookResource(MercadoPagoProperties properties,MercadoPagoWebhookSignatureValidator validator,MercadoPagoWebhookProcessor processor){this.properties=properties;this.validator=validator;this.processor=processor;}

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receive(@RequestHeader(value="x-signature",required=false)String signature,@RequestHeader(value="x-request-id",required=false)String requestId,@RequestParam(value="data.id",required=false)String dataId,@RequestBody(required=false)MercadoPagoWebhookPayload payload){
        LOG.info("Mercado Pago webhook received requestId={} dataId={}",requestId,dataId);
        if(!properties.isEnabled())return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        if(!validator.isValid(signature,requestId,dataId)){
            LOG.warn("Mercado Pago webhook rejected requestId={} dataId={} reason=invalid_signature",requestId,dataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if(payload==null||payload.getData()==null||payload.getData().getId()==null||!payload.getData().getId().equalsIgnoreCase(dataId)||payload.getType()==null){
            LOG.warn("Mercado Pago webhook rejected requestId={} dataId={} reason=invalid_payload",requestId,dataId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            MercadoPagoWebhookProcessor.Result result=processor.process(requestId,payload.getType(),dataId);
            LOG.info("Mercado Pago webhook handled requestId={} dataId={} processorResult={}",requestId,dataId,result);
            return ResponseEntity.ok().build();
        }
        catch(DataIntegrityViolationException duplicate){
            if (!isDeliveryDuplicate(duplicate)) {
                LOG.warn("Mercado Pago webhook persistence failure requestId={} dataId={}", requestId, dataId);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            LOG.info("Mercado Pago webhook handled requestId={} dataId={} processorResult=DUPLICATE_CONSTRAINT",requestId,dataId);
            return ResponseEntity.ok().build();
        }
        catch(MercadoPagoException temporaryProviderFailure){
            LOG.warn("Mercado Pago webhook provider failure requestId={} dataId={}",requestId,dataId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private boolean isDeliveryDuplicate(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                org.hibernate.exception.ConstraintViolationException violation =
                    (org.hibernate.exception.ConstraintViolationException) cause;
                String constraint = violation.getConstraintName();
                return ("ux_mp_webhook_delivery".equals(constraint)
                    || "mercadopago_webhook_event.ux_mp_webhook_delivery".equals(constraint))
                    && violation.getErrorCode() == 1062;
            }
        }
        return false;
    }
}
