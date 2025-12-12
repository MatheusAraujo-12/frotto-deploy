package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.*;
import javax.validation.constraints.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Driver. */
@Entity
@Table(name = "driver")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Driver implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Size(max = 60)
    @Column(name = "name", length = 60)
    private String name;

    @Size(max = 20)
    @Column(name = "cpf", length = 20)
    private String cpf;

    @Size(max = 60)
    @Column(name = "email", length = 60)
    private String email;

    @Size(max = 60)
    @Column(name = "contact", length = 60)
    private String contact;

    @Size(max = 60)
    @Column(name = "emergency_contact", length = 60)
    private String emergencyContact;

    @Size(max = 60)
    @Column(name = "emergency_contact_second", length = 60)
    private String emergencyContactSecond;

    @Size(max = 60)
    @Column(name = "document_driver_license", length = 30)
    private String documentDriverLicense;

    @Size(max = 60)
    @Column(name = "document_driver_register", length = 30)
    private String documentDriverRegister;

    @Column(name = "public_score")
    private Float publicScore;

    @Column(name = "average_km")
    private Float averageKm;

    @Column(name = "average_inspectio_score")
    private Float averageInspectioScore;

    @Column(name = "average_driver_car_score")
    private Float averageDriverCarScore;

    @OneToOne
    @JoinColumn(unique = true)
    private Address address;

    @OneToMany(mappedBy = "driver")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Set<DriverCar> driverCars = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Driver id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Driver name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCpf() {
        return this.cpf;
    }

    public Driver cpf(String cpf) {
        this.setCpf(cpf);
        return this;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return this.email;
    }

    public Driver email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContact() {
        return this.contact;
    }

    public Driver contact(String contact) {
        this.setContact(contact);
        return this;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getEmergencyContact() {
        return this.emergencyContact;
    }

    public Driver emergencyContact(String emergencyContact) {
        this.setEmergencyContact(emergencyContact);
        return this;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public String getEmergencyContactSecond() {
        return emergencyContactSecond;
    }

    public void setEmergencyContactSecond(String emergencyContactSecond) {
        this.emergencyContactSecond = emergencyContactSecond;
    }

    public String getDocumentDriverLicense() {
        return documentDriverLicense;
    }

    public void setDocumentDriverLicense(String documentDriverLicense) {
        this.documentDriverLicense = documentDriverLicense;
    }

    public String getDocumentDriverRegister() {
        return documentDriverRegister;
    }

    public void setDocumentDriverRegister(String documentDriverRegister) {
        this.documentDriverRegister = documentDriverRegister;
    }

    public Float getPublicScore() {
        return this.publicScore;
    }

    public Driver publicScore(Float publicScore) {
        this.setPublicScore(publicScore);
        return this;
    }

    public void setPublicScore(Float publicScore) {
        this.publicScore = publicScore;
    }

    public Float getAverageKm() {
        return this.averageKm;
    }

    public Driver averageKm(Float averageKm) {
        this.setAverageKm(averageKm);
        return this;
    }

    public void setAverageKm(Float averageKm) {
        this.averageKm = averageKm;
    }

    public Float getAverageInspectioScore() {
        return this.averageInspectioScore;
    }

    public Driver averageInspectioScore(Float averageInspectioScore) {
        this.setAverageInspectioScore(averageInspectioScore);
        return this;
    }

    public void setAverageInspectioScore(Float averageInspectioScore) {
        this.averageInspectioScore = averageInspectioScore;
    }

    public Float getAverageDriverCarScore() {
        return this.averageDriverCarScore;
    }

    public Driver averageDriverCarScore(Float averageDriverCarScore) {
        this.setAverageDriverCarScore(averageDriverCarScore);
        return this;
    }

    public void setAverageDriverCarScore(Float averageDriverCarScore) {
        this.averageDriverCarScore = averageDriverCarScore;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Driver address(Address address) {
        this.setAddress(address);
        return this;
    }

    public Set<DriverCar> getDriverCars() {
        return this.driverCars;
    }

    public void setDriverCars(Set<DriverCar> driverCars) {
        if (this.driverCars != null) {
            this.driverCars.forEach(i -> i.setDriver(null));
        }
        if (driverCars != null) {
            driverCars.forEach(i -> i.setDriver(this));
        }
        this.driverCars = driverCars;
    }

    public Driver driverCars(Set<DriverCar> driverCars) {
        this.setDriverCars(driverCars);
        return this;
    }

    public Driver addDriverCar(DriverCar driverCar) {
        this.driverCars.add(driverCar);
        driverCar.setDriver(this);
        return this;
    }

    public Driver removeDriverCar(DriverCar driverCar) {
        this.driverCars.remove(driverCar);
        driverCar.setDriver(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Driver)) {
            return false;
        }
        return id != null && id.equals(((Driver) o).id);
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
    return "Driver{"
        + "id="
        + getId()
        + ", name='"
        + getName()
        + "'"
        + ", cpf='"
        + getCpf()
        + "'"
        + ", email='"
        + getEmail()
        + "'"
        + ", contact='"
        + getContact()
        + "'"
        + ", emergencyContact='"
        + getEmergencyContact()
        + "'"
        + ", emergencyContactSecond='"
        + getEmergencyContactSecond()
        + "'"
        + ", documentDriverLicense='"
        + getDocumentDriverLicense()
        + "'"
        + ", documentDriverRegister='"
        + getDocumentDriverRegister()
        + "'"
        + ", publicScore="
        + getPublicScore()
        + ", averageKm="
        + getAverageKm()
        + ", averageInspectioScore="
        + getAverageInspectioScore()
        + ", averageDriverCarScore="
        + getAverageDriverCarScore()
        + "}";
  }
}
