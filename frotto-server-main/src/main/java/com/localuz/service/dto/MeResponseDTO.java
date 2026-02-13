package com.localuz.service.dto;

import java.io.Serializable;
import java.time.LocalDate;

public class MeResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String login;
    private String firstName;
    private String lastName;
    private String imageUrl;
    private String langKey;
    private String personalName;
    private String personalCpf;
    private LocalDate personalBirthDate;
    private String personalEmail;
    private String personalPhone;
    private String taxPersonType;
    private String taxLandlordName;
    private String taxCpf;
    private String taxEmail;
    private String taxPhone;
    private String taxCompanyName;
    private String taxCnpj;
    private String taxIe;
    private String taxContactPhone;
    private String taxAddress;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
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
}
