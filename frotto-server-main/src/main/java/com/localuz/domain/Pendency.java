package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localuz.domain.enumeration.PendencyStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Pendency. */
@Entity
@Table(name = "pendency")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Pendency implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Size(max = 60)
    @Column(name = "name", length = 60)
    private String name;

    @Column(name = "cost", precision = 21, scale = 2)
    private BigDecimal cost;

    @Column(name = "date")
    private LocalDate date;

    @Size(max = 255)
    @Column(name = "note", length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private PendencyStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_amount", precision = 21, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "remaining_amount", precision = 21, scale = 2)
    private BigDecimal remainingAmount;

    @Size(max = 60)
    @Column(name = "payment_method", length = 60)
    private String paymentMethod;

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private DriverCar driverCar;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public DriverCar getDriverCar() {
        return driverCar;
    }

    public void setDriverCar(DriverCar driverCar) {
        this.driverCar = driverCar;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public PendencyStatus getStatus() {
        return status;
    }

    public void setStatus(PendencyStatus status) {
        this.status = status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pendency)) {
            return false;
        }
        return id != null && id.equals(((Pendency) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, cost, driverCar);
    }
}
