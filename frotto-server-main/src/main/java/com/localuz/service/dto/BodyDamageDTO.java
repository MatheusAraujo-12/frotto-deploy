package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;

public class BodyDamageDTO {

    private Long id;

    private LocalDate date;

    private String responsible;

    private String part;

    private String imagePath;

    private BigDecimal cost;

    private Boolean resolved;

    private MultipartFile file;

    private MultipartFile file2;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getResponsible() {
        return responsible;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public MultipartFile getFile2() {
        return file2;
    }

    public void setFile2(MultipartFile file2) {
        this.file2 = file2;
    }

    public String toString() {
        return (
            "CarBodyDamage{" +
            "id=" +
            getId() +
            ", date='" +
            getDate() +
            "'" +
            ", responsible='" +
            getResponsible() +
            "'" +
            ", part='" +
            getPart() +
            "'" +
            ", imagePath='" +
            getImagePath() +
            "'" +
            ", cost=" +
            getCost() +
            ", resolved='" +
            getResolved() +
            "'" +
            "}"
        );
    }
}
