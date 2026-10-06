package com.localuz.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** A traffic fine charged to the driver of a contract (Pendências -> Nova multa). */
public class FineChargeRequest {

    /** Generated once by the screen for this operation and reused on every retry of it. */
    private String idempotencyKey;
    private BigDecimal amount;
    private LocalDate infractionDate;
    private LocalTime infractionTime;
    private String ait;
    private String agency;
    private String location;
    private String classification;
    private LocalDate dueDate;
    private String note;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getInfractionDate() {
        return infractionDate;
    }

    public void setInfractionDate(LocalDate infractionDate) {
        this.infractionDate = infractionDate;
    }

    public LocalTime getInfractionTime() {
        return infractionTime;
    }

    public void setInfractionTime(LocalTime infractionTime) {
        this.infractionTime = infractionTime;
    }

    public String getAit() {
        return ait;
    }

    public void setAit(String ait) {
        this.ait = ait;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
