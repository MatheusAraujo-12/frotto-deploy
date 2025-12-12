package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.localuz.service.dto.BodyDamageDTO;
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
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A CarBodyDamage. */
@Entity
@Table(name = "car_body_damage")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CarBodyDamage implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "date")
    private LocalDate date;

    @Size(max = 60)
    @Column(name = "responsible", length = 60)
    private String responsible;

    @Size(max = 60)
    @Column(name = "part", length = 60)
    private String part;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_path_2")
    private String imagePath2;

    @Column(name = "cost", precision = 21, scale = 2)
    private BigDecimal cost;

    @Column(name = "resolved")
    private Boolean resolved;

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Car car;

    @ManyToMany(mappedBy = "carBodyDamages", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Set<Inspection> inspections = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public CarBodyDamage() {}

    public Long getId() {
        return this.id;
    }

    public CarBodyDamage id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public CarBodyDamage date(LocalDate date) {
        this.setDate(date);
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getResponsible() {
        return this.responsible;
    }

    public CarBodyDamage responsible(String responsible) {
        this.setResponsible(responsible);
        return this;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public String getPart() {
        return this.part;
    }

    public CarBodyDamage part(String part) {
        this.setPart(part);
        return this;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getImagePath() {
        return this.imagePath;
    }

    public CarBodyDamage imagePath(String imagePath) {
        this.setImagePath(imagePath);
        return this;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImagePath2() {
        return imagePath2;
    }

    public void setImagePath2(String imagePath2) {
        this.imagePath2 = imagePath2;
    }

    public BigDecimal getCost() {
        return this.cost;
    }

    public CarBodyDamage cost(BigDecimal cost) {
        this.setCost(cost);
        return this;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Boolean getResolved() {
        return this.resolved;
    }

    public CarBodyDamage resolved(Boolean resolved) {
        this.setResolved(resolved);
        return this;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public Car getCar() {
        return this.car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public CarBodyDamage car(Car car) {
        this.setCar(car);
        return this;
    }

    public Set<Inspection> getInspections() {
        return this.inspections;
    }

    public void setInspections(Set<Inspection> inspections) {
        if (this.inspections != null) {
            this.inspections.forEach(i -> i.removeCarBodyDamage(this));
        }
        if (inspections != null) {
            inspections.forEach(i -> i.addCarBodyDamage(this));
        }
        this.inspections = inspections;
    }

    public CarBodyDamage inspections(Set<Inspection> inspections) {
        this.setInspections(inspections);
        return this;
    }

    public CarBodyDamage addInspection(Inspection inspection) {
        this.inspections.add(inspection);
        inspection.getCarBodyDamages().add(this);
        return this;
    }

    public CarBodyDamage removeInspection(Inspection inspection) {
        this.inspections.remove(inspection);
        inspection.getCarBodyDamages().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CarBodyDamage)) {
            return false;
        }
        return id != null && id.equals(((CarBodyDamage) o).id);
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
    return "CarBodyDamage{"
        + "id="
        + getId()
        + ", date='"
        + getDate()
        + "'"
        + ", responsible='"
        + getResponsible()
        + "'"
        + ", part='"
        + getPart()
        + "'"
        + ", imagePath='"
        + getImagePath()
        + "'"
        + ", cost="
        + getCost()
        + ", resolved='"
        + getResolved()
        + "'"
        + "}";
  }
}
