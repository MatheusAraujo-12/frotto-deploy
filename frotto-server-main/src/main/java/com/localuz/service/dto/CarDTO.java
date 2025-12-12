package com.localuz.service.dto;

import com.localuz.domain.Car;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Inspection;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Reminder;
import java.util.List;

public class CarDTO {

    private Car car;
    private Maintenance lastMaintenance;
    private Inspection lastInspection;
    private DriverCar activeDriver;
    private List<Reminder> reminders;

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public Maintenance getLastMaintenance() {
        return lastMaintenance;
    }

    public void setLastMaintenance(Maintenance lastMaintenance) {
        this.lastMaintenance = lastMaintenance;
    }

    public Inspection getLastInspection() {
        return lastInspection;
    }

    public void setLastInspection(Inspection lastInspection) {
        this.lastInspection = lastInspection;
    }

    public DriverCar getActiveDriver() {
        return activeDriver;
    }

    public void setActiveDriver(DriverCar activeDriver) {
        this.activeDriver = activeDriver;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }
}
