package com.localuz.service.dto;

import java.io.Serializable;
import java.time.LocalDate;
import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

public class UpdatePersonalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Size(max = 256)
    private String imageUrl;

    @Size(max = 10)
    private String langKey;

    @Size(max = 120)
    private String personalName;

    @Size(max = 20)
    private String personalCpf;

    private LocalDate personalBirthDate;

    @Email
    @Size(max = 254)
    private String personalEmail;

    @Size(max = 20)
    private String personalPhone;

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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
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
}
