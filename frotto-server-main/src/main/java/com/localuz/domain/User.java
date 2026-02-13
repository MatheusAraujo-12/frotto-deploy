package com.localuz.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.localuz.config.Constants;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A user. */
@Entity
@Table(name = "jhi_user")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class User extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    @Column(length = 50, unique = true, nullable = false)
    private String login;

    @JsonIgnore
    @NotNull
    @Size(min = 60, max = 60)
    @Column(name = "password_hash", length = 60, nullable = false)
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Email
    @Size(min = 5, max = 254)
    @Column(length = 254, unique = true)
    private String email;

    @Size(max = 120)
    @Column(name = "personal_name", length = 120)
    private String personalName;

    @Size(max = 20)
    @Column(name = "personal_cpf", length = 20)
    private String personalCpf;

    @Column(name = "personal_birth_date")
    private LocalDate personalBirthDate;

    @Email
    @Size(max = 254)
    @Column(name = "personal_email", length = 254)
    private String personalEmail;

    @Size(max = 20)
    @Column(name = "personal_phone", length = 20)
    private String personalPhone;

    @Size(max = 10)
    @Column(name = "tax_person_type", length = 10)
    private String taxPersonType;

    @Size(max = 120)
    @Column(name = "tax_landlord_name", length = 120)
    private String taxLandlordName;

    @Size(max = 20)
    @Column(name = "tax_cpf", length = 20)
    private String taxCpf;

    @Email
    @Size(max = 254)
    @Column(name = "tax_email", length = 254)
    private String taxEmail;

    @Size(max = 20)
    @Column(name = "tax_phone", length = 20)
    private String taxPhone;

    @Size(max = 160)
    @Column(name = "tax_company_name", length = 160)
    private String taxCompanyName;

    @Size(max = 20)
    @Column(name = "tax_cnpj", length = 20)
    private String taxCnpj;

    @Size(max = 40)
    @Column(name = "tax_ie", length = 40)
    private String taxIe;

    @Size(max = 20)
    @Column(name = "tax_contact_phone", length = 20)
    private String taxContactPhone;

    @Size(max = 500)
    @Column(name = "tax_address", length = 500)
    private String taxAddress;

    @NotNull
    @Column(nullable = false)
    private boolean activated = false;

    @Size(min = 2, max = 10)
    @Column(name = "lang_key", length = 10)
    private String langKey;

    @Size(max = 256)
    @Column(name = "image_url", length = 256)
    private String imageUrl;

    @Size(max = 20)
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    private String activationKey;

    @Size(max = 20)
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "jhi_user_authority",
        joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") },
        inverseJoinColumns = { @JoinColumn(name = "authority_name", referencedColumnName = "name") }
    )
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @BatchSize(size = 20)
    private Set<Authority> authorities = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    // Lowercase the login before saving it in database
    public void setLogin(String login) {
        this.login = StringUtils.lowerCase(login, Locale.ENGLISH);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPersonalName() {
        return personalName;
    }

    public void setPersonalName(String personalName) {
        this.personalName = personalName;
    }

    public String getPersonalCpf() {
        return personalCpf;
    }

    public void setPersonalCpf(String personalCpf) {
        this.personalCpf = personalCpf;
    }

    public LocalDate getPersonalBirthDate() {
        return personalBirthDate;
    }

    public void setPersonalBirthDate(LocalDate personalBirthDate) {
        this.personalBirthDate = personalBirthDate;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public String getPersonalPhone() {
        return personalPhone;
    }

    public void setPersonalPhone(String personalPhone) {
        this.personalPhone = personalPhone;
    }

    public String getTaxPersonType() {
        return taxPersonType;
    }

    public void setTaxPersonType(String taxPersonType) {
        this.taxPersonType = taxPersonType;
    }

    public String getTaxLandlordName() {
        return taxLandlordName;
    }

    public void setTaxLandlordName(String taxLandlordName) {
        this.taxLandlordName = taxLandlordName;
    }

    public String getTaxCpf() {
        return taxCpf;
    }

    public void setTaxCpf(String taxCpf) {
        this.taxCpf = taxCpf;
    }

    public String getTaxEmail() {
        return taxEmail;
    }

    public void setTaxEmail(String taxEmail) {
        this.taxEmail = taxEmail;
    }

    public String getTaxPhone() {
        return taxPhone;
    }

    public void setTaxPhone(String taxPhone) {
        this.taxPhone = taxPhone;
    }

    public String getTaxCompanyName() {
        return taxCompanyName;
    }

    public void setTaxCompanyName(String taxCompanyName) {
        this.taxCompanyName = taxCompanyName;
    }

    public String getTaxCnpj() {
        return taxCnpj;
    }

    public void setTaxCnpj(String taxCnpj) {
        this.taxCnpj = taxCnpj;
    }

    public String getTaxIe() {
        return taxIe;
    }

    public void setTaxIe(String taxIe) {
        this.taxIe = taxIe;
    }

    public String getTaxContactPhone() {
        return taxContactPhone;
    }

    public void setTaxContactPhone(String taxContactPhone) {
        this.taxContactPhone = taxContactPhone;
    }

    public String getTaxAddress() {
        return taxAddress;
    }

    public void setTaxAddress(String taxAddress) {
        this.taxAddress = taxAddress;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        return id != null && id.equals(((User) o).id);
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
    return "User{"
        + "login='"
        + login
        + '\''
        + ", firstName='"
        + firstName
        + '\''
        + ", lastName='"
        + lastName
        + '\''
        + ", email='"
        + email
        + '\''
        + ", personalName='"
        + personalName
        + '\''
        + ", personalCpf='"
        + personalCpf
        + '\''
        + ", personalBirthDate="
        + personalBirthDate
        + ", personalEmail='"
        + personalEmail
        + '\''
        + ", personalPhone='"
        + personalPhone
        + '\''
        + ", taxPersonType='"
        + taxPersonType
        + '\''
        + ", taxLandlordName='"
        + taxLandlordName
        + '\''
        + ", taxCpf='"
        + taxCpf
        + '\''
        + ", taxEmail='"
        + taxEmail
        + '\''
        + ", taxPhone='"
        + taxPhone
        + '\''
        + ", taxCompanyName='"
        + taxCompanyName
        + '\''
        + ", taxCnpj='"
        + taxCnpj
        + '\''
        + ", taxIe='"
        + taxIe
        + '\''
        + ", taxContactPhone='"
        + taxContactPhone
        + '\''
        + ", taxAddress='"
        + taxAddress
        + '\''
        + ", imageUrl='"
        + imageUrl
        + '\''
        + ", activated='"
        + activated
        + '\''
        + ", langKey='"
        + langKey
        + '\''
        + ", activationKey='"
        + activationKey
        + '\''
        + "}";
  }
}
