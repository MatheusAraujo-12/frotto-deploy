package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.DriverDocument;
import com.localuz.domain.Inspection;
import com.localuz.domain.Tire;
import com.localuz.domain.enumeration.ChecklistType;
import com.localuz.domain.enumeration.DriverAssignmentType;
import com.localuz.domain.enumeration.FuelLevel;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.DriverDocumentRepository;
import com.localuz.repository.DriverRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.service.dto.ReserveReturnResultDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Checklist de Entrega/Devolução as the operation of a contract (driver_car). A structured checklist (checklist_type
 * set) is bound to its contract and, when finalized - inside the caller's transaction, after the document lock - it
 * creates or reuses the contract (Entrega) or concludes / returns it (Devolução) with the existing assignment rules,
 * generates exactly one Inspection and, on Entrega, fills empty emergency contacts of the driver. Any failure rolls
 * everything back. Historical checklists (checklist_type NULL) are never touched by any of this.
 */
@Service
public class ChecklistService {

    static final String ENTITY_NAME = "document";
    /** Payload keys of the structured checklist (validated here, stored in the document as the user filled them). */
    public static final String DATE_KEY = "dataVistoria";
    public static final String TIME_KEY = "horaVistoria";
    public static final String ODOMETER_KEY = "km";
    public static final String FUEL_KEY = "combustivel";
    static final double MAX_ODOMETER = 9_999_999;
    /** Tire integrity values of the inspection form: only these are copied, nothing is converted. */
    private static final Set<String> TIRE_INTEGRITY = Set.of("0-10%", "10-20%", "30-50%", "50-70%", "70-90%", "90-100%");
    private static final int INSPECTION_DRIVER_NAME_LENGTH = 60;

    private final DriverCarRepository driverCars;
    private final DriverDocumentRepository documents;
    private final InspectionRepository inspections;
    private final DriverRepository drivers;
    private final DriverAssignmentService assignments;
    private final CarService carService;

    public ChecklistService(
        DriverCarRepository driverCars,
        DriverDocumentRepository documents,
        InspectionRepository inspections,
        DriverRepository drivers,
        DriverAssignmentService assignments,
        CarService carService
    ) {
        this.driverCars = driverCars;
        this.documents = documents;
        this.inspections = inspections;
        this.drivers = drivers;
        this.assignments = assignments;
        this.carService = carService;
    }

    /** What the finalization did, for the response. */
    public static final class Outcome {

        private final Long driverCarId;
        private final Long inspectionId;
        private final ReserveReturnResultDTO reserveReturn;

        Outcome(Long driverCarId, Long inspectionId, ReserveReturnResultDTO reserveReturn) {
            this.driverCarId = driverCarId;
            this.inspectionId = inspectionId;
            this.reserveReturn = reserveReturn;
        }

        public Long getDriverCarId() {
            return driverCarId;
        }

        public Long getInspectionId() {
            return inspectionId;
        }

        public ReserveReturnResultDTO getReserveReturn() {
            return reserveReturn;
        }
    }

    // ---------------------------------------------------------------- drafts

    /**
     * Binding of a new / edited checklist draft: the type is required (and fixed once set), a Devolução names the
     * open contract it returns, a contract given to an Entrega must be open; the contract must be of the document's
     * driver and car, in this account. payload.tipo mirrors the type for the PDF.
     */
    public void bindDraft(DriverDocument document, ChecklistType requestedType, Long requestedDriverCarId, Map<String, Object> payload) {
        ChecklistType type = document.getChecklistType() != null ? document.getChecklistType() : requestedType;
        if (type == null) {
            throw invalid("Informe se o checklist é de Entrega ou de Devolução.", "checklisttyperequired");
        }
        if (document.getChecklistType() != null && requestedType != null && requestedType != document.getChecklistType()) {
            throw invalid("O tipo do checklist não pode ser alterado: crie um novo checklist.", "checklisttypeimmutable");
        }
        Long driverCarId = requestedDriverCarId != null ? requestedDriverCarId : document.getDriverCarId();
        if (type == ChecklistType.DEVOLUCAO && driverCarId == null) {
            throw invalid("A devolução precisa do vínculo (contrato) que está sendo devolvido.", "checklistdrivercarrequired");
        }
        if (driverCarId != null) {
            DriverCar contract = driverCars.findByCurrentUserAndId(driverCarId).orElseThrow(() -> invalid("Vínculo não encontrado.", "checklistdrivercarnotfound"));
            requireSameDriverAndCar(document, contract);
            if (Boolean.TRUE.equals(contract.getConcluded())) {
                throw invalid("Este vínculo já foi encerrado.", "checklistdrivercarconcluded");
            }
            requireNotSuspended(contract);
        }
        document.setChecklistType(type);
        document.setDriverCarId(driverCarId);
        if (payload != null) {
            payload.put("tipo", type.name());
        }
    }

    // ---------------------------------------------------------------- finalization

    /**
     * Finalization of a structured checklist draft. The caller holds the document lock and runs this in its own
     * transaction (READ COMMITTED: after each lock every read sees the latest committed state); the caller marks the
     * document FINAL afterwards, in the same transaction.
     */
    public Outcome finalizeChecklist(DriverDocument document, Map<String, Object> payload, DriverAssignmentType assignment) {
        ChecklistType type = document.getChecklistType();
        Driver driver = document.getDriver();
        Car car = document.getCar();
        if (type == null || driver == null || car == null) {
            throw invalid("Checklist sem tipo, motorista ou veículo.", "checklistincomplete");
        }
        LocalDate date = requireDate(payload);
        float odometer = requireOdometer(payload);
        FuelLevel fuel = requireFuel(payload);

        // Domain order of locks (DriverAssignmentService#lockContract): cars, then the driver, then contract rows.
        // A known contract (the one returned / delivered) is locked first, with its cars; a delivery that opens a new
        // contract locks the car and the driver. Car and driver came with the document, before any lock: the domain
        // locks re-read them, so what another request committed meanwhile is seen instead of a stale version.
        DriverCar known = type == ChecklistType.DEVOLUCAO
            ? lockedContract(document.getDriverCarId(), document)
            : existingDeliveryContract(document, driver, car);
        assignments.lockForAssignment(car, driver.getId());
        VehicleLifecycleService.requireOperational(car);
        DriverCar contract;
        ReserveReturnResultDTO reserveReturn = null;
        if (type == ChecklistType.ENTREGA) {
            if (known == null) {
                // Opened meanwhile by a request that held these locks and committed: registered on it, never duplicated.
                known = deliveryContractUnderLocks(driver, car);
            }
            contract = known != null ? openForDelivery(known) : assignments.openContract(driver, car, assignment, date);
        } else {
            contract = known;
            if (Boolean.TRUE.equals(contract.getConcluded())) {
                throw invalid("Este vínculo já foi encerrado: a devolução não pode ser registrada.", "checklistdrivercarconcluded");
            }
            requireNotSuspended(contract);
            if (contract.isReserve()) {
                reserveReturn = assignments.returnReserve(contract, date);
            } else {
                assignments.concludeContract(contract, date);
            }
        }

        if (documents.existsFinalChecklist(contract.getId(), type)) {
            throw invalid(
                type == ChecklistType.ENTREGA
                    ? "Este vínculo já possui um checklist de Entrega finalizado."
                    : "Este vínculo já possui um checklist de Devolução finalizado.",
                "checklistalreadyfinalized"
            );
        }
        document.setDriverCarId(contract.getId());
        document.setFinalChecklistSlot(type);

        if (type == ChecklistType.ENTREGA) {
            fillEmptyEmergencyContacts(driver, payload);
        }

        Inspection inspection = new Inspection();
        inspection.setDate(date);
        inspection.setOdometer(odometer);
        inspection.setDriverName(truncate(driver.getName(), INSPECTION_DRIVER_NAME_LENGTH));
        inspection.setCar(car);
        inspection.setDriverCarId(contract.getId());
        inspection.setFuelLevel(fuel);
        inspection.setOriginDocumentId(document.getId());
        applyPositionalTires(inspection, payload);
        // Same rule as a manual inspection: the car's odometer follows the most recent inspection / maintenance.
        carService.updateCarOdometerByDate(date, odometer, car);
        Inspection saved = inspections.save(inspection);
        return new Outcome(contract.getId(), saved.getId(), reserveReturn);
    }

    /** Inspection generated by a checklist, if any (the documents screen shows it). */
    public Optional<Long> inspectionIdOf(Long documentId) {
        return documentId == null ? Optional.empty() : inspections.findIdByOriginDocumentId(documentId);
    }

    /**
     * Entrega: the contract named by the draft, else the open contract of exactly this driver on this car, else a
     * new one with the PERMANENT / RESERVE rules of the contract screen (the same service, the same 409).
     */
    /** The contract an Entrega is registered on when it already exists (the document's, or the exact open one), locked. */
    private DriverCar existingDeliveryContract(DriverDocument document, Driver driver, Car car) {
        if (document.getDriverCarId() != null) {
            return lockedContract(document.getDriverCarId(), document);
        }
        List<DriverCar> open = driverCars.findActiveByCurrentUserAndDriverAndCar(driver.getId(), car.getId());
        return open.isEmpty() ? null : lockedContract(open.get(0).getId(), document);
    }

    /** The exact open contract read under the car and driver locks (only its row is locked: a delivery changes nothing else). */
    private DriverCar deliveryContractUnderLocks(Driver driver, Car car) {
        List<DriverCar> open = driverCars.findActiveByCurrentUserAndDriverAndCar(driver.getId(), car.getId());
        if (open.isEmpty()) {
            return null;
        }
        DriverCar contract = open.get(0);
        assignments.lockContractRow(contract);
        return contract;
    }

    private static DriverCar openForDelivery(DriverCar contract) {
        if (Boolean.TRUE.equals(contract.getConcluded())) {
            throw invalid("Este vínculo já foi encerrado.", "checklistdrivercarconcluded");
        }
        requireNotSuspended(contract);
        return contract;
    }

    /** The document's contract, in this account, locked, and of the document's driver and car. */
    private DriverCar lockedContract(Long driverCarId, DriverDocument document) {
        if (driverCarId == null || driverCars.findByCurrentUserAndId(driverCarId).isEmpty()) {
            throw invalid("Vínculo não encontrado.", "checklistdrivercarnotfound");
        }
        DriverCar contract = driverCars.findById(driverCarId).orElseThrow(() -> invalid("Vínculo não encontrado.", "checklistdrivercarnotfound"));
        requireSameDriverAndCar(document, contract);
        // Re-read under the lock: a PUT / return committed after the ownership read is seen here.
        assignments.lockContract(contract);
        return contract;
    }

    /**
     * A suspended primary is not with the driver (who is on a reserve car): a checklist never delivers or returns it.
     * It goes back to operation through the existing flows (return of the reserve / reactivation).
     */
    private static void requireNotSuspended(DriverCar contract) {
        if (Boolean.TRUE.equals(contract.getSuspended())) {
            throw invalid(
                "Este vínculo está suspenso (o motorista está em um carro reserva). Devolva o carro reserva ou reative o vínculo antes.",
                "checklistdrivercarsuspended"
            );
        }
    }

    private static void requireSameDriverAndCar(DriverDocument document, DriverCar contract) {
        Long documentDriver = document.getDriver() == null ? null : document.getDriver().getId();
        Long documentCar = document.getCar() == null ? null : document.getCar().getId();
        Long contractDriver = contract.getDriver() == null ? null : contract.getDriver().getId();
        Long contractCar = contract.getCar() == null ? null : contract.getCar().getId();
        if (!Objects.equals(documentDriver, contractDriver)) {
            throw invalid("O vínculo é de outro motorista.", "checklistdrivercardriver");
        }
        if (!Objects.equals(documentCar, contractCar)) {
            throw invalid("O vínculo é de outro veículo.", "checklistdrivercarcar");
        }
    }

    /**
     * Entrega fills the driver's emergency contacts only where they are empty: never overwritten, never erased. A
     * driver's contact is a phone stored as its digits (as the vínculo form edits it); the contact's name stays in the
     * checklist document.
     */
    private void fillEmptyEmergencyContacts(Driver driver, Map<String, Object> payload) {
        List<String> contacts = new ArrayList<>();
        Object raw = payload == null ? null : payload.get("emergencyContacts");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map) {
                    String name = text(((Map<?, ?>) item).get("nome"));
                    String phone = text(((Map<?, ?>) item).get("telefone"));
                    String digits = phone.replaceAll("[^0-9]", "");
                    if (!name.isEmpty() && digits.length() >= 10 && digits.length() <= 11) {
                        contacts.add(digits);
                    }
                }
            }
        }
        boolean changed = false;
        for (String contact : contacts) {
            if (contact.equals(digitsOf(driver.getEmergencyContact())) || contact.equals(digitsOf(driver.getEmergencyContactSecond()))) {
                continue;
            }
            if (text(driver.getEmergencyContact()).isEmpty()) {
                driver.setEmergencyContact(contact);
                changed = true;
            } else if (text(driver.getEmergencyContactSecond()).isEmpty()) {
                driver.setEmergencyContactSecond(contact);
                changed = true;
            }
        }
        if (changed) {
            drivers.save(driver);
        }
    }

    private static String digitsOf(String value) {
        return text(value).replaceAll("[^0-9]", "");
    }

    /** Only positional tires with a known position are copied: the brand as model, the integrity when it is one of the form's. */
    private static void applyPositionalTires(Inspection inspection, Map<String, Object> payload) {
        Object tires = payload == null ? null : payload.get("tires");
        if (!(tires instanceof Map) || !(((Map<?, ?>) tires).get("positions") instanceof List)) {
            return;
        }
        for (Object raw : (List<?>) ((Map<?, ?>) tires).get("positions")) {
            if (!(raw instanceof Map)) {
                continue;
            }
            Map<?, ?> position = (Map<?, ?>) raw;
            String model = truncate(text(position.get("marca")), 60);
            String integrity = text(position.get("estado"));
            Tire tire = new Tire();
            tire.setModel(model.isEmpty() ? null : model);
            tire.setIntegrity(TIRE_INTEGRITY.contains(integrity) ? integrity : null);
            if (tire.getModel() == null && tire.getIntegrity() == null) {
                continue;
            }
            switch (text(position.get("posicao"))) {
                case "Dianteiro esquerdo":
                    inspection.setLeftFront(tire);
                    break;
                case "Dianteiro direito":
                    inspection.setRightFront(tire);
                    break;
                case "Traseiro esquerdo":
                    inspection.setLeftBack(tire);
                    break;
                case "Traseiro direito":
                    inspection.setRightBack(tire);
                    break;
                case "Estepe":
                    inspection.setSpare(tire);
                    break;
                default:
                    break;
            }
        }
    }

    // ---------------------------------------------------------------- structured fields

    static LocalDate requireDate(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get(DATE_KEY);
        if (!(value instanceof String)) {
            throw invalid("Informe a data da vistoria.", "checklistdaterequired");
        }
        try {
            return LocalDate.parse((String) value);
        } catch (DateTimeParseException e) {
            throw invalid("Informe a data da vistoria.", "checklistdaterequired");
        }
    }

    static float requireOdometer(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get(ODOMETER_KEY);
        BigDecimal number;
        try {
            number = value instanceof Number ? new BigDecimal(value.toString()) : new BigDecimal(text(value));
        } catch (NumberFormatException e) {
            throw invalid("Informe a quilometragem em números.", "checklistodometerinvalid");
        }
        if (number.signum() < 0 || number.doubleValue() > MAX_ODOMETER || number.stripTrailingZeros().scale() > 1) {
            throw invalid("Informe a quilometragem em números.", "checklistodometerinvalid");
        }
        return number.floatValue();
    }

    static FuelLevel requireFuel(Map<String, Object> payload) {
        try {
            return FuelLevel.valueOf(text(payload == null ? null : payload.get(FUEL_KEY)));
        } catch (IllegalArgumentException e) {
            throw invalid("Informe o nível de combustível.", "checklistfuelrequired");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static BadRequestAlertException invalid(String message, String key) {
        return new BadRequestAlertException(message, ENTITY_NAME, key);
    }
}
