package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A DriverCar. */
@Entity
@Table(name = "driver_car")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DriverCar implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "warranty", precision = 21, scale = 2)
    private BigDecimal warranty;

    @Column(name = "score")
    private Float score;

    @Column(name = "debt", precision = 21, scale = 2)
    private BigDecimal debt;

    @Column(name = "concluded")
    private Boolean concluded;

    @Column(name = "contract_number", length = 120)
    private String contractNumber;

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Car car;

    @ManyToOne
    @JsonIgnoreProperties(value = { "driverCars" }, allowSetters = true)
    private Driver driver;

    /**
     * A primary contract whose driver is temporarily on a reserve car: not concluded, no end date, not operational
     * while suspended. Only server operations change it (never the request body).
     */
    @Column(name = "suspended", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean suspended = false;

    /**
     * Set only on reserve contracts: the primary contract it temporarily replaces and that is restored when the
     * reserve is returned. Always the primary itself, never another reserve. Lazy so it is read after the locks.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_driver_car_id")
    @JsonIgnore
    private DriverCar primaryDriverCar;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Boolean getSuspended() {
        return suspended;
    }

    public void setSuspended(Boolean suspended) {
        this.suspended = suspended;
    }

    public DriverCar getPrimaryDriverCar() {
        return primaryDriverCar;
    }

    public void setPrimaryDriverCar(DriverCar primaryDriverCar) {
        this.primaryDriverCar = primaryDriverCar;
    }

    @JsonProperty(value = "primaryDriverCarId", access = JsonProperty.Access.READ_ONLY)
    public Long getPrimaryDriverCarId() {
        return primaryDriverCar == null ? null : primaryDriverCar.getId();
    }

    /** True for a reserve (temporary) contract; false for a normal/primary one. */
    @JsonProperty(value = "reserve", access = JsonProperty.Access.READ_ONLY)
    public boolean isReserve() {
        return primaryDriverCar != null;
    }

    /** CONCLUDED, SUSPENDED or ACTIVE (operational). */
    @JsonProperty(value = "status", access = JsonProperty.Access.READ_ONLY)
    public String getStatus() {
        if (Boolean.TRUE.equals(concluded)) {
            return "CONCLUDED";
        }
        return Boolean.TRUE.equals(suspended) ? "SUSPENDED" : "ACTIVE";
    }

    public Long getId() {
        return this.id;
    }

    public DriverCar id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public DriverCar startDate(LocalDate startDate) {
        this.setStartDate(startDate);
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public DriverCar endDate(LocalDate endDate) {
        this.setEndDate(endDate);
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getWarranty() {
        return this.warranty;
    }

    public DriverCar warranty(BigDecimal warranty) {
        this.setWarranty(warranty);
        return this;
    }

    public void setWarranty(BigDecimal warranty) {
        this.warranty = warranty;
    }

    public Float getScore() {
        return this.score;
    }

    public DriverCar score(Float score) {
        this.setScore(score);
        return this;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public BigDecimal getDebt() {
        return this.debt;
    }

    public DriverCar debt(BigDecimal debt) {
        this.setDebt(debt);
        return this;
    }

    public void setDebt(BigDecimal debt) {
        this.debt = debt;
    }

    public Boolean getConcluded() {
        return this.concluded;
    }

    public DriverCar concluded(Boolean concluded) {
        this.setConcluded(concluded);
        return this;
    }

    public void setConcluded(Boolean concluded) {
        this.concluded = concluded;
    }

    public String getContractNumber() {
        return this.contractNumber;
    }

    public DriverCar contractNumber(String contractNumber) {
        this.setContractNumber(contractNumber);
        return this;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    /** Car of the contract exposed read-only (its id only): clients can never re-link a contract through it. */
    @JsonProperty(value = "carId", access = JsonProperty.Access.READ_ONLY)
    public Long getCarId() {
        return car == null ? null : car.getId();
    }

    public Car getCar() {
        return this.car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public DriverCar car(Car car) {
        this.setCar(car);
        return this;
    }

    public Driver getDriver() {
        return this.driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public DriverCar driver(Driver driver) {
        this.setDriver(driver);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DriverCar)) {
            return false;
        }
        return id != null && id.equals(((DriverCar) o).id);
    }

    @Override
    public int hashCode() {
        // see
        // https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
  @Override
  public String toString() {
    return "DriverCar{"
        + "id="
        + getId()
        + ", startDate='"
        + getStartDate()
        + "'"
        + ", endDate='"
        + getEndDate()
        + "'"
        + ", warranty="
        + getWarranty()
        + ", score="
        + getScore()
        + ", debt="
        + getDebt()
        + ", concluded='"
        + getConcluded()
        + "'"
        + ", contractNumber='"
        + getContractNumber()
        + "'"
        + ", suspended='"
        + getSuspended()
        + "'"
        + ", primaryDriverCarId="
        + getPrimaryDriverCarId()
        + "}";
  }
}
