package com.localuz.service.dto.Reports;

import com.localuz.domain.CarHistory;
import java.math.BigDecimal;
import java.util.List;

public class ReportHistoryDTO {

    private Long carId;
    private String carName;
    private BigDecimal initialValue;
    private String color;
    private Integer year;
    private List<CarHistory> carHistory;

    private BigDecimal totalEarnings;
    private BigDecimal totalExpenses;
    private BigDecimal totalAdminFee;
    private BigDecimal totalNetEarnings;

    private BigDecimal totalPercentageProfit;
    private BigDecimal averagePercentageProfit;

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<CarHistory> getCarHistory() {
        return carHistory;
    }

    public void setCarHistory(List<CarHistory> carHistory) {
        this.carHistory = carHistory;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public void setTotalEarnings(BigDecimal totalEarnings) {
        this.totalEarnings = totalEarnings;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public BigDecimal getTotalAdminFee() {
        return totalAdminFee;
    }

    public void setTotalAdminFee(BigDecimal totalAdminFee) {
        this.totalAdminFee = totalAdminFee;
    }

    public BigDecimal getTotalNetEarnings() {
        return totalNetEarnings;
    }

    public void setTotalNetEarnings(BigDecimal totalNetEarnings) {
        this.totalNetEarnings = totalNetEarnings;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(BigDecimal initialValue) {
        this.initialValue = initialValue;
    }

    public BigDecimal getTotalPercentageProfit() {
        return totalPercentageProfit;
    }

    public void setTotalPercentageProfit(BigDecimal totalPercentageProfit) {
        this.totalPercentageProfit = totalPercentageProfit;
    }

    public BigDecimal getAveragePercentageProfit() {
        return averagePercentageProfit;
    }

    public void setAveragePercentageProfit(BigDecimal averagePercentageProfit) {
        this.averagePercentageProfit = averagePercentageProfit;
    }
}
