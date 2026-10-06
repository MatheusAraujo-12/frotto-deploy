package com.localuz.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DocumentDTO {
    private Long id;
    private DocumentType type;
    private DocumentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long driverId;
    private String driverName;
    private String driverCpf;
    private Long carId;
    private String carPlate;
    private String carModel;
    private String pdfUrl;
    /** The pendency the document was issued from (Pendências -> Emitir documento), when it was. */
    private Long originPendencyId;
    private Map<String, Object> payload;
    /** Stored keys of the attachments (unchanged contract). */
    private List<String> attachments;

    /**
     * Reference (attachment key or checklist photo reference of the payload) → URL to load: signed local URL or
     * legacy bucket URL; "" = not available. A reference absent from the map keeps the client's legacy resolution
     * (s3 mode only). Present only in the document detail.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> attachmentUrls;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverCpf() {
        return driverCpf;
    }

    public void setDriverCpf(String driverCpf) {
        this.driverCpf = driverCpf;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public String getCarPlate() {
        return carPlate;
    }

    public void setCarPlate(String carPlate) {
        this.carPlate = carPlate;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
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

    public Map<String, String> getAttachmentUrls() {
        return attachmentUrls;
    }

    public void setAttachmentUrls(Map<String, String> attachmentUrls) {
        this.attachmentUrls = attachmentUrls;
    }

    public Long getOriginPendencyId() {
        return originPendencyId;
    }

    public void setOriginPendencyId(Long originPendencyId) {
        this.originPendencyId = originPendencyId;
    }
}
