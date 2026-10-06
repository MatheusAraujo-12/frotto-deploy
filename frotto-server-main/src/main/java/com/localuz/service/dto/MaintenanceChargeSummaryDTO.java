package com.localuz.service.dto;

import java.math.BigDecimal;

/** How much of a maintenance is already charged to drivers (assigned responsibility, paid or not). */
public class MaintenanceChargeSummaryDTO {

    private Long maintenanceId;
    private BigDecimal maintenanceCost;
    private BigDecimal assignedAmount;
    private BigDecimal availableAmount;
    private int chargesCount;

    public MaintenanceChargeSummaryDTO() {}

    public MaintenanceChargeSummaryDTO(Long maintenanceId, BigDecimal maintenanceCost, BigDecimal assignedAmount, int chargesCount) {
        this.maintenanceId = maintenanceId;
        this.maintenanceCost = maintenanceCost;
        this.assignedAmount = assignedAmount;
        this.availableAmount = maintenanceCost.subtract(assignedAmount).max(BigDecimal.ZERO);
        this.chargesCount = chargesCount;
    }

    public Long getMaintenanceId() {
        return maintenanceId;
    }

    public BigDecimal getMaintenanceCost() {
        return maintenanceCost;
    }

    public BigDecimal getAssignedAmount() {
        return assignedAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public int getChargesCount() {
        return chargesCount;
    }
}
