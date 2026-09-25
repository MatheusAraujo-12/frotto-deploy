package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 5G.12.1: a one-off Checkout Pro charge (POST /checkout/preferences) for the prorated part of an
 * upgrade. Always a single item of quantity 1 whose unit_price is the server-computed charge - the
 * amount never comes from the client.
 */
public class MercadoPagoPaymentPreferenceRequest {
    private final String externalReference;
    private final String title;
    private final BigDecimal amount;
    private final String currencyId;
    private final String backUrl;
    private final Instant expiresAt;

    public MercadoPagoPaymentPreferenceRequest(String externalReference, String title, BigDecimal amount, String currencyId,
        String backUrl, Instant expiresAt) {
        this.externalReference = externalReference;
        this.title = title;
        this.amount = amount;
        this.currencyId = currencyId;
        this.backUrl = backUrl;
        this.expiresAt = expiresAt;
    }

    public String getExternalReference() { return externalReference; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyId() { return currencyId; }
    public String getBackUrl() { return backUrl; }
    public Instant getExpiresAt() { return expiresAt; }
}
