package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.Inspection;
import com.localuz.domain.Maintenance;
import com.localuz.repository.CarRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.repository.MaintenanceRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CarService {

    private final InspectionRepository inspectionRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final CarRepository carRepository;

    public CarService(InspectionRepository inspectionRepository, MaintenanceRepository maintenanceRepository, CarRepository carRepository) {
        this.inspectionRepository = inspectionRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.carRepository = carRepository;
    }

    public void updateCarOdometerByDate(LocalDate currentDate, Float odometer, Car car) {
        boolean updateCar = true;
        Inspection lastInspection = inspectionRepository.findFirstByCarOrderByDateDesc(car);
        Maintenance lastMaintenance = maintenanceRepository.findFirstByCarOrderByDateDesc(car);
        if (lastInspection != null && lastInspection.getDate().isAfter(currentDate)) {
            updateCar = false;
        }
        if (lastMaintenance != null && lastMaintenance.getDate().isAfter(currentDate)) {
            updateCar = false;
        }

        if (updateCar) {
            car.setOdometer(odometer);
            carRepository.save(car);
        }
    }
}
