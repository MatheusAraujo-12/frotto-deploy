package com.localuz.service;

import com.localuz.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Validates the exact HMAC-SHA256 manifest documented by Mercado Pago. */
@Component
public class MercadoPagoWebhookSignatureValidator {
    private final MercadoPagoProperties properties;
    public MercadoPagoWebhookSignatureValidator(MercadoPagoProperties properties) { this.properties=properties; }

    public boolean isValid(String signature, String requestId, String dataId) {
        if (!properties.isEnabled() || !properties.hasWebhookSecret() || blank(signature) || blank(requestId) || blank(dataId)) return false;
        String timestamp=null; String supplied=null;
        for (String part : signature.split(",")) {
            String[] pair=part.trim().split("=",2);
            if (pair.length==2 && "ts".equals(pair[0])) timestamp=pair[1];
            if (pair.length==2 && "v1".equals(pair[0])) supplied=pair[1];
        }
        if (blank(timestamp) || blank(supplied)) return false;
        try {
            String manifest="id:"+dataId.toLowerCase(Locale.ROOT)+";request-id:"+requestId+";ts:"+timestamp+";";
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] actual=java.util.HexFormat.of().parseHex(supplied);
            return MessageDigest.isEqual(expected,actual);
        } catch (Exception ignored) { return false; }
    }
    private boolean blank(String value){return value==null||value.isBlank();}
}
