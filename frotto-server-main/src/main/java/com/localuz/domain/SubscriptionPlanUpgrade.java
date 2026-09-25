package com.localuz.domain;

import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import javax.persistence.*;
import javax.validation.constraints.*;

/**
 * 5G.12.1: one prorated upgrade attempt for a PAYMENT_PROVIDER subscription. The quote (prices,
 * charge, cycle) is frozen here when the attempt starts so a later webhook/reconciliation never
 * re-prices anything. Deliberately separate from BillingInvoice/PaymentAttempt: those model the
 * recurring competencies that SubscriptionFinancialCoverageService turns into entitlement, and a
 * one-off charge stored there would corrupt that evaluation (see the Liquibase changelog).
 */
@Entity
@Table(name = "subscription_plan_upgrade", uniqueConstraints = {
    @UniqueConstraint(name = "ux_plan_upgrade_external_reference", columnNames = {"external_reference"}),
    @UniqueConstraint(name = "ux_plan_upgrade_external_payment", columnNames = {"external_payment_id"})
}, indexes = {
    @Index(name = "idx_plan_upgrade_subscription_status", columnList = "subscription_id,status"),
    @Index(name = "idx_plan_upgrade_status", columnList = "status")
})
public class SubscriptionPlanUpgrade implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private Subscription subscription;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_plan_id", nullable = false, updatable = false)
    private Plan fromPlan;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "target_plan_id", nullable = false, updatable = false)
    private Plan targetPlan;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "from_price", nullable = false, precision = 21, scale = 2, updatable = false)
    private BigDecimal fromPrice;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "target_price", nullable = false, precision = 21, scale = 2, updatable = false)
    private BigDecimal targetPrice;

    @NotNull
    @Column(name = "target_vehicle_count", nullable = false, updatable = false)
    private Integer targetVehicleCount;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "charge_amount", nullable = false, precision = 21, scale = 2, updatable = false)
    private BigDecimal chargeAmount;

    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @NotNull
    @Column(name = "cycle_start", nullable = false, updatable = false)
    private Instant cycleStart;

    @NotNull
    @Column(name = "cycle_end", nullable = false, updatable = false)
    private Instant cycleEnd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SubscriptionPlanUpgradeStatus status;

    @NotNull
    @Size(max = 64)
    @Column(name = "external_reference", nullable = false, length = 64, updatable = false)
    private String externalReference;

    @Size(max = 128)
    @Column(name = "external_preference_id", length = 128)
    private String externalPreferenceId;

    @Size(max = 1024)
    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @NotNull
    @Column(name = "checkout_expires_at", nullable = false)
    private Instant checkoutExpiresAt;

    @Size(max = 128)
    @Column(name = "external_payment_id", length = 128)
    private String externalPaymentId;

    @Size(max = 32)
    @Column(name = "last_payment_status", length = 32)
    private String lastPaymentStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @NotNull
    @Column(name = "apply_attempts", nullable = false)
    private Integer applyAttempts = 0;

    @Size(max = 64)
    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "applied_at")
    private Instant appliedAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) { this.subscription = subscription; }
    public Plan getFromPlan() { return fromPlan; }
    public void setFromPlan(Plan fromPlan) { this.fromPlan = fromPlan; }
    public Plan getTargetPlan() { return targetPlan; }
    public void setTargetPlan(Plan targetPlan) { this.targetPlan = targetPlan; }
    public BigDecimal getFromPrice() { return fromPrice; }
    public void setFromPrice(BigDecimal fromPrice) { this.fromPrice = fromPrice; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }
    public Integer getTargetVehicleCount() { return targetVehicleCount; }
    public void setTargetVehicleCount(Integer targetVehicleCount) { this.targetVehicleCount = targetVehicleCount; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getCycleStart() { return cycleStart; }
    public void setCycleStart(Instant cycleStart) { this.cycleStart = cycleStart; }
    public Instant getCycleEnd() { return cycleEnd; }
    public void setCycleEnd(Instant cycleEnd) { this.cycleEnd = cycleEnd; }
    public SubscriptionPlanUpgradeStatus getStatus() { return status; }
    public void setStatus(SubscriptionPlanUpgradeStatus status) { this.status = status; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
    public String getExternalPreferenceId() { return externalPreferenceId; }
    public void setExternalPreferenceId(String externalPreferenceId) { this.externalPreferenceId = externalPreferenceId; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public Instant getCheckoutExpiresAt() { return checkoutExpiresAt; }
    public void setCheckoutExpiresAt(Instant checkoutExpiresAt) { this.checkoutExpiresAt = checkoutExpiresAt; }
    public String getExternalPaymentId() { return externalPaymentId; }
    public void setExternalPaymentId(String externalPaymentId) { this.externalPaymentId = externalPaymentId; }
    public String getLastPaymentStatus() { return lastPaymentStatus; }
    public void setLastPaymentStatus(String lastPaymentStatus) { this.lastPaymentStatus = lastPaymentStatus; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Integer getApplyAttempts() { return applyAttempts; }
    public void setApplyAttempts(Integer applyAttempts) { this.applyAttempts = applyAttempts; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SubscriptionPlanUpgrade)) return false;
        return id != null && id.equals(((SubscriptionPlanUpgrade) other).getId());
    }

    @Override
    public int hashCode() {
        return SubscriptionPlanUpgrade.class.hashCode();
    }
}
