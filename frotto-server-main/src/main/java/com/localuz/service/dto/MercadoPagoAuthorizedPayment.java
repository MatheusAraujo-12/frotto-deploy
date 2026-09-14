package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Authoritative recurring charge. Its payment status is not proof of settlement. */
public class MercadoPagoAuthorizedPayment {
    private final String id;
    private final String status;
    private final String preapprovalId;
    private final String paymentStatus;
    private final String paymentId;
    private final BigDecimal transactionAmount;
    private final String currencyId;
    private final Instant dateCreated;
    private final Instant lastModified;
    private final Instant debitDate;
    private final String externalReference;
    public MercadoPagoAuthorizedPayment(String id, String status, String preapprovalId) { this(id, status, preapprovalId, null); }
    public MercadoPagoAuthorizedPayment(String id, String status, String preapprovalId, String paymentStatus) {
        this(id, status, preapprovalId, paymentStatus, null, null, null, null, null, null, null);
    }
    public MercadoPagoAuthorizedPayment(String id, String status, String preapprovalId, String paymentStatus, String paymentId, BigDecimal transactionAmount, String currencyId, Instant dateCreated, Instant lastModified, Instant debitDate, String externalReference) {
        this.id = id;
        this.status = status;
        this.preapprovalId = preapprovalId;
        this.paymentStatus = paymentStatus;
        this.paymentId = paymentId;
        this.transactionAmount = transactionAmount;
        this.currencyId = currencyId;
        this.dateCreated = dateCreated;
        this.lastModified = lastModified;
        this.debitDate = debitDate;
        this.externalReference = externalReference;
    }
    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getPreapprovalId() { return preapprovalId; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getPaymentId() { return paymentId; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public String getCurrencyId() { return currencyId; }
    public Instant getDateCreated() { return dateCreated; }
    public Instant getLastModified() { return lastModified; }
    public Instant getDebitDate() { return debitDate; }
    public String getExternalReference() { return externalReference; }
}
