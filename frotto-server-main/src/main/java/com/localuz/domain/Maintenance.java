package com.localuz.domain;

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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.Size;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Maintenance. */
@Entity
@Table(name = "maintenance")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Maintenance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "odometer")
    private Float odometer;

    @Size(max = 100)
    @Column(name = "local", length = 100)
    private String local;

    @Column(name = "cost", precision = 21, scale = 2)
    private BigDecimal cost;

    @OneToMany(mappedBy = "maintenance", fetch = FetchType.EAGER, cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Service> services = new HashSet<>();

    @ManyToOne
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Car car;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Maintenance id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public Maintenance date(LocalDate date) {
        this.setDate(date);
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Float getOdometer() {
        return this.odometer;
    }

    public Maintenance odometer(Float odometer) {
        this.setOdometer(odometer);
        return this;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }

    public String getLocal() {
        return this.local;
    }

    public Maintenance local(String local) {
        this.setLocal(local);
        return this;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Set<Service> getServices() {
        return this.services;
    }

    public void setServices(Set<Service> services) {
        if (this.services != null) {
            this.services.forEach(i -> i.setMaintenance(null));
        }
        if (services != null) {
            services.forEach(i -> i.setMaintenance(this));
        }
        this.services = services;
    }

    public Maintenance services(Set<Service> services) {
        this.setServices(services);
        return this;
    }

    public Maintenance addService(Service service) {
        this.services.add(service);
        service.setMaintenance(this);
        return this;
    }

    public Maintenance removeService(Service service) {
        this.services.remove(service);
        service.setMaintenance(null);
        return this;
    }

    public Car getCar() {
        return this.car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public Maintenance car(Car car) {
        this.setCar(car);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Maintenance)) {
            return false;
        }
        return id != null && id.equals(((Maintenance) o).id);
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
    return "Maintenance{"
        + "id="
        + getId()
        + ", date='"
        + getDate()
        + "'"
        + ", odometer="
        + getOdometer()
        + ", local='"
        + getLocal()
        + ", cost='"
        + getCost()
        + "'"
        + "}";
  }
}
