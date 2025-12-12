package com.localuz.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class CarHistoryId implements Serializable {

    private Long carId;

    private LocalDate date;

    public CarHistoryId() {}

    public CarHistoryId(Long carId, LocalDate date) {
        this.carId = carId;
        this.date = date;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CarHistoryId that = (CarHistoryId) o;
        return carId.equals(that.carId) && date.equals(that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carId, date);
    }
}
