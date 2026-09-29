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
    /** point_of_interaction.transaction_data.subscription_id - the only documented, deterministic
     * link from a bare Payment back to the Mercado Pago preapproval/subscription that generated it.
     * Present for both the initial and renewal charges of a subscription, unlike external_reference
     * (only confirmed for the initial checkout payment). */
    private final String subscriptionId;
    /** point_of_interaction.transaction_data.subscription_sequence.number - 1-based ordinal of this
     * charge within the subscription's recurrence (1 = initial). Used to derive the competency period
     * deterministically without depending on an unconfirmed date field format. */
    private final Integer subscriptionSequenceNumber;

    public MercadoPagoPayment(String id, String status, String statusDetail, BigDecimal transactionAmount, String currencyId, Instant dateCreated, Instant dateApproved, Instant dateLastUpdated, String externalReference, BigDecimal refundedAmount) {
        this(id, status, statusDetail, transactionAmount, currencyId, dateCreated, dateApproved, dateLastUpdated, externalReference, refundedAmount, null, null);
    }
    public MercadoPagoPayment(String id, String status, String statusDetail, BigDecimal transactionAmount, String currencyId, Instant dateCreated, Instant dateApproved, Instant dateLastUpdated, String externalReference, BigDecimal refundedAmount, String subscriptionId, Integer subscriptionSequenceNumber) {
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
        this.subscriptionId = subscriptionId;
        this.subscriptionSequenceNumber = subscriptionSequenceNumber;
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
    public String getSubscriptionId() { return subscriptionId; }
    public Integer getSubscriptionSequenceNumber() { return subscriptionSequenceNumber; }
}
