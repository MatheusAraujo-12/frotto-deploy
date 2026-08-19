package com.localuz.domain;

import com.localuz.domain.enumeration.PlanCode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Plan.
 *
 * Represents the current, mutable configuration (price/limits) for a billing plan.
 * `code` is the immutable business identity; price/limits may change over time.
 * Historical correctness for already-contracted subscriptions comes from the price
 * snapshot stored on Subscription, not from this table.
 */
@Entity
@Table(name = "plan")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Plan implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private PlanCode code;

    @Size(max = 60)
    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Column(name = "min_vehicles", nullable = false)
    private Integer minVehicles;

    @Column(name = "max_vehicles")
    private Integer maxVehicles;

    @Column(name = "monthly_base_price", precision = 21, scale = 2, nullable = false)
    private BigDecimal monthlyBasePrice;

    @Column(name = "active", nullable = false)
    private Boolean active;

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
        if (active == null) {
            active = Boolean.TRUE;
        }
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

    public PlanCode getCode() {
        return code;
    }

    public void setCode(PlanCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMinVehicles() {
        return minVehicles;
    }

    public void setMinVehicles(Integer minVehicles) {
        this.minVehicles = minVehicles;
    }

    public Integer getMaxVehicles() {
        return maxVehicles;
    }

    public void setMaxVehicles(Integer maxVehicles) {
        this.maxVehicles = maxVehicles;
    }

    public BigDecimal getMonthlyBasePrice() {
        return monthlyBasePrice;
    }

    public void setMonthlyBasePrice(BigDecimal monthlyBasePrice) {
        this.monthlyBasePrice = monthlyBasePrice;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
        if (!(o instanceof Plan)) {
            return false;
        }
        Plan that = (Plan) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
