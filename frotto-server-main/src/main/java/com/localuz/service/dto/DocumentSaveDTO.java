package com.localuz.service.dto;

import com.localuz.domain.enumeration.ChecklistType;

import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import java.util.List;
import java.util.Map;

public class DocumentSaveDTO {
    /** Checklist de Entrega/Devolução: ENTREGA or DEVOLUCAO (required for every new checklist). */
    private ChecklistType checklistType;
    /** Checklist: the contract it delivers (optional, the finalization creates or reuses it) or returns (required). */
    private Long driverCarId;
    private DocumentType type;
    private Long driverId;
    private Long carId;
    private DocumentStatus status;
    private Map<String, Object> payload;
    private List<String> attachments;
    private String pdfUrl;

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public ChecklistType getChecklistType() {
        return checklistType;
    }

    public void setChecklistType(ChecklistType checklistType) {
        this.checklistType = checklistType;
    }

    public Long getDriverCarId() {
        return driverCarId;
    }

    public void setDriverCarId(Long driverCarId) {
        this.driverCarId = driverCarId;
    }
}
