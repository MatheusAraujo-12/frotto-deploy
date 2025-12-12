package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Inspection. */
@Entity
@Table(name = "inspection")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Inspection implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "date")
    private LocalDate date;

    @Size(max = 60)
    @Column(name = "driver_name", length = 60)
    private String driverName;

    @Column(name = "odometer")
    private Float odometer;

    @Size(max = 60)
    @Column(name = "internal_cleaning", length = 60)
    private String internalCleaning;

    @Size(max = 60)
    @Column(name = "external_cleaning", length = 60)
    private String externalCleaning;

    @Size(max = 100)
    @Column(name = "comment", length = 100)
    private String comment;

    @Column(name = "score")
    private Float score;

    @Column(name = "cost", precision = 21, scale = 2)
    private BigDecimal cost;

    @OneToOne(cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @JoinColumn(unique = true)
    private Tire leftFront;

    @OneToOne(cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @JoinColumn(unique = true)
    private Tire rightFront;

    @OneToOne(cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @JoinColumn(unique = true)
    private Tire leftBack;

    @OneToOne(cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @JoinColumn(unique = true)
    private Tire rightBack;

    @OneToOne(cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @JoinColumn(unique = true)
    private Tire spare;

    @OneToMany(mappedBy = "inspection", fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "inspection" }, allowSetters = true)
    private Set<Expense> expenses = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER, cascade = { CascadeType.MERGE })
    @JoinTable(
        name = "rel_inspection__car_body_damage",
        joinColumns = @JoinColumn(name = "inspection_id"),
        inverseJoinColumns = @JoinColumn(name = "car_body_damage_id")
    )
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonIgnoreProperties(value = { "car", "inspections" }, allowSetters = true)
    private Set<CarBodyDamage> carBodyDamages = new HashSet<>();

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Car car;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Inspection id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public Inspection date(LocalDate date) {
        this.setDate(date);
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDriverName() {
        return this.driverName;
    }

    public Inspection driverName(String driverName) {
        this.setDriverName(driverName);
        return this;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public Float getOdometer() {
        return this.odometer;
    }

    public Inspection odometer(Float odometer) {
        this.setOdometer(odometer);
        return this;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }

    public String getInternalCleaning() {
        return this.internalCleaning;
    }

    public Inspection internalCleaning(String internalCleaning) {
        this.setInternalCleaning(internalCleaning);
        return this;
    }

    public void setInternalCleaning(String internalCleaning) {
        this.internalCleaning = internalCleaning;
    }

    public String getExternalCleaning() {
        return this.externalCleaning;
    }

    public Inspection externalCleaning(String externalCleaning) {
        this.setExternalCleaning(externalCleaning);
        return this;
    }

    public void setExternalCleaning(String externalCleaning) {
        this.externalCleaning = externalCleaning;
    }

    public String getComment() {
        return this.comment;
    }

    public Inspection comment(String comment) {
        this.setComment(comment);
        return this;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Float getScore() {
        return this.score;
    }

    public Inspection score(Float score) {
        this.setScore(score);
        return this;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public BigDecimal getCost() {
        return this.cost;
    }

    public Inspection cost(BigDecimal cost) {
        this.setCost(cost);
        return this;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Tire getLeftFront() {
        return this.leftFront;
    }

    public void setLeftFront(Tire tire) {
        this.leftFront = tire;
    }

    public Inspection leftFront(Tire tire) {
        this.setLeftFront(tire);
        return this;
    }

    public Tire getRightFront() {
        return this.rightFront;
    }

    public void setRightFront(Tire tire) {
        this.rightFront = tire;
    }

    public Inspection rightFront(Tire tire) {
        this.setRightFront(tire);
        return this;
    }

    public Tire getLeftBack() {
        return this.leftBack;
    }

    public void setLeftBack(Tire tire) {
        this.leftBack = tire;
    }

    public Inspection leftBack(Tire tire) {
        this.setLeftBack(tire);
        return this;
    }

    public Tire getRightBack() {
        return this.rightBack;
    }

    public void setRightBack(Tire tire) {
        this.rightBack = tire;
    }

    public Inspection rightBack(Tire tire) {
        this.setRightBack(tire);
        return this;
    }

    public Tire getSpare() {
        return this.spare;
    }

    public void setSpare(Tire tire) {
        this.spare = tire;
    }

    public Inspection spare(Tire tire) {
        this.setSpare(tire);
        return this;
    }

    public Set<Expense> getExpenses() {
        return this.expenses;
    }

    public void setExpenses(Set<Expense> expenses) {
        if (this.expenses != null) {
            this.expenses.forEach(i -> i.setInspection(null));
        }
        if (expenses != null) {
            expenses.forEach(i -> i.setInspection(this));
        }
        this.expenses = expenses;
    }

    public Inspection expenses(Set<Expense> expenses) {
        this.setExpenses(expenses);
        return this;
    }

    public Inspection addExpense(Expense expense) {
        this.expenses.add(expense);
        expense.setInspection(this);
        return this;
    }

    public Inspection removeExpense(Expense expense) {
        this.expenses.remove(expense);
        expense.setInspection(null);
        return this;
    }

    public Set<CarBodyDamage> getCarBodyDamages() {
        return this.carBodyDamages;
    }

    public void setCarBodyDamages(Set<CarBodyDamage> carBodyDamages) {
        this.carBodyDamages = carBodyDamages;
    }

    public Inspection carBodyDamages(Set<CarBodyDamage> carBodyDamages) {
        this.setCarBodyDamages(carBodyDamages);
        return this;
    }

    public Inspection addCarBodyDamage(CarBodyDamage carBodyDamage) {
        this.carBodyDamages.add(carBodyDamage);
        carBodyDamage.getInspections().add(this);
        return this;
    }

    public Inspection removeCarBodyDamage(CarBodyDamage carBodyDamage) {
        this.carBodyDamages.remove(carBodyDamage);
        carBodyDamage.getInspections().remove(this);
        return this;
    }

    public Car getCar() {
        return this.car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public Inspection car(Car car) {
        this.setCar(car);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Inspection)) {
            return false;
        }
        return id != null && id.equals(((Inspection) o).id);
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
    return "Inspection{"
        + "id="
        + getId()
        + ", date='"
        + getDate()
        + "'"
        + ", driverName='"
        + getDriverName()
        + "'"
        + ", odometer="
        + getOdometer()
        + ", internalCleaning='"
        + getInternalCleaning()
        + "'"
        + ", externalCleaning='"
        + getExternalCleaning()
        + "'"
        + ", comment='"
        + getComment()
        + "'"
        + ", score="
        + getScore()
        + ", cost="
        + getCost()
        + "}";
  }
}
