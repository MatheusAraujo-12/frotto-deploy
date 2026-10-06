package com.localuz.service.dto;

import java.math.BigDecimal;

/** The driver's share of an existing maintenance of the contract's car (Pendências -> Cobrar manutenção). */
public class SharedMaintenanceChargeRequest {

    /** Generated once by the screen for this operation and reused on every retry of it. */
    private String idempotencyKey;
    private Long maintenanceId;
    private BigDecimal amount;
    private String note;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getMaintenanceId() {
        return maintenanceId;
    }

    public void setMaintenanceId(Long maintenanceId) {
        this.maintenanceId = maintenanceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
