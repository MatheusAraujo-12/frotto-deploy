package com.localuz.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Pendency;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.AWSS3FileService;
import com.localuz.service.UserService;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api")
@Transactional
public class DocumentResource {

    private final Logger log = LoggerFactory.getLogger(DocumentResource.class);

    private static final String ENTITY_NAME = "document";
    private static final int DEFAULT_LIST_LIMIT = 30;
    private static final int MAX_LIST_LIMIT = 100;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DriverDocumentRepository documentRepository;
    private final DriverRepository driverRepository;
    private final CarRepository carRepository;
    private final DriverCarRepository driverCarRepository;
    private final PendencyRepository pendencyRepository;
    private final AWSS3FileService awsS3FileService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public DocumentResource(
        DriverDocumentRepository documentRepository,
        DriverRepository driverRepository,
        CarRepository carRepository,
        DriverCarRepository driverCarRepository,
        PendencyRepository pendencyRepository,
        AWSS3FileService awsS3FileService,
        UserService userService,
        ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.driverRepository = driverRepository;
        this.carRepository = carRepository;
        this.driverCarRepository = driverCarRepository;
        this.pendencyRepository = pendencyRepository;
        this.awsS3FileService = awsS3FileService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/documents")
    public ResponseEntity<DocumentDTO> createDocument(@RequestBody DocumentSaveDTO payload) throws URISyntaxException {
        validateCreatePayload(payload);

        Driver driver = getCurrentDriverOrThrow(payload.getDriverId());
        Car car = resolveCurrentCar(payload.getCarId());
        validateTypeBinding(payload.getType(), driver.getId(), car == null ? null : car.getId());

        DriverDocument document = new DriverDocument();
        document.setType(payload.getType());
        document.setDriver(driver);
        document.setCar(car);
        document.setUser(getCurrentUserOrThrow());
        document.setStatus(payload.getStatus() == null ? DocumentStatus.DRAFT : payload.getStatus());
        document.setPayloadJson(writePayload(payload.getPayload()));
        document.setAttachmentsJson(writeAttachments(payload.getAttachments()));
        document.setPdfUrl(normalizeText(payload.getPdfUrl()));

        DriverDocument result = documentRepository.save(document);
        DocumentDTO response = toDto(result, true);

        return ResponseEntity
            .created(new URI("/api/documents/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(response);
    }

    @GetMapping("/documents")
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

    @GetMapping("/documents/{id}")
    public DocumentDTO getDocumentById(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        return toDto(document, true);
    }

    @PatchMapping("/documents/{id}")
    public ResponseEntity<DocumentDTO> updateDocumentDraft(@PathVariable Long id, @RequestBody DocumentSaveDTO payload) {
        DriverDocument document = getDocumentOrThrow(id);
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new BadRequestAlertException("Only DRAFT documents can be edited", ENTITY_NAME, "documentnotdraft");
        }

        if (payload.getType() != null) {
            document.setType(payload.getType());
        }
        if (payload.getDriverId() != null) {
            document.setDriver(getCurrentDriverOrThrow(payload.getDriverId()));
        }
        if (payload.getCarId() != null) {
            document.setCar(resolveCurrentCar(payload.getCarId()));
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
        if (payload.getAttachments() != null) {
            document.setAttachmentsJson(writeAttachments(payload.getAttachments()));
        }
        if (payload.getPdfUrl() != null) {
            document.setPdfUrl(normalizeText(payload.getPdfUrl()));
        }

        DriverDocument result = documentRepository.save(document);
        return ResponseEntity.ok(toDto(result, true));
    }

    @PostMapping("/documents/{id}/finalize")
    public ResponseEntity<DocumentDTO> finalizeDocument(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        DocumentStatus previousStatus = document.getStatus();
        document.setStatus(DocumentStatus.FINAL);
        DriverDocument result = documentRepository.save(document);

        if (previousStatus == DocumentStatus.DRAFT) {
            createPendencyIfApplicable(result);
        }

        return ResponseEntity.ok(toDto(result, true));
    }

    @PostMapping("/documents/{id}/generate-pdf")
    public ResponseEntity<DocumentDTO> markDocumentPdfGenerated(
        @PathVariable Long id,
        @RequestBody(required = false) DocumentGeneratePdfDTO payload
    ) {
        DriverDocument document = getDocumentOrThrow(id);
        if (payload != null && payload.getPdfUrl() != null) {
            document.setPdfUrl(normalizeText(payload.getPdfUrl()));
            document = documentRepository.save(document);
        }
        return ResponseEntity.ok(toDto(document, true));
    }

    @PostMapping("/documents/{id}/mark-sent")
    public ResponseEntity<DocumentDTO> markDocumentAsSent(@PathVariable Long id) {
        DriverDocument document = getDocumentOrThrow(id);
        document.setStatus(DocumentStatus.SENT);
        DriverDocument result = documentRepository.save(document);
        return ResponseEntity.ok(toDto(result, true));
    }

    @PostMapping(value = "/documents/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDTO> uploadDocumentAttachments(@PathVariable Long id, @RequestParam("file") MultipartFile[] files) {
        DriverDocument document = getDocumentOrThrow(id);
        List<String> attachments = new ArrayList<>(readAttachments(document.getAttachmentsJson()));
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String fileName = awsS3FileService.uploadFile(file, "DOC_" + document.getId());
                if (fileName != null && !fileName.trim().isEmpty()) {
                    attachments.add(fileName);
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

        if (includeContent) {
            dto.setPayload(readPayload(document.getPayloadJson()));
            dto.setAttachments(readAttachments(document.getAttachmentsJson()));
        }

        return dto;
    }

    private DriverDocument getDocumentOrThrow(Long id) {
        return documentRepository
            .findByCurrentUserAndId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found for current user"));
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

    private void createPendencyIfApplicable(DriverDocument document) {
        if (document == null || document.getType() == null || document.getDriver() == null) {
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
            return toBigDecimal(payload.get("valorTotal"));
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
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(text);
        } catch (Exception _error) {
            return BigDecimal.ZERO;
        }
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
