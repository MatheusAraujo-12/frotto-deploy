package com.localuz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Maintenance;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.DocumentStatus;
import com.localuz.domain.enumeration.DocumentType;
import com.localuz.domain.enumeration.PendencyOriginType;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.MaintenanceRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.dto.FineChargeRequest;
import com.localuz.service.dto.MaintenanceChargeOptionDTO;
import com.localuz.service.dto.MaintenanceChargeSummaryDTO;
import com.localuz.service.dto.SharedMaintenanceChargeRequest;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.IdempotencyConflictException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Debts of the driver born from a traffic fine or from a share of a car maintenance, and the documents issued from
 * them. The pendency is the only financial record of the debt (balance, payments, Confissão de Dívida); the
 * maintenance keeps its full cost and the documents are only notifications. Every creation is idempotent per
 * operation key and safe under concurrency: the database constraints are the last word, the row locks keep the
 * maintenance limit exact.
 */
@Service
public class DriverChargeService {

    public static final String FINE_NAME = "Multa de trânsito";
    public static final String SHARED_MAINTENANCE_NAME = "Manutenção compartilhada";
    static final String ENTITY_NAME = "pendency";
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,80}$");
    /** Local timestamp at noon: read as local time by the browser, so the PDF prints the right day. */
    private static final LocalTime DATE_ONLY_TIME = LocalTime.NOON;

    private final PendencyRepository pendencies;
    private final DriverCarRepository driverCars;
    private final MaintenanceRepository maintenances;
    private final DriverDocumentRepository documents;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public DriverChargeService(
        PendencyRepository pendencies,
        DriverCarRepository driverCars,
        MaintenanceRepository maintenances,
        DriverDocumentRepository documents,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.pendencies = pendencies;
        this.driverCars = driverCars;
        this.maintenances = maintenances;
        this.documents = documents;
        this.objectMapper = objectMapper;
        // Each operation in its own transaction, READ COMMITTED: after a row lock every read sees what the previous
        // holder committed, and a refused insert never poisons the caller's transaction.
        this.write = new TransactionTemplate(transactionManager);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.read = new TransactionTemplate(transactionManager);
        this.read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.read.setReadOnly(true);
    }

    /** Result of an idempotent operation: created now, or the one an earlier attempt of the same operation made. */
    public static final class Outcome<T> {

        private final T value;
        private final boolean created;

        Outcome(T value, boolean created) {
            this.value = value;
            this.created = created;
        }

        public T getValue() {
            return value;
        }

        public boolean isCreated() {
            return created;
        }
    }

    // ---------------------------------------------------------------- fines

    public Outcome<Pendency> chargeFine(Long driverCarId, FineChargeRequest request) {
        if (request == null) {
            throw invalid("Dados da multa obrigatórios.", "finerequired");
        }
        String key = requireKey(request.getIdempotencyKey());
        BigDecimal amount = requireAmount(request.getAmount());
        // Only the value and the day are required; the time and the other details are optional (never invented).
        if (request.getInfractionDate() == null) {
            throw invalid("Informe a data da infração.", "fineinfractionrequired");
        }
        String ait = optionalText(request.getAit(), 40, "AIT");
        String agency = optionalText(request.getAgency(), 120, "Órgão autuador");
        String location = optionalText(request.getLocation(), 255, "Local");
        String classification = optionalText(request.getClassification(), 255, "Enquadramento");
        String note = optionalText(request.getNote(), 255, "Observação");

        return idempotent(
            key,
            () -> {
                DriverCar contract = requireContract(driverCarId);
                Optional<Pendency> again = pendencies.findByCurrentUserAndIdempotencyKey(key);
                if (again.isPresent()) {
                    return new Outcome<>(again.get(), false);
                }
                Pendency fine = newCharge(contract, PendencyOriginType.FINE, FINE_NAME, amount, request.getInfractionDate(), note, key);
                fine.setFineAit(ait);
                fine.setFineAgency(agency);
                fine.setFineLocation(location);
                fine.setFineClassification(classification);
                fine.setFineInfractionTime(request.getInfractionTime() == null ? null : request.getInfractionTime().withSecond(0).withNano(0));
                fine.setFineDueDate(request.getDueDate());
                return new Outcome<>(pendencies.saveAndFlush(fine), true);
            },
            existing ->
                existing.getOriginType() == PendencyOriginType.FINE &&
                Objects.equals(existing.getDriverCarId(), driverCarId) &&
                sameMoney(existing.getCost(), amount)
        );
    }

    // ---------------------------------------------------------------- shared maintenance

    public Outcome<Pendency> chargeSharedMaintenance(Long driverCarId, SharedMaintenanceChargeRequest request) {
        if (request == null || request.getMaintenanceId() == null) {
            throw invalid("Selecione a manutenção.", "maintenancerequired");
        }
        String key = requireKey(request.getIdempotencyKey());
        BigDecimal amount = requireAmount(request.getAmount());
        Long maintenanceId = request.getMaintenanceId();
        String note = optionalText(request.getNote(), 255, "Motivo");

        return idempotent(
            key,
            () -> {
                DriverCar contract = requireContract(driverCarId);
                if (!maintenances.existsByCurrentUserAndMaintenanceId(maintenanceId)) {
                    throw notFound("Manutenção não encontrada.");
                }
                // Serializes every charge of this maintenance until commit.
                Maintenance maintenance = maintenances.findByIdForUpdate(maintenanceId).orElseThrow(() -> notFound("Manutenção não encontrada."));
                // A retry of this same operation may have committed while we waited for the lock.
                Optional<Pendency> again = pendencies.findByCurrentUserAndIdempotencyKey(key);
                if (again.isPresent()) {
                    return new Outcome<>(again.get(), false);
                }
                if (maintenance.getCar() == null || !Objects.equals(maintenance.getCar().getId(), contract.getCar().getId())) {
                    throw invalid("A manutenção precisa ser do veículo deste contrato.", "maintenancecarmismatch");
                }
                BigDecimal assigned = assignedAmount(pendencies.findByOriginMaintenanceIdForUpdate(maintenanceId), null);
                if (assigned.add(amount).compareTo(money(maintenance.getCost())) > 0) {
                    throw invalid("A soma das cobranças ultrapassaria o custo da manutenção.", "maintenancechargeexceeded");
                }
                LocalDate date = maintenance.getDate() == null ? LocalDate.now() : maintenance.getDate();
                Pendency share = newCharge(contract, PendencyOriginType.SHARED_MAINTENANCE, SHARED_MAINTENANCE_NAME, amount, date, note, key);
                share.setOriginMaintenanceId(maintenanceId);
                return new Outcome<>(pendencies.saveAndFlush(share), true);
            },
            existing ->
                existing.getOriginType() == PendencyOriginType.SHARED_MAINTENANCE &&
                Objects.equals(existing.getDriverCarId(), driverCarId) &&
                Objects.equals(existing.getOriginMaintenanceId(), maintenanceId) &&
                sameMoney(existing.getCost(), amount)
        );
    }

    /**
     * The maintenances a shared-maintenance charge of this contract can come from: those of the contract's car, in
     * the current account, each with its cost, what is already assigned and what is still available (display only:
     * the limit is always enforced under the row lock when charging).
     */
    public List<MaintenanceChargeOptionDTO> chargeableMaintenances(Long driverCarId) {
        return read.execute(status -> {
            DriverCar contract = driverCars.findByCurrentUserAndId(driverCarId).orElseThrow(() -> notFound("Contrato não encontrado."));
            if (contract.getCar() == null) {
                return List.<MaintenanceChargeOptionDTO>of();
            }
            return maintenances
                .findByCurrentUserAndCarIdByDate(contract.getCar().getId())
                .stream()
                .map(maintenance ->
                    new MaintenanceChargeOptionDTO(
                        maintenance.getId(),
                        maintenance.getDate(),
                        maintenance.getLocal(),
                        describe(maintenance),
                        money(maintenance.getCost()),
                        assignedAmount(pendencies.findByOriginMaintenanceId(maintenance.getId()), null)
                    )
                )
                .collect(java.util.stream.Collectors.toList());
        });
    }

    /** Cost of the maintenance and how much of it is already charged to drivers (paid or not). */
    public MaintenanceChargeSummaryDTO maintenanceSummary(Long maintenanceId) {
        return read.execute(status -> {
            if (!maintenances.existsByCurrentUserAndMaintenanceId(maintenanceId)) {
                throw notFound("Manutenção não encontrada.");
            }
            Maintenance maintenance = maintenances.findById(maintenanceId).orElseThrow(() -> notFound("Manutenção não encontrada."));
            List<Pendency> charges = pendencies.findByOriginMaintenanceId(maintenanceId);
            return new MaintenanceChargeSummaryDTO(maintenanceId, money(maintenance.getCost()), assignedAmount(charges, null), charges.size());
        });
    }

    /**
     * Editing the cost of a shared-maintenance charge keeps the maintenance limit (assigned responsibility, the
     * payments never free it). Runs inside the caller's transaction, under the same row locks as a new charge.
     */
    public void requireMaintenanceLimitForNewCost(Pendency existing, BigDecimal newCost) {
        if (
            existing.getOriginType() != PendencyOriginType.SHARED_MAINTENANCE ||
            existing.getOriginMaintenanceId() == null ||
            newCost == null ||
            sameMoney(existing.getCost(), newCost)
        ) {
            return;
        }
        Optional<Maintenance> maintenance = maintenances.findByIdForUpdate(existing.getOriginMaintenanceId());
        if (maintenance.isEmpty()) {
            return;
        }
        BigDecimal others = assignedAmount(pendencies.findByOriginMaintenanceIdForUpdate(existing.getOriginMaintenanceId()), existing.getId());
        if (others.add(money(newCost)).compareTo(money(maintenance.get().getCost())) > 0) {
            throw invalid("A soma das cobranças ultrapassaria o custo da manutenção.", "maintenancechargeexceeded");
        }
    }

    /**
     * Before deleting a maintenance (caller's transaction): refused while any driver charge comes from it. The same
     * row lock as a new charge serializes both, so a charge and a deletion never interleave; the RESTRICT foreign key
     * is the final barrier.
     */
    public void requireNoDriverCharges(Long maintenanceId) {
        if (maintenances.findByIdForUpdate(maintenanceId).isEmpty()) {
            return;
        }
        if (!pendencies.findByOriginMaintenanceIdForUpdate(maintenanceId).isEmpty()) {
            throw new BadRequestAlertException(
                "Esta manutenção possui cobranças vinculadas a motorista e não pode ser excluída.",
                "maintenance",
                "maintenancehasdrivercharges"
            );
        }
    }

    // ---------------------------------------------------------------- documents issued from a pendency

    /**
     * The notification of a fine / the agreement of a shared maintenance, built by the server from the pendency and
     * archived in Documentos as FINAL. One per pendency and type: asking again returns the same document (to print
     * it again), never a new one, and it never creates a pendency.
     */
    public Outcome<DriverDocument> issueDocument(Long pendencyId) {
        try {
            return write.execute(status -> issueLocked(pendencyId));
        } catch (RuntimeException e) {
            if (!isConstraintViolation(e)) {
                throw e;
            }
            Optional<DriverDocument> winner = read.execute(status -> existingDocument(pendencyId));
            return winner.map(document -> new Outcome<>(document, false)).orElseThrow(() -> e);
        }
    }

    private Outcome<DriverDocument> issueLocked(Long pendencyId) {
        if (!pendencies.existsByCurrentUserAndId(pendencyId)) {
            throw notFound("Pendência não encontrada.");
        }
        Pendency pendency = pendencies.findByIdForUpdate(pendencyId).orElseThrow(() -> notFound("Pendência não encontrada."));
        DriverCar contract = pendency.getDriverCar();
        Car car = contract == null ? null : contract.getCar();
        VehicleLifecycleService.requireOperational(car);
        Optional<DriverDocument> existing = existingDocument(pendency);
        if (existing.isPresent()) {
            return new Outcome<>(existing.get(), false);
        }
        DocumentType type = documentTypeOf(pendency);
        Driver debtor = pendency.getDebtor();
        if (type == null || debtor == null || car == null) {
            throw invalid("Esta pendência não tem um documento correspondente.", "pendencydocumentunsupported");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("tipo", "PENDENCIA");
        origin.put("pendencyId", pendency.getId());
        payload.put("origem", origin);
        payload.put("driverName", debtor.getName());
        payload.put("driverCpf", debtor.getCpf());
        payload.put("carPlate", car.getPlate());
        payload.put("carModel", car.getModel());
        // Only what is known goes into the document (no empty keys, no invented time): the PDF omits the rest.
        if (type == DocumentType.MULTA) {
            putIfPresent(payload, "dataInfracao", pendency.getDate() == null ? null : pendency.getDate().toString());
            if (pendency.getFineInfractionTime() != null) {
                payload.put("horaInfracao", pendency.getFineInfractionTime().toString());
                payload.put("dataHora", localTimestamp(pendency.getDate(), pendency.getFineInfractionTime()));
            }
            putIfPresent(payload, "local", pendency.getFineLocation());
            putIfPresent(payload, "ait", pendency.getFineAit());
            putIfPresent(payload, "orgao", pendency.getFineAgency());
            putIfPresent(payload, "enquadramento", pendency.getFineClassification());
            payload.put("valor", money(pendency.getCost()));
            putIfPresent(payload, "vencimento", pendency.getFineDueDate() == null ? null : pendency.getFineDueDate().toString());
            payload.put("responsavelPagamento", debtor.getName());
        } else {
            Optional<Maintenance> maintenance = pendency.getOriginMaintenanceId() == null
                ? Optional.empty()
                : maintenances.findById(pendency.getOriginMaintenanceId());
            LocalDate date = maintenance.map(Maintenance::getDate).orElse(pendency.getDate());
            putIfPresent(payload, "data", date == null ? null : date.toString());
            putIfPresent(payload, "oficina", maintenance.map(Maintenance::getLocal).map(String::trim).filter(text -> !text.isEmpty()).orElse(null));
            putIfPresent(payload, "descricao", maintenance.map(DriverChargeService::describe).orElse(null));
            maintenance.ifPresent(found -> payload.put("valorTotal", money(found.getCost())));
            payload.put("formaDivisao", "Valor atribuído ao motorista");
            payload.put("parteMotoristaValor", money(pendency.getCost()));
        }
        putIfPresent(payload, "observacoes", pendency.getNote());

        DriverDocument document = new DriverDocument();
        document.setType(type);
        document.setStatus(DocumentStatus.FINAL);
        document.setDriver(debtor);
        document.setCar(car);
        document.setUser(car.getUser());
        document.setOriginPendencyId(pendency.getId());
        document.setPayloadJson(writeJson(payload));
        return new Outcome<>(documents.saveAndFlush(document), true);
    }

    private Optional<DriverDocument> existingDocument(Long pendencyId) {
        return pendencies.findById(pendencyId).flatMap(this::existingDocument);
    }

    /** The document of the debt: the legacy one it was born from, or the one already issued from it. */
    private Optional<DriverDocument> existingDocument(Pendency pendency) {
        if (pendency.getOriginDocumentId() != null) {
            Optional<DriverDocument> source = documents.findById(pendency.getOriginDocumentId());
            if (source.isPresent()) {
                return source;
            }
        }
        DocumentType type = documentTypeOf(pendency);
        return type == null ? Optional.empty() : documents.findByOriginPendencyIdAndType(pendency.getId(), type);
    }

    private static DocumentType documentTypeOf(Pendency pendency) {
        if (pendency.getOriginType() == PendencyOriginType.FINE) {
            return DocumentType.MULTA;
        }
        if (pendency.getOriginType() == PendencyOriginType.SHARED_MAINTENANCE) {
            return DocumentType.MANUTENCAO_COMPARTILHADA;
        }
        return null;
    }

    // ---------------------------------------------------------------- idempotency

    private Outcome<Pendency> idempotent(String key, Supplier<Outcome<Pendency>> insert, Predicate<Pendency> sameOperation) {
        Optional<Pendency> previous = read.execute(status -> pendencies.findByCurrentUserAndIdempotencyKey(key));
        if (previous.isPresent()) {
            return replay(previous.get(), sameOperation);
        }
        try {
            Outcome<Pendency> outcome = write.execute(status -> insert.get());
            return outcome.isCreated() ? outcome : replay(outcome.getValue(), sameOperation);
        } catch (RuntimeException e) {
            if (!isConstraintViolation(e)) {
                throw e;
            }
            // Another attempt of the same operation won the unique key: return what it created.
            Optional<Pendency> winner = read.execute(status -> pendencies.findByCurrentUserAndIdempotencyKey(key));
            if (winner.isPresent()) {
                return replay(winner.get(), sameOperation);
            }
            if (Boolean.TRUE.equals(read.execute(status -> pendencies.existsByIdempotencyKey(key)))) {
                throw new IdempotencyConflictException();
            }
            throw e;
        }
    }

    private static Outcome<Pendency> replay(Pendency existing, Predicate<Pendency> sameOperation) {
        if (!sameOperation.test(existing)) {
            throw new IdempotencyConflictException();
        }
        return new Outcome<>(existing, false);
    }

    static boolean isConstraintViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (
                cause instanceof DataIntegrityViolationException ||
                cause instanceof SQLIntegrityConstraintViolationException ||
                cause instanceof org.hibernate.exception.ConstraintViolationException
            ) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private DriverCar requireContract(Long driverCarId) {
        DriverCar contract = driverCars.findByCurrentUserAndId(driverCarId).orElseThrow(() -> notFound("Contrato não encontrado."));
        VehicleLifecycleService.requireOperational(contract.getCar());
        if (contract.getDriver() == null) {
            throw invalid("Este contrato não tem motorista: não há devedor para a cobrança.", "drivercarwithoutdriver");
        }
        return contract;
    }

    private static Pendency newCharge(
        DriverCar contract,
        PendencyOriginType type,
        String name,
        BigDecimal amount,
        LocalDate date,
        String note,
        String key
    ) {
        Pendency pendency = new Pendency();
        pendency.setDriverCar(contract);
        // The debt belongs to the driver of this contract, frozen from now on (transfers and reserves never move it).
        pendency.setDebtor(contract.getDriver());
        pendency.setOriginType(type);
        pendency.setName(name);
        pendency.setCost(amount);
        pendency.setDate(date);
        pendency.setNote(note);
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setPaidAmount(BigDecimal.ZERO.setScale(2));
        pendency.setRemainingAmount(amount);
        pendency.setPaidAt(null);
        pendency.setPaymentMethod(null);
        pendency.setIdempotencyKey(key);
        return pendency;
    }

    /** Responsibility already assigned to drivers: the cost of every charge, whatever was paid of it. */
    private static BigDecimal assignedAmount(List<Pendency> charges, Long excludedId) {
        return charges
            .stream()
            .filter(charge -> excludedId == null || !excludedId.equals(charge.getId()))
            .map(charge -> money(charge.getCost()))
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!KEY_PATTERN.matcher(value).matches()) {
            throw invalid("Identificador da operação inválido.", "idempotencykeyinvalid");
        }
        return value;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("O valor cobrado deve ser maior que zero.", "chargeamountinvalid");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw invalid("O valor cobrado deve ter no máximo 2 casas decimais.", "chargeamountinvalid");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String optionalText(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw invalid(label + ": no máximo " + max + " caracteres.", "chargetexttoolong");
        }
        return text.isEmpty() ? null : text;
    }

    private static boolean sameMoney(BigDecimal value, BigDecimal expected) {
        return value != null && expected != null && money(value).compareTo(money(expected)) == 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            payload.put(key, value.trim());
        }
    }

    /** What the maintenance was, from its own services (never retyped by the user). */
    static String describe(Maintenance maintenance) {
        if (maintenance.getServices() == null) {
            return null;
        }
        String names = maintenance
            .getServices()
            .stream()
            .map(com.localuz.domain.Service::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .map(String::trim)
            .sorted()
            .collect(java.util.stream.Collectors.joining(", "));
        return names.isEmpty() ? null : names;
    }

    private static String localTimestamp(LocalDate date, LocalTime time) {
        return date == null ? null : LocalDateTime.of(date, time == null ? DATE_ONLY_TIME : time).toString();
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the document payload", e);
        }
    }

    private static BadRequestAlertException invalid(String message, String key) {
        return new BadRequestAlertException(message, ENTITY_NAME, key);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
