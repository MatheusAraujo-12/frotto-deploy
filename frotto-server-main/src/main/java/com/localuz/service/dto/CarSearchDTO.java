package com.localuz.service.dto;

import com.localuz.domain.enumeration.CarAdminStatus;

public class CarSearchDTO {
    private Long id;
    private String plate;
    private String name;
    private String model;
    private Boolean active;
    private CarAdminStatus adminStatus;

    public CarSearchDTO() {}

    public CarSearchDTO(Long id, String plate, String name, String model, Boolean active, CarAdminStatus adminStatus) {
        this.id = id;
        this.plate = plate;
        this.name = name;
        this.model = model;
        this.active = active;
        this.adminStatus = adminStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public CarAdminStatus getAdminStatus() {
        return adminStatus;
    }

    public void setAdminStatus(CarAdminStatus adminStatus) {
        this.adminStatus = adminStatus;
    }
}
