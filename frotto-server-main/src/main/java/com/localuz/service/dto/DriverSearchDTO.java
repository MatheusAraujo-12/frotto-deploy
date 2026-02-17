package com.localuz.service.dto;

public class DriverSearchDTO {
    private Long id;
    private String name;
    private String cpf;
    private Boolean active;

    public DriverSearchDTO() {}

    public DriverSearchDTO(Long id, String name, String cpf, Boolean active) {
        this.id = id;
        this.name = name;
        this.cpf = cpf;
        this.active = active;
    }

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

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
