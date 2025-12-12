package com.localuz.DTO;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.localuz.domain.Car;

public class CarDriverDto {

    @JsonUnwrapped
    private Car car;

    private String driverName;

    public CarDriverDto(Car car, String driverName) {
        this.car = car;
        this.driverName = driverName;
    }

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }
}
