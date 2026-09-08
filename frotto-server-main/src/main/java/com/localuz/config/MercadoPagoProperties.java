package com.localuz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Backend-only Mercado Pago configuration. Credentials must never be serialized or logged. */
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoProperties {
    private boolean enabled = false;
    private String accessToken;
    private String webhookSecret;
    private String backUrl;
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 7000;
    /** Sandbox-only escape hatch: must never be honored unless testMode is also true. */
    private boolean testMode = false;
    private String testPayerEmail;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getBackUrl() { return backUrl; }
    public void setBackUrl(String backUrl) { this.backUrl = backUrl; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int value) { this.connectTimeoutMillis = value; }
    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int value) { this.readTimeoutMillis = value; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }
    public String getTestPayerEmail() { return testPayerEmail; }
    public void setTestPayerEmail(String testPayerEmail) { this.testPayerEmail = testPayerEmail; }
    public boolean hasAccessToken() { return accessToken != null && !accessToken.isBlank(); }
    public boolean hasWebhookSecret() { return webhookSecret != null && !webhookSecret.isBlank(); }
    public boolean hasTestPayerEmail() { return testPayerEmail != null && !testPayerEmail.isBlank(); }
}
