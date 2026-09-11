package com.localuz.domain;

import com.localuz.domain.enumeration.BillingInvoiceStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import javax.persistence.*;
import javax.validation.constraints.*;

/** Financial persistence only: no activation, settlement or entitlement side effects. */
@Entity
@Table(name = "billing_invoice", uniqueConstraints = {
    @UniqueConstraint(name = "ux_billing_invoice_provider_id", columnNames = {"provider", "external_authorized_payment_id"}),
    @UniqueConstraint(name = "ux_billing_invoice_period", columnNames = {"subscription_id", "period_start", "period_end"})
}, indexes = {
    @Index(name = "idx_billing_invoice_status_due", columnList = "status,due_at"),
    @Index(name = "idx_billing_invoice_status_grace", columnList = "status,grace_period_end"),
    @Index(name = "idx_billing_invoice_reconciled", columnList = "last_reconciled_at")
})
public class BillingInvoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private Subscription subscription;

    @Size(max = 128)
    @Pattern(regexp = "\\S+")
    @Column(name = "external_authorized_payment_id", length = 128)
    private String externalAuthorizedPaymentId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BillingInvoiceStatus status;

    @NotNull
    @Digits(integer = 19, fraction = 2)
    @DecimalMin("0.00")
    @Column(name = "amount", nullable = false, precision = 21, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Size(max = 3)
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @NotNull
    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @NotNull
    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @NotNull
    @Column(name = "grace_period_end", nullable = false, updatable = false)
    private Instant gracePeriodEnd;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @NotNull
    @Size(max = 64)
    @NotBlank
    @Column(name = "provider", nullable = false, length = 64, updatable = false)
    private String provider;

    @Size(max = 64)
    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "provider_created_at")
    private Instant providerCreatedAt;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    @AssertTrue(message = "Service period must be positive and grace must end 72 hours after dueAt")
    public boolean isPeriodValid() {
        return periodStart != null && periodEnd != null && dueAt != null && gracePeriodEnd != null
            && periodStart.isBefore(periodEnd) && dueAt.equals(periodStart)
            && gracePeriodEnd.equals(dueAt.plus(Duration.ofHours(72)));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public String getExternalAuthorizedPaymentId() {
        return externalAuthorizedPaymentId;
    }

    public void setExternalAuthorizedPaymentId(String externalAuthorizedPaymentId) {
        this.externalAuthorizedPaymentId = externalAuthorizedPaymentId;
    }

    public BillingInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(BillingInvoiceStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getGracePeriodEnd() {
        return gracePeriodEnd;
    }

    public void setGracePeriodEnd(Instant gracePeriodEnd) {
        this.gracePeriodEnd = gracePeriodEnd;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(Instant refundedAt) {
        this.refundedAt = refundedAt;
    }

    public Instant getLastReconciledAt() {
        return lastReconciledAt;
    }

    public void setLastReconciledAt(Instant lastReconciledAt) {
        this.lastReconciledAt = lastReconciledAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public Instant getProviderCreatedAt() {
        return providerCreatedAt;
    }

    public void setProviderCreatedAt(Instant providerCreatedAt) {
        this.providerCreatedAt = providerCreatedAt;
    }

    public Instant getProviderUpdatedAt() {
        return providerUpdatedAt;
    }

    public void setProviderUpdatedAt(Instant providerUpdatedAt) {
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BillingInvoice)) return false;
        return id != null && id.equals(((BillingInvoice) other).getId());
    }

    @Override
    public int hashCode() {
        return BillingInvoice.class.hashCode();
    }
}
