package com.localuz.web.rest;

import com.localuz.config.MercadoPagoProperties;
import com.localuz.service.MercadoPagoException;
import com.localuz.service.MercadoPagoWebhookProcessor;
import com.localuz.service.MercadoPagoWebhookSignatureValidator;
import com.localuz.service.dto.MercadoPagoWebhookPayload;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class MercadoPagoWebhookResource {
    private final MercadoPagoProperties properties; private final MercadoPagoWebhookSignatureValidator validator; private final MercadoPagoWebhookProcessor processor;
    public MercadoPagoWebhookResource(MercadoPagoProperties properties,MercadoPagoWebhookSignatureValidator validator,MercadoPagoWebhookProcessor processor){this.properties=properties;this.validator=validator;this.processor=processor;}

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receive(@RequestHeader(value="x-signature",required=false)String signature,@RequestHeader(value="x-request-id",required=false)String requestId,@RequestParam(value="data.id",required=false)String dataId,@RequestBody(required=false)MercadoPagoWebhookPayload payload){
        if(!properties.isEnabled())return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        if(!validator.isValid(signature,requestId,dataId))return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if(payload==null||payload.getData()==null||payload.getData().getId()==null||!payload.getData().getId().equalsIgnoreCase(dataId)||payload.getType()==null)return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        try {processor.process(requestId,payload.getType(),dataId);return ResponseEntity.ok().build();}
        catch(DataIntegrityViolationException duplicate){return ResponseEntity.ok().build();}
        catch(MercadoPagoException temporaryProviderFailure){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();}
    }
}
