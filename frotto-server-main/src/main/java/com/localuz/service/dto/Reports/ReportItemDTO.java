package com.localuz.service.dto.Reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReportItemDTO {

    private String name;
    private LocalDate date;
    private BigDecimal ammount;

    public ReportItemDTO(String name, LocalDate date, BigDecimal ammount) {
        this.name = name;
        this.date = date;
        this.ammount = ammount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getAmmount() {
        return ammount;
    }

    public void setAmmount(BigDecimal ammount) {
        this.ammount = ammount;
    }
}
