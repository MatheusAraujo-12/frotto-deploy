package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A maintenance of the contract's car as a charge option: what it was and how much is still assignable. */
public class MaintenanceChargeOptionDTO {

    private final Long id;
    private final LocalDate date;
    private final String local;
    private final String description;
    private final BigDecimal cost;
    private final BigDecimal assignedAmount;
    private final BigDecimal availableAmount;

    public MaintenanceChargeOptionDTO(
        Long id,
        LocalDate date,
        String local,
        String description,
        BigDecimal cost,
        BigDecimal assignedAmount
    ) {
        this.id = id;
        this.date = date;
        this.local = local;
        this.description = description;
        this.cost = cost;
        this.assignedAmount = assignedAmount;
        this.availableAmount = cost.subtract(assignedAmount).max(BigDecimal.ZERO);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLocal() {
        return local;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getAssignedAmount() {
        return assignedAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    /** False when the whole cost is already assigned (or the maintenance has no cost): no new charge. */
    public boolean isChargeable() {
        return availableAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
