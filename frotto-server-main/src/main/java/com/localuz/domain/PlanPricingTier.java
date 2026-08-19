package com.localuz.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A PlanPricingTier.
 *
 * Progressive per-vehicle pricing component for plans whose price is not flat
 * (PLATINUM, FROTTA). PricingService sums the plan's base price plus, for each tier
 * that overlaps the requested vehicle count, (overlap quantity x pricePerVehicle).
 * toVehicleCount = null means the tier is unbounded (last tier of FROTTA).
 */
@Entity
@Table(name = "plan_pricing_tier")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class PlanPricingTier implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "tier_order", nullable = false)
    private Integer tierOrder;

    @Column(name = "from_vehicle_count", nullable = false)
    private Integer fromVehicleCount;

    @Column(name = "to_vehicle_count")
    private Integer toVehicleCount;

    @Column(name = "price_per_vehicle", precision = 21, scale = 2, nullable = false)
    private BigDecimal pricePerVehicle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Integer getTierOrder() {
        return tierOrder;
    }

    public void setTierOrder(Integer tierOrder) {
        this.tierOrder = tierOrder;
    }

    public Integer getFromVehicleCount() {
        return fromVehicleCount;
    }

    public void setFromVehicleCount(Integer fromVehicleCount) {
        this.fromVehicleCount = fromVehicleCount;
    }

    public Integer getToVehicleCount() {
        return toVehicleCount;
    }

    public void setToVehicleCount(Integer toVehicleCount) {
        this.toVehicleCount = toVehicleCount;
    }

    public BigDecimal getPricePerVehicle() {
        return pricePerVehicle;
    }

    public void setPricePerVehicle(BigDecimal pricePerVehicle) {
        this.pricePerVehicle = pricePerVehicle;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlanPricingTier)) {
            return false;
        }
        PlanPricingTier that = (PlanPricingTier) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
