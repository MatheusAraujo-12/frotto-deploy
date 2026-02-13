package com.localuz.service.dto;

import java.io.Serializable;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class UpdateTaxDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Pattern(regexp = "CPF|CNPJ", message = "taxPersonType must be CPF or CNPJ")
    @Size(max = 10)
    private String taxPersonType;

    @Size(max = 120)
    private String taxLandlordName;

    @Size(max = 20)
    private String taxCpf;

    @Email
    @Size(max = 254)
    private String taxEmail;

    @Size(max = 20)
    private String taxPhone;

    @Size(max = 160)
    private String taxCompanyName;

    @Size(max = 20)
    private String taxCnpj;

    @Size(max = 40)
    private String taxIe;

    @Size(max = 20)
    private String taxContactPhone;

    @Size(max = 500)
    private String taxAddress;

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
