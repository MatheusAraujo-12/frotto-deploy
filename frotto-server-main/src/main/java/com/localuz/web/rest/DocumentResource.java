package com.localuz.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Pendency;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.ChecklistType;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.PendencyOriginType;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.ChecklistService;
import com.localuz.service.DebtConfessionService;
import com.localuz.service.UserService;
import com.localuz.service.storage.FileStorageGateway;
import com.localuz.service.storage.StorageCategory;
import com.localuz.service.storage.StorageKeys;
import com.localuz.service.dto.DocumentDTO;
import com.localuz.service.dto.DocumentGeneratePdfDTO;
import com.localuz.service.dto.DocumentSaveDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.HeaderUtil;

@RestController
@RequestMapping("/api/documents")
@Transactional
public class DocumentResource {

    private final Logger log = LoggerFactory.getLogger(DocumentResource.class);

    private static final String ENTITY_NAME = "document";
    private static final int DEFAULT_LIST_LIMIT = 30;
    private static final int MAX_LIST_LIMIT = 100;
    /** Payload keys where the frontend keeps checklist photo references (all must be attachments of the document). */
    private static final List<String> CHECKLIST_PHOTO_REF_KEYS = List.of(
        "attachmentsChecklist",
        "checklistPhotoRefs",
        "fotosChecklist",
        "fotos",
        "attachments"
    );

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DriverDocumentRepository documentRepository;
    private final DriverRepository driverRepository;
    private final CarRepository carRepository;
    private final DriverCarRepository driverCarRepository;
    private final PendencyRepository pendencyRepository;
    private final FileStorageGateway fileStorage;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final DebtConfessionService debtConfessionService;
    private final ChecklistService checklistService;

    public DocumentResource(
        DriverDocumentRepository documentRepository,
        DriverRepository driverRepository,
        CarRepository carRepository,
        DriverCarRepository driverCarRepository,
        PendencyRepository pendencyRepository,
        FileStorageGateway fileStorage,
        UserService userService,
        ObjectMapper objectMapper,
        DebtConfessionService debtConfessionService,
        ChecklistService checklistService
    ) {
        this.debtConfessionService = debtConfessionService;
        this.checklistService = checklistService;
        this.documentRepository = documentRepository;
        this.driverRepository = driverRepository;
        this.carRepository = carRepository;
        this.driverCarRepository = driverCarRepository;
        this.pendencyRepository = pendencyRepository;
        this.fileStorage = fileStorage;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<DocumentDTO> createDocument(@RequestBody DocumentSaveDTO payload) throws URISyntaxException {
        validateCreatePayload(payload);

        Driver driver = getCurrentDriverOrThrow(payload.getDriverId());
        Car car = resolveCurrentCar(payload.getCarId());
        com.localuz.service.VehicleLifecycleService.requireOperational(car);
        validateTypeBinding(payload.getType(), driver.getId(), car == null ? null : car.getId());

        Map<String, Object> storedPayload = payload.getPayload();
        if (DebtConfessionService.hasOrigin(payload.getPayload())) {
            // Confession originated from pendencies: the server rebuilds the payload from the database.
            if (payload.getType() != DocumentType.CONFISSAO_DIVIDA) {
                throw new BadRequestAlertException("Origem permitida somente na confissão de dívida.", ENTITY_NAME, "confessionorigininvalid");
            }
            if (payload.getStatus() != null && payload.getStatus() != DocumentStatus.DRAFT) {
                throw new BadRequestAlertException("A confissão deve ser criada como rascunho.", ENTITY_NAME, "confessionmustbedraft");
            }
            storedPayload =
                debtConfessionService.confirmPendencyOrigin(driver.getId(), car == null ? null : car.getId(), payload.getPayload(), true);
        } else {
            rejectPendencyReferencesWithoutOrigin(payload.getPayload());
        }
        rejectChargeDocumentType(payload.getType());
        boolean checklist = payload.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST;
        if (checklist && payload.getStatus() != null && payload.getStatus() != DocumentStatus.DRAFT) {
            // A checklist only becomes FINAL through /finalize, which performs the delivery / return.
            throw checklistFinalizeRequired();
        }

        DriverDocument document = new DriverDocument();
        document.setType(payload.getType());
        document.setDriver(driver);
        document.setCar(car);
        document.setUser(getCurrentUserOrThrow());
        document.setStatus(payload.getStatus() == null ? DocumentStatus.DRAFT : payload.getStatus());
        if (checklist) {
            // New checklists are structured: their type and contract are columns, validated against driver and car.
            if (storedPayload == null) {
                storedPayload = new LinkedHashMap<>();
            }
            checklistService.bindDraft(document, payload.getChecklistType(), payload.getDriverCarId(), storedPayload);
        }
        document.setPayloadJson(writePayload(storedPayload));
        // attachments and pdfUrl are never taken from clients: attachments only come from the upload endpoint
        // (server-generated keys) and PDFs are generated in the browser, never stored.

        DriverDocument result = documentRepository.save(document);
        DocumentDTO response = toDto(result, true);

        return ResponseEntity
            .created(new URI("/api/documents/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(response);
    }

    @GetMapping
    public List<DocumentDTO> getDocuments(
        @RequestParam(name = "driverId", required = false) Long driverId,
        @RequestParam(name = "carId", required = false) Long carId,
        @RequestParam(name = "type", required = false) DocumentType type,
        @RequestParam(name = "status", required = false) DocumentStatus status,
        @RequestParam(name = "limit", required = false) Integer limit,
        @RequestParam(name = "page", required = false) Integer page
    ) {
        int effectiveLimit = normalizeLimit(limit);
        int effectivePage = normalizePage(page);
        List<DriverDocument> documents = documentRepository.findByCurrentUserWithFilters(
            driverId,
            carId,
            type,
            status,
            PageRequest.of(effectivePage, effectiveLimit)
        );
        List<DocumentDTO> response = new ArrayList<>();
        for (DriverDocument document : documents) {
            response.add(toDto(document, false));
        }
        return response;
    }

    @GetMapping("/{id}")
    public DocumentDTO getDocumentById(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        return toDto(document, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        if (document.getChecklistType() != null && document.getStatus() != DocumentStatus.DRAFT) {
            // It delivered / returned a contract and generated an inspection: the record of that operation stays.
            throw new BadRequestAlertException(
                "Checklist finalizado não pode ser excluído: ele efetivou a entrega/devolução e gerou a inspeção.",
                ENTITY_NAME,
                "checklistfinalcannotbedeleted"
            );
        }
        List<String> attachments = readAttachments(document.getAttachmentsJson());
        documentRepository.delete(document);
        deleteStoredAttachments(document.getId(), attachments);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DocumentDTO> updateDocumentDraft(@PathVariable Long id, @RequestBody DocumentSaveDTO payload) {
        DriverDocument document = getDocumentOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new BadRequestAlertException("Only DRAFT documents can be edited", ENTITY_NAME, "documentnotdraft");
        }

        if (DebtConfessionService.isPendencyOrigin(readPayload(document.getPayloadJson()))) {
            return ResponseEntity.ok(toDto(documentRepository.save(updatePendencyOriginDraft(document, payload)), true));
        }
        if (DebtConfessionService.hasOrigin(payload.getPayload())) {
            // An existing document is never turned into a confession originated from pendencies.
            throw new BadRequestAlertException("A origem da confissão não pode ser alterada.", ENTITY_NAME, "confessionoriginimmutable");
        }
        rejectPendencyReferencesWithoutOrigin(payload.getPayload());
        boolean checklist =
            document.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST || payload.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST;
        if (checklist && payload.getStatus() != null && payload.getStatus() != DocumentStatus.DRAFT) {
            throw checklistFinalizeRequired();
        }
        if (document.getChecklistType() != null && payload.getType() != null && payload.getType() != document.getType()) {
            throw new BadRequestAlertException("O tipo do checklist não pode ser alterado: crie um novo checklist.", ENTITY_NAME, "checklisttypeimmutable");
        }

        if (payload.getType() != null && payload.getType() != document.getType()) {
            // A draft is never turned into a new fine / shared maintenance (those are born in Pendências).
            rejectChargeDocumentType(payload.getType());
        }
        if (payload.getType() != null) {
            document.setType(payload.getType());
        }
        if (payload.getDriverId() != null) {
            document.setDriver(getCurrentDriverOrThrow(payload.getDriverId()));
        }
        if (payload.getCarId() != null) {
            Car replacement = resolveCurrentCar(payload.getCarId());
            com.localuz.service.VehicleLifecycleService.requireOperational(replacement);
            document.setCar(replacement);
        }
        validateTypeBinding(
            document.getType(),
            document.getDriver() == null ? null : document.getDriver().getId(),
            document.getCar() == null ? null : document.getCar().getId()
        );

        if (payload.getStatus() != null) {
            document.setStatus(payload.getStatus());
        }
        if (payload.getPayload() != null) {
            document.setPayloadJson(writePayload(payload.getPayload()));
        }
        if (
            document.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST &&
            (document.getChecklistType() != null || payload.getChecklistType() != null)
        ) {
            // Structured checklist: the binding is checked again against the (possibly edited) driver and car.
            Map<String, Object> stored = readPayload(document.getPayloadJson());
            Map<String, Object> editable = stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
            checklistService.bindDraft(document, payload.getChecklistType(), payload.getDriverCarId(), editable);
            document.setPayloadJson(writePayload(editable));
        }

        DriverDocument result = documentRepository.save(document);
        return ResponseEntity.ok(toDto(result, true));
    }

    public ResponseEntity<DocumentDTO> finalizeDocument(Long id) {
        return finalizeDocument(id, null);
    }

    /**
     * READ COMMITTED: after each lock (document, car, driver, contract) every read sees the latest committed state, so
     * concurrent deliveries / returns / assignments of the same contract or car are decided on fresh data.
     */
    @PostMapping("/{id}/finalize")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<DocumentDTO> finalizeDocument(
        @PathVariable Long id,
        @RequestParam(name = "assignment", required = false) DriverAssignmentType assignment
    ) {
        // Row lock until commit: a double click, a retry or two tabs finalize one after the other, and the second
        // one reads FINAL here (the lock read is always the latest committed row), so it never creates a pendency.
        DriverDocument document = getDocumentForUpdateOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        DocumentStatus previousStatus = document.getStatus();
        if (previousStatus == DocumentStatus.FINAL || previousStatus == DocumentStatus.SENT) {
            // Idempotent retry: an already finalized (or sent) document is returned as it is - never moved back to FINAL,
            // never processed again (no pendency, contract or inspection).
            return ResponseEntity.ok(toDto(document, true));
        }
        if (previousStatus == DocumentStatus.CANCELED) {
            // A canceled document is history: it is never finalized (nor processed: pendency, contract, inspection).
            throw new BadRequestAlertException("Um documento cancelado não pode ser finalizado.", ENTITY_NAME, "documentcanceled");
        }
        Map<String, Object> storedPayload = readPayload(document.getPayloadJson());
        boolean pendencyOrigin = DebtConfessionService.isPendencyOrigin(storedPayload);
        if (pendencyOrigin && previousStatus != DocumentStatus.FINAL && previousStatus != DocumentStatus.SENT) {
            // Validated before anything changes: a rejected finalization leaves the document untouched.
            debtConfessionService.revalidateForFinalize(
                document.getDriver() == null ? null : document.getDriver().getId(),
                document.getCar() == null ? null : document.getCar().getId(),
                storedPayload
            );
        }
        ChecklistService.Outcome checklistOutcome = null;
        if (document.getChecklistType() != null && previousStatus == DocumentStatus.DRAFT) {
            // Same transaction: contract, inspection and contacts are rolled back with the document if anything fails.
            checklistOutcome = checklistService.finalizeChecklist(document, storedPayload, assignment);
        }
        document.setStatus(DocumentStatus.FINAL);
        DriverDocument result;
        if (checklistOutcome != null) {
            try {
                result = documentRepository.saveAndFlush(document);
            } catch (DataIntegrityViolationException e) {
                // The UNIQUE (driver_car_id, final_checklist_slot) lost a race: one FINAL checklist per contract and type.
                throw new BadRequestAlertException(
                    "Este vínculo já possui um checklist finalizado deste tipo.",
                    ENTITY_NAME,
                    "checklistalreadyfinalized"
                );
            }
        } else {
            result = documentRepository.save(document);
        }

        // A document issued from a pendency is only its notification: it never creates another pendency.
        if (previousStatus == DocumentStatus.DRAFT && !pendencyOrigin && !isIssuedFromPendency(result, storedPayload)) {
            createPendencyIfApplicable(result);
        }

        DocumentDTO response = toDto(result, true);
        if (checklistOutcome != null) {
            response.setInspectionId(checklistOutcome.getInspectionId());
            response.setReserveReturn(checklistOutcome.getReserveReturn());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/generate-pdf")
    public ResponseEntity<DocumentDTO> markDocumentPdfGenerated(
        @PathVariable Long id,
        @RequestBody(required = false) DocumentGeneratePdfDTO payload
    ) {
        DriverDocument document = getDocumentOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        // The PDF is generated in the browser and never stored: a pdfUrl sent by the client is ignored.
        return ResponseEntity.ok(toDto(document, true));
    }

    @PostMapping("/{id}/mark-sent")
    public ResponseEntity<DocumentDTO> markDocumentAsSent(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        if (document.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST && document.getStatus() == DocumentStatus.DRAFT) {
            throw checklistFinalizeRequired();
        }
        document.setStatus(DocumentStatus.SENT);
        DriverDocument result = documentRepository.save(document);
        return ResponseEntity.ok(toDto(result, true));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDTO> uploadDocumentAttachments(@PathVariable Long id, @RequestParam("file") MultipartFile[] files) {
        DriverDocument document = getDocumentOrThrow(id);
        com.localuz.service.VehicleLifecycleService.requireOperational(document.getCar());
        List<String> attachments = new ArrayList<>(readAttachments(document.getAttachmentsJson()));
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String key = fileStorage.store(StorageCategory.DOCUMENT, file, "DOC_" + document.getId());
                if (key != null && !key.trim().isEmpty()) {
                    fileStorage.deleteOnRollback(key);
                    attachments.add(key);
                }
            }
        }
        document.setAttachmentsJson(writeAttachments(attachments));
        DriverDocument result = documentRepository.save(document);
        return ResponseEntity.ok(toDto(result, true));
    }

    private DocumentDTO toDto(DriverDocument document, boolean includeContent) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setType(document.getType());
        dto.setStatus(document.getStatus());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());

        Driver driver = document.getDriver();
        if (driver != null) {
            dto.setDriverId(driver.getId());
            dto.setDriverName(driver.getName());
            dto.setDriverCpf(driver.getCpf());
        }

        Car car = document.getCar();
        if (car != null) {
            dto.setCarId(car.getId());
            dto.setCarPlate(car.getPlate());
            dto.setCarModel(car.getModel());
        }

        dto.setPdfUrl(document.getPdfUrl());
        dto.setOriginPendencyId(document.getOriginPendencyId());
        dto.setChecklistType(document.getChecklistType());
        dto.setDriverCarId(document.getDriverCarId());
        if (includeContent && document.getType() == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST) {
            dto.setInspectionId(checklistService.inspectionIdOf(document.getId()).orElse(null));
        }

        if (includeContent) {
            Map<String, Object> payload = readPayload(document.getPayloadJson());
            List<String> attachments = readAttachments(document.getAttachmentsJson());
            dto.setPayload(payload);
            dto.setAttachments(attachments);
            dto.setAttachmentUrls(attachmentUrls(document.getId(), attachments, payload));
        }

        return dto;
    }

    private DriverDocument getDocumentOrThrow(Long id) {
        return documentRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found for current user"));
    }

    private DriverDocument getDocumentForUpdateOrThrow(Long id) {
        // Ownership first without loading the entity, so the locking read below loads it fresh from the database.
        if (!documentRepository.existsByCurrentUserAndId(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found for current user");
        }
        return documentRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found for current user"));
    }

    /** Structural: the origin column set by the server, or an origin block of type PENDENCIA. Never by text. */
    private static boolean isIssuedFromPendency(DriverDocument document, Map<String, Object> payload) {
        if (document.getOriginPendencyId() != null) {
            return true;
        }
        Object origin = payload == null ? null : payload.get(DebtConfessionService.ORIGIN_KEY);
        return origin instanceof Map && "PENDENCIA".equals(((Map<?, ?>) origin).get("tipo"));
    }

    private static BadRequestAlertException checklistFinalizeRequired() {
        return new BadRequestAlertException(
            "Use a finalização do checklist: ela efetiva a entrega/devolução e gera a inspeção.",
            ENTITY_NAME,
            "checklistfinalizerequired"
        );
    }

    private User getCurrentUserOrThrow() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
    }

    private Driver getCurrentDriverOrThrow(Long id) {
        if (id == null) {
            throw new BadRequestAlertException("Driver is required", ENTITY_NAME, "driverrequired");
        }
        return driverRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found for current user"));
    }

    private Car resolveCurrentCar(Long id) {
        if (id == null) {
            return null;
        }
        return carRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found for current user"));
    }

    private void validateCreatePayload(DocumentSaveDTO payload) {
        if (payload == null) {
            throw new BadRequestAlertException("Payload is required", ENTITY_NAME, "payloadrequired");
        }
        if (payload.getType() == null) {
            throw new BadRequestAlertException("Document type is required", ENTITY_NAME, "typerequired");
        }
        if (payload.getDriverId() == null) {
            throw new BadRequestAlertException("Driver is required", ENTITY_NAME, "driverrequired");
        }
    }

    private void validateTypeBinding(DocumentType type, Long driverId, Long carId) {
        if (type == null) {
            throw new BadRequestAlertException("Document type is required", ENTITY_NAME, "typerequired");
        }
        if (driverId == null) {
            throw new BadRequestAlertException("Driver is required", ENTITY_NAME, "driverrequired");
        }
        boolean requiresCar =
            type == DocumentType.MULTA ||
            type == DocumentType.MANUTENCAO_COMPARTILHADA ||
            type == DocumentType.RECIBO_ALUGUEL ||
            type == DocumentType.ENTREGA_DEVOLUCAO_CHECKLIST;
        if (requiresCar && carId == null) {
            throw new BadRequestAlertException("Car is required for this document type", ENTITY_NAME, "carrequired");
        }
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid payload_json", ENTITY_NAME, "invalidpayloadjson");
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception _error) {
            return Collections.emptyMap();
        }
    }

    private String writeAttachments(List<String> attachments) {
        if (attachments == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid attachments_json", ENTITY_NAME, "invalidattachmentsjson");
        }
    }

    private List<String> readAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(attachmentsJson, new TypeReference<List<String>>() {});
        } catch (Exception _error) {
            return new ArrayList<>();
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int normalizeLimit(Integer limit) {
        int defaulted = limit == null ? DEFAULT_LIST_LIMIT : limit;
        if (defaulted < 1) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(defaulted, MAX_LIST_LIMIT);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    /**
     * PATCH of a draft confession originated from pendencies: driver, car, contract, pendencies and origin never
     * change; a new payload is accepted only for the same selection, revalidated and rebuilt by the server.
     */
    private DriverDocument updatePendencyOriginDraft(DriverDocument document, DocumentSaveDTO patch) {
        Long driverId = document.getDriver() == null ? null : document.getDriver().getId();
        Long carId = document.getCar() == null ? null : document.getCar().getId();
        boolean changesContext =
            (patch.getType() != null && patch.getType() != document.getType()) ||
            (patch.getDriverId() != null && !patch.getDriverId().equals(driverId)) ||
            (patch.getCarId() != null && !patch.getCarId().equals(carId));
        if (changesContext) {
            throw new BadRequestAlertException("A origem da confissão não pode ser alterada.", ENTITY_NAME, "confessionoriginimmutable");
        }
        if (patch.getStatus() != null && patch.getStatus() != DocumentStatus.DRAFT) {
            // Only /finalize revalidates the pendencies: a status change here would skip it.
            throw new BadRequestAlertException("Use a finalização para concluir a confissão.", ENTITY_NAME, "confessionfinalizerequired");
        }
        if (patch.getPayload() != null) {
            Map<String, Object> stored = readPayload(document.getPayloadJson());
            if (!DebtConfessionService.sameOriginSelection(stored, patch.getPayload())) {
                throw new BadRequestAlertException("A origem da confissão não pode ser alterada.", ENTITY_NAME, "confessionoriginimmutable");
            }
            // A draft with payment terms keeps them; only drafts saved before the terms may go without.
            boolean termsRequired = DebtConfessionService.hasPaymentTerms(stored);
            document.setPayloadJson(
                writePayload(debtConfessionService.confirmPendencyOrigin(driverId, carId, patch.getPayload(), termsRequired))
            );
        }
        return document;
    }

    /** A manual confession (no origin) can never claim to come from pendencies. */
    private void rejectPendencyReferencesWithoutOrigin(Map<String, Object> payload) {
        if (DebtConfessionService.referencesPendencies(payload)) {
            throw new BadRequestAlertException(
                "Itens vinculados a pendências exigem a origem PENDENCIAS.",
                ENTITY_NAME,
                "sourcependencywithoutorigin"
            );
        }
    }

    /**
     * Fines and shared maintenance are created in Pendências (the debt) and their documents are issued from there
     * (DriverChargeService, origin_pendency_id). A new manual document of these types would be a second entry point
     * for the same debt, so it is refused here; existing documents and drafts stay readable and usable.
     */
    private static void rejectChargeDocumentType(DocumentType type) {
        if (type == DocumentType.MULTA || type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            throw new BadRequestAlertException(
                "Multas e manutenções compartilhadas são registradas em Pendências; o documento é emitido a partir da pendência.",
                ENTITY_NAME,
                "documenttypemovedtopendencies"
            );
        }
    }

    private void createPendencyIfApplicable(DriverDocument document) {
        if (document == null || document.getType() == null || document.getDriver() == null) {
            return;
        }
        if (document.getId() != null && pendencyRepository.existsByOriginDocumentId(document.getId())) {
            // Structural: this document already produced its debt (one per document, unique in the database).
            return;
        }
        if (DebtConfessionService.isPendencyOrigin(readPayload(document.getPayloadJson()))) {
            // The selected pendencies stay the only financial record of the debt: never a consolidated copy.
            return;
        }

        if (
            document.getType() != DocumentType.MULTA &&
            document.getType() != DocumentType.MANUTENCAO_COMPARTILHADA &&
            document.getType() != DocumentType.CONFISSAO_DIVIDA
        ) {
            return;
        }

        if (document.getCar() == null) {
            return;
        }

        DriverCar driverCar = resolveDriverCarForPendency(document.getDriver().getId(), document.getCar().getId());
        if (driverCar == null) {
            return;
        }

        Map<String, Object> payload = readPayload(document.getPayloadJson());
        BigDecimal amount = resolvePendencyAmount(document.getType(), payload);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Pendency pendency = new Pendency();
        pendency.setDriverCar(driverCar);
        pendency.setDebtor(driverCar.getDriver());
        // Where it came from (one pendency per document, unique in the database) and what kind of debt it is.
        pendency.setOriginDocumentId(document.getId());
        pendency.setOriginType(originTypeOf(document.getType()));
        pendency.setName(truncate(resolvePendencyName(document.getType(), payload), 60));
        pendency.setCost(amount);
        pendency.setDate(resolvePendencyDate(document.getType(), payload));
        pendency.setNote(truncate(resolvePendencyNote(document.getType(), payload), 255));
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setPaidAt(null);
        pendency.setPaidAmount(BigDecimal.ZERO);
        pendency.setRemainingAmount(amount);
        pendency.setPaymentMethod(null);
        pendencyRepository.save(pendency);
    }

    private static PendencyOriginType originTypeOf(DocumentType type) {
        if (type == DocumentType.MULTA) {
            return PendencyOriginType.FINE;
        }
        if (type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            return PendencyOriginType.SHARED_MAINTENANCE;
        }
        // A manual Confissão consolidates debts of any kind: its origin is the document, its kind is unknown.
        return null;
    }

    private DriverCar resolveDriverCarForPendency(Long driverId, Long carId) {
        List<DriverCar> activeList = driverCarRepository.findActiveByCurrentUserAndDriverAndCar(driverId, carId);
        if (activeList != null && !activeList.isEmpty()) {
            return activeList.get(0);
        }
        List<DriverCar> allList = driverCarRepository.findByCurrentUserAndDriverAndCarOrderByStartDateDesc(driverId, carId);
        if (allList == null || allList.isEmpty()) {
            return null;
        }
        return allList.get(0);
    }

    private BigDecimal resolvePendencyAmount(DocumentType type, Map<String, Object> payload) {
        if (payload == null) {
            return BigDecimal.ZERO;
        }
        if (type == DocumentType.MULTA) {
            return toBigDecimal(payload.get("valor"));
        }
        if (type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            return toBigDecimal(payload.get("parteMotoristaValor"));
        }
        if (type == DocumentType.CONFISSAO_DIVIDA) {
            BigDecimal total = toBigDecimal(payload.get("valorTotal"));
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                return total;
            }
            return sumConfissaoItens(payload);
        }
        return BigDecimal.ZERO;
    }

    private String resolvePendencyName(DocumentType type, Map<String, Object> payload) {
        if (type == DocumentType.MULTA) {
            String ait = toText(payload.get("ait"));
            String orgao = toText(payload.get("orgao"));
            if (!ait.isEmpty()) {
                return "Multa AIT " + ait;
            }
            if (!orgao.isEmpty()) {
                return "Multa " + orgao;
            }
            return "Multa de trânsito";
        }
        if (type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            String descricao = toText(payload.get("descricao"));
            if (!descricao.isEmpty()) {
                return "Rateio manutenção: " + descricao;
            }
            return "Rateio de manutenção";
        }
        if (type == DocumentType.CONFISSAO_DIVIDA) {
            return "Confissão de dívida";
        }
        return "Documento financeiro";
    }

    private LocalDate resolvePendencyDate(DocumentType type, Map<String, Object> payload) {
        if (payload == null) {
            return LocalDate.now();
        }
        String dateValue = "";
        if (type == DocumentType.MULTA) {
            dateValue = toText(payload.get("dataHora"));
        } else if (type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            dateValue = toText(payload.get("data"));
        } else if (type == DocumentType.CONFISSAO_DIVIDA) {
            dateValue = toText(payload.get("vencimentoInicial"));
        }
        LocalDate parsed = parseDate(dateValue);
        return parsed == null ? LocalDate.now() : parsed;
    }

    private String resolvePendencyNote(DocumentType type, Map<String, Object> payload) {
        String baseNote = toText(payload.get("observacoes"));
        if (type == DocumentType.MULTA) {
            String local = toText(payload.get("local"));
            String enquadramento = toText(payload.get("enquadramento"));
            String responsavel = toText(payload.get("responsavelPagamento"));
            return String.format(
                Locale.ROOT,
                "Local: %s | Enquadramento: %s | Responsável: %s | Obs: %s",
                local,
                enquadramento,
                responsavel,
                baseNote
            );
        }
        if (type == DocumentType.MANUTENCAO_COMPARTILHADA) {
            String oficina = toText(payload.get("oficina"));
            return String.format(Locale.ROOT, "Oficina: %s | Obs: %s", oficina, baseNote);
        }
        if (type == DocumentType.CONFISSAO_DIVIDA) {
            String origem = toText(payload.get("origemDaDivida"));
            return String.format(Locale.ROOT, "Origem: %s | Obs: %s", origem, baseNote);
        }
        return baseNote;
    }

    private String truncate(String value, int max) {
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return BigDecimal.ZERO;
            }
            String cleaned = text.replaceAll("[^0-9,.-]", "");
            if (cleaned.isEmpty()) {
                return BigDecimal.ZERO;
            }

            int lastComma = cleaned.lastIndexOf(',');
            int lastDot = cleaned.lastIndexOf('.');
            int decimalSeparatorIndex = Math.max(lastComma, lastDot);
            boolean hasBothSeparators = lastComma >= 0 && lastDot >= 0;
            String normalized;

            if (decimalSeparatorIndex >= 0) {
                int fractionLength = cleaned.length() - decimalSeparatorIndex - 1;
                boolean useDecimalSeparator = hasBothSeparators || fractionLength <= 2;
                if (useDecimalSeparator) {
                    String integerPart = cleaned.substring(0, decimalSeparatorIndex).replaceAll("[^0-9-]", "");
                    String fractionPart = cleaned.substring(decimalSeparatorIndex + 1).replaceAll("[^0-9]", "");
                    normalized = fractionPart.isEmpty() ? integerPart : integerPart + "." + fractionPart;
                } else {
                    normalized = cleaned.replaceAll("[^0-9-]", "");
                }
            } else {
                normalized = cleaned.replaceAll("[^0-9-]", "");
            }

            if (normalized.isEmpty() || "-".equals(normalized)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(normalized);
        } catch (Exception _error) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal sumConfissaoItens(Map<String, Object> payload) {
        if (payload == null) {
            return BigDecimal.ZERO;
        }
        Object rawItens = payload.get("itensDaDivida");
        if (!(rawItens instanceof List<?>)) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Object rawItem : (List<?>) rawItens) {
            if (!(rawItem instanceof Map<?, ?>)) {
                continue;
            }
            Object valorItem = ((Map<?, ?>) rawItem).get("valorItem");
            total = total.add(toBigDecimal(valorItem));
        }
        return total;
    }

    /**
     * Resolved URL for each attachment and each checklist photo reference of the payload. References that are not
     * files of this document (arbitrary client strings) never get a URL: "" in local mode (no image), omitted in s3
     * mode (the client keeps its legacy resolution, unchanged behavior).
     */
    private Map<String, String> attachmentUrls(Long documentId, List<String> attachments, Map<String, Object> payload) {
        Set<String> references = new LinkedHashSet<>(attachments);
        for (String key : CHECKLIST_PHOTO_REF_KEYS) {
            Object value = payload == null ? null : payload.get(key);
            if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    if (item instanceof String && !((String) item).isBlank()) {
                        references.add((String) item);
                    }
                }
            }
        }
        Map<String, String> urls = new LinkedHashMap<>();
        for (String reference : references) {
            // Rows written before Etapa 5 could hold client-supplied keys of other records: only this document's own
            // legacy objects and server-generated keys listed in its attachments are files of this document.
            boolean ownFile = ownedLegacyKey(documentId, reference) != null || (attachments.contains(reference) && isServerGeneratedKey(reference));
            String url = ownFile ? fileStorage.displayUrl(reference) : (fileStorage.isLocalMode() ? "" : null);
            if (url != null) {
                urls.put(reference, url);
            }
        }
        return urls;
    }

    /**
     * Local mode: files are deleted only after the deletion commits; historical keys and anything that is not a key
     * are never deleted. s3 mode: only this document's own legacy objects ({millis}_DOC_{id}...) are deleted, as
     * before failures are ignored; references that point elsewhere are never deleted.
     */
    private void deleteStoredAttachments(Long documentId, List<String> attachments) {
        for (String attachment : attachments) {
            if (fileStorage.isLocalMode()) {
                fileStorage.deleteAfterCommit(attachment);
                continue;
            }
            String key = ownedLegacyKey(documentId, attachment);
            if (key == null) {
                log.warn("Document attachment not deleted: not an object of document {}", documentId);
                continue;
            }
            try {
                fileStorage.deleteFromLegacyS3(key);
            } catch (Exception e) {
                log.warn("Could not delete document file: {}", key, e);
            }
        }
    }

    /** The legacy S3 key of an attachment uploaded for this document (plain key or URL), or null. */
    static String ownedLegacyKey(Long documentId, String reference) {
        String value = normalizeReference(reference);
        if (value == null || documentId == null) {
            return null;
        }
        int queryIndex = value.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        String key = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        return Pattern.matches("[0-9]{13}_DOC_" + documentId + "([._].*)?", key) ? key : null;
    }

    private static boolean isServerGeneratedKey(String reference) {
        return StorageKeys.parse(reference).map(key -> !key.isLegacy()).orElse(false);
    }

    private static String normalizeReference(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(String dateValue) {
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateValue);
        } catch (DateTimeParseException _ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(dateValue).toLocalDate();
        } catch (DateTimeParseException _ignored) {
            // continue
        }
        try {
            return OffsetDateTime.parse(dateValue).toLocalDate();
        } catch (DateTimeParseException _ignored) {
            return null;
        }
    }
}
