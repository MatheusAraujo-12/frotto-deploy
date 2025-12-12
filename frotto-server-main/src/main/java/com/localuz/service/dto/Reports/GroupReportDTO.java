package com.localuz.service.dto.Reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class GroupReportDTO {

    private String group;
    private LocalDate date;

    private List<ReportsDTO> reportsDTO;

    private BigDecimal groupEarnings;
    private BigDecimal groupExpenses;
    private BigDecimal groupAdminFee;
    private BigDecimal groupNetEarnings;

    private BigDecimal groupInvested;
    private BigDecimal groupPercentageProfit;

    public GroupReportDTO(String group, LocalDate date) {
        this.group = group;
        this.date = date;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<ReportsDTO> getReportsDTO() {
        return reportsDTO;
    }

    public void setReportsDTO(List<ReportsDTO> reportsDTO) {
        this.reportsDTO = reportsDTO;
    }

    public BigDecimal getGroupEarnings() {
        return groupEarnings;
    }

    public void setGroupEarnings(BigDecimal groupEarnings) {
        this.groupEarnings = groupEarnings;
    }

    public BigDecimal getGroupExpenses() {
        return groupExpenses;
    }

    public void setGroupExpenses(BigDecimal groupExpenses) {
        this.groupExpenses = groupExpenses;
    }

    public BigDecimal getGroupAdminFee() {
        return groupAdminFee;
    }

    public void setGroupAdminFee(BigDecimal groupAdminFee) {
        this.groupAdminFee = groupAdminFee;
    }

    public BigDecimal getGroupNetEarnings() {
        return groupNetEarnings;
    }

    public void setGroupNetEarnings(BigDecimal groupNetEarnings) {
        this.groupNetEarnings = groupNetEarnings;
    }

    public BigDecimal getGroupInvested() {
        return groupInvested;
    }

    public void setGroupInvested(BigDecimal groupInvested) {
        this.groupInvested = groupInvested;
    }

    public BigDecimal getGroupPercentageProfit() {
        return groupPercentageProfit;
    }

    public void setGroupPercentageProfit(BigDecimal groupPercentageProfit) {
        this.groupPercentageProfit = groupPercentageProfit;
    }
}
