package com.localuz.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/** A Car History. */
@Entity
@Table(name = "car_history")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@IdClass(CarHistoryId.class)
public class CarHistory {

    @Id
    @Column(name = "car_id")
    private Long carId;

    @Id
    @Column(name = "date")
    private LocalDate date;

    @Column(name = "earnings")
    private BigDecimal earnings;

    @Column(name = "expenses")
    private BigDecimal expenses;

    @Column(name = "admin_fee")
    private BigDecimal adminFee;

    @Column(name = "net_earnings")
    private BigDecimal netEarnings;

    @Column(name = "odometer")
    private Float odometer;

    public CarHistory() {}

    public CarHistory(
        Long carId,
        LocalDate date,
        BigDecimal earnings,
        BigDecimal expenses,
        BigDecimal adminFee,
        BigDecimal netEarnings,
        Float odometer
    ) {
        this.carId = carId;
        this.date = date;
        this.earnings = earnings;
        this.expenses = expenses;
        this.adminFee = adminFee;
        this.netEarnings = netEarnings;
        this.odometer = odometer;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public Float getOdometer() {
        return odometer;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }
}
