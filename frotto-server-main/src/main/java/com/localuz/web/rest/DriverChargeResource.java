package com.localuz.web.rest;

import com.localuz.domain.DriverDocument;
import com.localuz.domain.Pendency;
import com.localuz.service.DriverChargeService;
import com.localuz.service.DriverChargeService.Outcome;
import com.localuz.service.dto.DocumentDTO;
import com.localuz.service.dto.FineChargeRequest;
import com.localuz.service.dto.MaintenanceChargeOptionDTO;
import com.localuz.service.dto.MaintenanceChargeSummaryDTO;
import com.localuz.service.dto.SharedMaintenanceChargeRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pendências as the operational home of fines and shared maintenance. No class-level transaction on purpose: each
 * operation runs in its own transaction inside {@link DriverChargeService}, so a retry or a concurrent attempt of
 * the same operation can be answered with what the first one created (201 when created now, 200 when replayed).
 */
@RestController
@RequestMapping("/api")
public class DriverChargeResource {

    private final DriverChargeService driverChargeService;

    public DriverChargeResource(DriverChargeService driverChargeService) {
        this.driverChargeService = driverChargeService;
    }

    @PostMapping("/pendencies/car-driver/{driverCarId}/fines")
    public ResponseEntity<Pendency> chargeFine(@PathVariable Long driverCarId, @RequestBody FineChargeRequest request) {
        return pendencyResponse(driverChargeService.chargeFine(driverCarId, request));
    }

    @PostMapping("/pendencies/car-driver/{driverCarId}/shared-maintenance")
    public ResponseEntity<Pendency> chargeSharedMaintenance(
        @PathVariable Long driverCarId,
        @RequestBody SharedMaintenanceChargeRequest request
    ) {
        return pendencyResponse(driverChargeService.chargeSharedMaintenance(driverCarId, request));
    }

    /** Maintenances of the contract's car that a shared-maintenance charge can come from. */
    @GetMapping("/pendencies/car-driver/{driverCarId}/chargeable-maintenances")
    public List<MaintenanceChargeOptionDTO> chargeableMaintenances(@PathVariable Long driverCarId) {
        return driverChargeService.chargeableMaintenances(driverCarId);
    }

    @GetMapping("/pendencies/shared-maintenance/{maintenanceId}/summary")
    public MaintenanceChargeSummaryDTO maintenanceSummary(@PathVariable Long maintenanceId) {
        return driverChargeService.maintenanceSummary(maintenanceId);
    }

    /** Issues (or returns the already issued) document of the pendency; the PDF is then generated from it. */
    @PostMapping("/pendencies/{id}/document")
    public ResponseEntity<DocumentDTO> issueDocument(@PathVariable Long id) {
        Outcome<DriverDocument> outcome = driverChargeService.issueDocument(id);
        DriverDocument document = outcome.getValue();
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setType(document.getType());
        dto.setStatus(document.getStatus());
        dto.setOriginPendencyId(document.getOriginPendencyId());
        return outcome.isCreated()
            ? ResponseEntity.created(URI.create("/api/documents/" + document.getId())).body(dto)
            : ResponseEntity.ok(dto);
    }

    private static ResponseEntity<Pendency> pendencyResponse(Outcome<Pendency> outcome) {
        Pendency pendency = outcome.getValue();
        return outcome.isCreated()
            ? ResponseEntity.created(URI.create("/api/pendencies/" + pendency.getId())).body(pendency)
            : ResponseEntity.status(HttpStatus.OK).body(pendency);
    }
}
