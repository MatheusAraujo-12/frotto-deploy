package com.localuz.service.dto;

import com.localuz.domain.Car;
import com.localuz.domain.enumeration.CommissionType;
import java.math.BigDecimal;

public class CarFormDTO {

    private Long id;
    private String name;
    private String model;
    private String color;
    private String plate;
    private Float odometer;
    private Float commissionPercent;
    private Float administrationFee;
    private CommissionType commissionType;
    private BigDecimal commissionFixed;
    private BigDecimal initialValue;
    private Integer year;
    private String group;
    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public Float getOdometer() {
        return odometer;
    }

    public void setOdometer(Float odometer) {
        this.odometer = odometer;
    }

    public Float getCommissionPercent() {
        return commissionPercent;
    }

    public void setCommissionPercent(Float commissionPercent) {
        this.commissionPercent = commissionPercent;
    }

    public Float getAdministrationFee() {
        return administrationFee;
    }

    public void setAdministrationFee(Float administrationFee) {
        this.administrationFee = administrationFee;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public void setCommissionType(CommissionType commissionType) {
        this.commissionType = commissionType;
    }

    public BigDecimal getCommissionFixed() {
        return commissionFixed;
    }

    public void setCommissionFixed(BigDecimal commissionFixed) {
        this.commissionFixed = commissionFixed;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(BigDecimal initialValue) {
        this.initialValue = initialValue;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Car toCar() {
        Car car = new Car();
        car.setId(id);
        car.setName(name);
        car.setModel(model);
        car.setColor(color);
        car.setPlate(plate);
        car.setOdometer(odometer);
        car.setCommissionPercent(commissionPercent != null ? commissionPercent : administrationFee);
        car.setCommissionType(commissionType);
        car.setCommissionFixed(commissionFixed);
        car.setInitialValue(initialValue);
        car.setYear(year);
        car.setGroup(group);
        car.setActive(active);
        return car;
    }
}
