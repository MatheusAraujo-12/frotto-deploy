package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.localuz.domain.enumeration.CommissionType;
import java.io.Serializable;
import java.math.BigDecimal;
import javax.persistence.*;
import javax.validation.constraints.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Car. */
@Entity
@Table(name = "car")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Car implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Size(max = 60)
    @Column(name = "name", length = 60)
    private String name;

    @Size(max = 60)
    @Column(name = "model", length = 60)
    private String model;

    @Size(max = 60)
    @Column(name = "color", length = 60)
    private String color;

    @Size(max = 60)
    @Column(name = "plate", length = 60)
    private String plate;

    @Column(name = "odometer")
    private Float odometer;

    @Column(name = "administration_fee")
    private Float commissionPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", length = 32)
    private CommissionType commissionType;

    @Column(name = "commission_fixed", precision = 10, scale = 2)
    private BigDecimal commissionFixed;

    @Column(name = "initial_value", precision = 21, scale = 2)
    private BigDecimal initialValue;

    @Column(name = "year")
    private Integer year;

    @Size(max = 60)
    @Column(name = "jhi_group", length = 60)
    private String group;

    @Column(name = "active")
    private Boolean active;

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private User user;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Car id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Car name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return this.model;
    }

    public Car model(String model) {
        this.setModel(model);
        return this;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return this.color;
    }

    public Car color(String color) {
        this.setColor(color);
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getPlate() {
        return this.plate;
    }

    public Car plate(String plate) {
        this.setPlate(plate);
        return this;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public Float getOdometer() {
        return this.odometer;
    }

    public Car odometer(Float odometer) {
        this.setOdometer(odometer);
        return this;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }

    public Float getCommissionPercent() {
        return commissionPercent;
    }

    public void setCommissionPercent(Float commissionPercent) {
        this.commissionPercent = commissionPercent;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public void setCommissionType(CommissionType commissionType) {
        this.commissionType = commissionType;
    }

    public BigDecimal getCommissionFixed() {
        return commissionFixed;
    }

    public void setCommissionFixed(BigDecimal commissionFixed) {
        this.commissionFixed = commissionFixed;
    }

    @JsonProperty("administrationFee")
    public Float getAdministrationFee() {
        return getCommissionPercent();
    }

    @JsonProperty("administrationFee")
    public void setAdministrationFee(Float administrationFee) {
        this.commissionPercent = administrationFee;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(BigDecimal initialValue) {
        this.initialValue = initialValue;
    }

    public Integer getYear() {
        return this.year;
    }

    public Car year(Integer year) {
        this.setYear(year);
        return this;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getGroup() {
        return this.group;
    }

    public Car group(String group) {
        this.setGroup(group);
        return this;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Car user(User user) {
        this.setUser(user);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Car)) {
            return false;
        }
        return id != null && id.equals(((Car) o).id);
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
    return "Car{"
        + "id="
        + getId()
        + ", name='"
        + getName()
        + "'"
        + ", model='"
        + getModel()
        + "'"
        + ", color='"
        + getColor()
        + "'"
        + ", plate='"
        + getPlate()
        + "'"
        + ", odometer="
        + getOdometer()
        + ", initialValue="
        + getInitialValue()
        + ", year="
        + getYear()
        + ", group='"
        + getGroup()
        + "'"
        + "}";
  }
}
