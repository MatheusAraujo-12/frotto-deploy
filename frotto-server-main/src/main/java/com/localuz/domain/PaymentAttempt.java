package com.localuz.domain;

import com.localuz.domain.enumeration.PaymentAttemptStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import javax.persistence.*;
import javax.validation.constraints.*;

/** Financial persistence only: no activation, settlement or entitlement side effects. */
@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
    @UniqueConstraint(name = "ux_payment_attempt_provider_id", columnNames = {"provider", "external_payment_id"}),
    @UniqueConstraint(name = "ux_payment_attempt_provider_attempt", columnNames = {"provider", "external_attempt_id"})
}, indexes = {
    @Index(name = "idx_payment_attempt_invoice", columnList = "billing_invoice_id")
})
public class PaymentAttempt implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_invoice_id", nullable = false, updatable = false)
    private BillingInvoice billingInvoice;

    @Size(max = 128)
    @Pattern(regexp = "\\S+")
    @Column(name = "external_payment_id", length = 128)
    private String externalPaymentId;

    @Size(max = 128)
    @Pattern(regexp = "\\S+")
    @Column(name = "external_attempt_id", length = 128)
    private String externalAttemptId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentAttemptStatus status;

    @Size(max = 128)
    @Column(name = "status_detail", length = 128)
    private String statusDetail;

    @NotNull
    @Digits(integer = 19, fraction = 2)
    @DecimalMin("0.00")
    @Column(name = "amount", nullable = false, precision = 21, scale = 2)
    private BigDecimal amount;

    @Digits(integer = 19, fraction = 2)
    @DecimalMin("0.00")
    @Column(name = "refunded_amount", precision = 21, scale = 2)
    private BigDecimal refundedAmount;

    @NotNull
    @Size(max = 3)
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "observed_at")
    private Instant observedAt;

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

    @AssertTrue(message = "A provider payment ID or stable provider attempt ID is required")
    public boolean isProviderIdentityPresent() {
        return externalPaymentId != null || externalAttemptId != null;
    }

    @AssertTrue(message = "Refunded amount cannot exceed the payment amount")
    public boolean isRefundedAmountValid() {
        return refundedAmount == null || amount == null || refundedAmount.compareTo(amount) <= 0;
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

    public BillingInvoice getBillingInvoice() {
        return billingInvoice;
    }

    public void setBillingInvoice(BillingInvoice billingInvoice) {
        this.billingInvoice = billingInvoice;
    }

    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    public void setExternalPaymentId(String externalPaymentId) {
        this.externalPaymentId = externalPaymentId;
    }

    public String getExternalAttemptId() {
        return externalAttemptId;
    }

    public void setExternalAttemptId(String externalAttemptId) {
        this.externalAttemptId = externalAttemptId;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentAttemptStatus status) {
        this.status = status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
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
        if (!(other instanceof PaymentAttempt)) return false;
        return id != null && id.equals(((PaymentAttempt) other).getId());
    }

    @Override
    public int hashCode() {
        return PaymentAttempt.class.hashCode();
    }
}
