package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Allowlisted authoritative payment snapshot; no payer, card or raw payload. */
public class MercadoPagoPayment {
    private final String id;
    private final String status;
    private final String statusDetail;
    private final BigDecimal transactionAmount;
    private final String currencyId;
    private final Instant dateCreated;
    private final Instant dateApproved;
    private final Instant dateLastUpdated;
    private final String externalReference;
    private final BigDecimal refundedAmount;

    public MercadoPagoPayment(String id, String status, String statusDetail, BigDecimal transactionAmount, String currencyId, Instant dateCreated, Instant dateApproved, Instant dateLastUpdated, String externalReference, BigDecimal refundedAmount) {
        this.id = id;
        this.status = status;
        this.statusDetail = statusDetail;
        this.transactionAmount = transactionAmount;
        this.currencyId = currencyId;
        this.dateCreated = dateCreated;
        this.dateApproved = dateApproved;
        this.dateLastUpdated = dateLastUpdated;
        this.externalReference = externalReference;
        this.refundedAmount = refundedAmount;
    }
    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getStatusDetail() { return statusDetail; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public String getCurrencyId() { return currencyId; }
    public Instant getDateCreated() { return dateCreated; }
    public Instant getDateApproved() { return dateApproved; }
    public Instant getDateLastUpdated() { return dateLastUpdated; }
    public String getExternalReference() { return externalReference; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
}
