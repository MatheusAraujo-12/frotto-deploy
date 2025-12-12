package com.localuz.domain;

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

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Car car;

    @ManyToOne
    @JsonIgnoreProperties(value = { "driverCars" }, allowSetters = true)
    private Driver driver;

    // jhipster-needle-entity-add-field - JHipster will add fields here

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
        + "}";
  }
}
