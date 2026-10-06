package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localuz.domain.enumeration.PendencyOriginType;
import com.localuz.domain.enumeration.PendencyStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
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

    /** Historical origin: the contract (driver_car) during which the debt was recorded. Never moved. */
    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private DriverCar driverCar;

    /**
     * Who owes the debt: the driver of {@link #driverCar} when the pendency was recorded, frozen from then on
     * (transfers, concluded contracts or the car's current driver never change it). Never taken from clients.
     */
    @ManyToOne
    @JoinColumn(name = "debtor_driver_id")
    @JsonIgnore
    private Driver debtor;

    /*
     * Structural origin, set only by the server when it knows it for sure (never by clients, never backfilled):
     * originType says WHAT the debt is, originMaintenanceId / originDocumentId say WHERE it came from.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", length = 40)
    private PendencyOriginType originType;

    /** The car maintenance a shared-maintenance charge comes from (the maintenance keeps its full cost). */
    @Column(name = "origin_maintenance_id")
    private Long originMaintenanceId;

    /** The legacy Documentos document (MULTA, MANUTENCAO_COMPARTILHADA...) whose finalization created this debt. */
    @Column(name = "origin_document_id")
    private Long originDocumentId;

    /** Key of the creation operation of the screen: a retry with the same key returns this pendency. */
    @Column(name = "idempotency_key", length = 80)
    @JsonIgnore
    private String idempotencyKey;

    /* Infraction data of a FINE: the pendency is the fine, the MULTA document is only its notification. */
    @Column(name = "fine_ait", length = 40)
    private String fineAit;

    @Column(name = "fine_agency", length = 120)
    private String fineAgency;

    @Column(name = "fine_location", length = 255)
    private String fineLocation;

    @Column(name = "fine_classification", length = 255)
    private String fineClassification;

    @Column(name = "fine_infraction_time")
    private LocalTime fineInfractionTime;

    @Column(name = "fine_due_date")
    private LocalDate fineDueDate;

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

    public Driver getDebtor() {
        return debtor;
    }

    public void setDebtor(Driver debtor) {
        this.debtor = debtor;
    }

    /** Debtor exposed read-only: clients can never re-assign a debt through it. */
    @JsonProperty(value = "debtorDriverId", access = JsonProperty.Access.READ_ONLY)
    public Long getDebtorDriverId() {
        return debtor == null ? null : debtor.getId();
    }

    /** Contract (driver_car) of the pendency, exposed read-only: clients can never re-link a pendency through it. */
    @JsonProperty(value = "driverCarId", access = JsonProperty.Access.READ_ONLY)
    public Long getDriverCarId() {
        return driverCar == null ? null : driverCar.getId();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public PendencyOriginType getOriginType() {
        return originType;
    }

    public void setOriginType(PendencyOriginType originType) {
        this.originType = originType;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getOriginMaintenanceId() {
        return originMaintenanceId;
    }

    public void setOriginMaintenanceId(Long originMaintenanceId) {
        this.originMaintenanceId = originMaintenanceId;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getOriginDocumentId() {
        return originDocumentId;
    }

    public void setOriginDocumentId(Long originDocumentId) {
        this.originDocumentId = originDocumentId;
    }

    @JsonIgnore
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @JsonIgnore
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getFineAit() {
        return fineAit;
    }

    public void setFineAit(String fineAit) {
        this.fineAit = fineAit;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getFineAgency() {
        return fineAgency;
    }

    public void setFineAgency(String fineAgency) {
        this.fineAgency = fineAgency;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getFineLocation() {
        return fineLocation;
    }

    public void setFineLocation(String fineLocation) {
        this.fineLocation = fineLocation;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getFineClassification() {
        return fineClassification;
    }

    public void setFineClassification(String fineClassification) {
        this.fineClassification = fineClassification;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public LocalTime getFineInfractionTime() {
        return fineInfractionTime;
    }

    public void setFineInfractionTime(LocalTime fineInfractionTime) {
        this.fineInfractionTime = fineInfractionTime;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public LocalDate getFineDueDate() {
        return fineDueDate;
    }

    public void setFineDueDate(LocalDate fineDueDate) {
        this.fineDueDate = fineDueDate;
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
