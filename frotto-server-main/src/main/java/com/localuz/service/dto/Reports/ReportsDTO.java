package com.localuz.service.dto.Reports;

import com.localuz.domain.Car;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReportsDTO {

    private Long carId;
    private String carName;
    private String color;
    private Integer year;
    private Float odometer;
    private LocalDate date;
    private BigDecimal initialValue;
    private BigDecimal earnings;
    private BigDecimal expenses;
    private BigDecimal adminFee;
    private BigDecimal netEarnings;
    private BigDecimal percentageProfit;

    private List<ReportItemDTO> incomes;
    private List<ReportItemDTO> carExpenses;
    private List<ReportItemDTO> inspections;
    private List<ReportItemDTO> maintenances;

    public ReportsDTO() {}

    public ReportsDTO(Long carId, String carName, LocalDate date, Float odometer, String color, BigDecimal initialValue, Integer year) {
        this.carId = carId;
        this.carName = carName;
        this.date = date;
        this.odometer = odometer;
        this.color = color;
        this.initialValue = initialValue;
        this.year = year;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public String getCarName() {
        return carName;
    }

    public void setCarName(String carName) {
        this.carName = carName;
    }

    public Float getOdometer() {
        return odometer;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<ReportItemDTO> getIncomes() {
        return incomes;
    }

    public void setIncomes(List<ReportItemDTO> incomes) {
        this.incomes = incomes;
    }

    public List<ReportItemDTO> getCarExpenses() {
        return carExpenses;
    }

    public void setCarExpenses(List<ReportItemDTO> carExpenses) {
        this.carExpenses = carExpenses;
    }

    public List<ReportItemDTO> getInspections() {
        return inspections;
    }

    public void setInspections(List<ReportItemDTO> inspections) {
        this.inspections = inspections;
    }

    public List<ReportItemDTO> getMaintenances() {
        return maintenances;
    }

    public void setMaintenances(List<ReportItemDTO> maintenances) {
        this.maintenances = maintenances;
    }

    public BigDecimal getEarnings() {
        return earnings;
    }

    public void setEarnings(BigDecimal earnings) {
        this.earnings = earnings;
    }

    public BigDecimal getExpenses() {
        return expenses;
    }

    public void setExpenses(BigDecimal expenses) {
        this.expenses = expenses;
    }

    public BigDecimal getAdminFee() {
        return adminFee;
    }

    public void setAdminFee(BigDecimal adminFee) {
        this.adminFee = adminFee;
    }

    public BigDecimal getNetEarnings() {
        return netEarnings;
    }

    public void setNetEarnings(BigDecimal netEarnings) {
        this.netEarnings = netEarnings;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(BigDecimal initialValue) {
        this.initialValue = initialValue;
    }

    public BigDecimal getPercentageProfit() {
        return percentageProfit;
    }

    public void setPercentageProfit(BigDecimal percentageProfit) {
        this.percentageProfit = percentageProfit;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }
}
