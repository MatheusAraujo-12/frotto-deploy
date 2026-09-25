package com.localuz.service.dto;

/** 5G.12.1: the subset of a created Checkout Pro preference Frotto needs - its id, checkout URL and echoed reference. */
public class MercadoPagoPaymentPreference {
    private final String id;
    private final String checkoutUrl;
    private final String externalReference;

    public MercadoPagoPaymentPreference(String id, String checkoutUrl, String externalReference) {
        this.id = id;
        this.checkoutUrl = checkoutUrl;
        this.externalReference = externalReference;
    }

    public String getId() { return id; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public String getExternalReference() { return externalReference; }
}
