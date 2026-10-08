package com.localuz.service.dto;

import com.localuz.domain.DriverCar;
import com.localuz.domain.enumeration.DriverAssignmentType;
import java.io.Serializable;
import java.time.LocalDate;

/** One contract of a driver's vehicle history (read only): the car, the kind of contract, its period and its state. */
public class DriverCarHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long driverCarId;
    private Long carId;
    private String carPlate;
    private String carModel;
    /** RESERVE for a temporary reserve-car contract; PERMANENT for a primary contract. */
    private DriverAssignmentType assignmentType;
    private LocalDate startDate;
    private LocalDate endDate;
    /** ACTIVE, SUSPENDED or CONCLUDED (as the contract itself reports it). */
    private String status;

    public static DriverCarHistoryDTO of(DriverCar contract) {
        DriverCarHistoryDTO dto = new DriverCarHistoryDTO();
        dto.driverCarId = contract.getId();
        dto.carId = contract.getCar() == null ? null : contract.getCar().getId();
        dto.carPlate = contract.getCar() == null ? null : contract.getCar().getPlate();
        dto.carModel = contract.getCar() == null ? null : contract.getCar().getModel();
        dto.assignmentType = contract.isReserve() ? DriverAssignmentType.RESERVE : DriverAssignmentType.PERMANENT;
        dto.startDate = contract.getStartDate();
        dto.endDate = contract.getEndDate();
        dto.status = contract.getStatus();
        return dto;
    }

    public Long getDriverCarId() {
        return driverCarId;
    }

    public Long getCarId() {
        return carId;
    }

    public String getCarPlate() {
        return carPlate;
    }

    public String getCarModel() {
        return carModel;
    }

    public DriverAssignmentType getAssignmentType() {
        return assignmentType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }
}
