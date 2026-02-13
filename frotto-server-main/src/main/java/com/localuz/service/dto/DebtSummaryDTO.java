package com.localuz.service.dto;

import java.math.BigDecimal;

public class DebtSummaryDTO {

    private Long driverCarId;
    private BigDecimal totalOutstanding;
    private long openPendenciesCount;
    private long totalPendenciesCount;
    private long paidPendenciesCount;

    public DebtSummaryDTO() {}

    public DebtSummaryDTO(
        Long driverCarId,
        BigDecimal totalOutstanding,
        long openPendenciesCount,
        long totalPendenciesCount,
        long paidPendenciesCount
    ) {
        this.driverCarId = driverCarId;
        this.totalOutstanding = totalOutstanding;
        this.openPendenciesCount = openPendenciesCount;
        this.totalPendenciesCount = totalPendenciesCount;
        this.paidPendenciesCount = paidPendenciesCount;
    }

    public Long getDriverCarId() {
        return driverCarId;
    }

    public void setDriverCarId(Long driverCarId) {
        this.driverCarId = driverCarId;
    }

    public BigDecimal getTotalOutstanding() {
        return totalOutstanding;
    }

    public void setTotalOutstanding(BigDecimal totalOutstanding) {
        this.totalOutstanding = totalOutstanding;
    }

    public long getOpenPendenciesCount() {
        return openPendenciesCount;
    }

    public void setOpenPendenciesCount(long openPendenciesCount) {
        this.openPendenciesCount = openPendenciesCount;
    }

    public long getTotalPendenciesCount() {
        return totalPendenciesCount;
    }

    public void setTotalPendenciesCount(long totalPendenciesCount) {
        this.totalPendenciesCount = totalPendenciesCount;
    }

    public long getPaidPendenciesCount() {
        return paidPendenciesCount;
    }

    public void setPaidPendenciesCount(long paidPendenciesCount) {
        this.paidPendenciesCount = paidPendenciesCount;
    }
}
