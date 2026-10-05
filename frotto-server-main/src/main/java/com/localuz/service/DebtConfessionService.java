package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.DebtItemType;
import com.localuz.domain.Driver;
import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DebtItemTypeRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.dto.DebtConfessionPreviewDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.DebtConfessionOutdatedException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single source of the rules of a Confissão de Dívida generated from pendencies: which pendencies can be confessed
 * together and which values the document carries. It only reads pendencies: the confession formalizes the debt,
 * the pendencies stay its only financial record.
 */
@Service
@Transactional(readOnly = true)
public class DebtConfessionService {

    static final int MAX_PENDENCIES = 50;
    static final String FALLBACK_TYPE_NAME = "Outros";

    /** payload_json block that records where a Confissão de Dívida came from. */
    public static final String ORIGIN_KEY = "origem";
    public static final String ORIGIN_TYPE_PENDENCIES = "PENDENCIAS";
    /** 2: identity is the debtor (driverId); items may come from several contracts, each with its own origin. */
    static final int ORIGIN_VERSION = 2;
    static final String ITEMS_KEY = "itensDaDivida";
    static final String SOURCE_PENDENCY_KEY = "sourcePendencyId";
    /** Keys a confession originated from pendencies never takes from the client (legacy single-item keys included). */
    private static final List<String> SERVER_OWNED_KEYS = List.of(
        ORIGIN_KEY,
        ITEMS_KEY,
        "valorTotal",
        "driverName",
        "driverCpf",
        "carPlate",
        "carModel",
        "tipoItem",
        "descricaoItem",
        "valorItem"
    );

    private static final String ENTITY_NAME = "pendency";
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PendencyRepository pendencyRepository;
    private final DebtItemTypeRepository debtItemTypeRepository;

    public DebtConfessionService(PendencyRepository pendencyRepository, DebtItemTypeRepository debtItemTypeRepository) {
        this.pendencyRepository = pendencyRepository;
        this.debtItemTypeRepository = debtItemTypeRepository;
    }

    public DebtConfessionPreviewDTO preview(List<Long> pendencyIds) {
        return buildPreview(loadEligible(pendencyIds));
    }

    /**
     * The debtor's data always; contract and car only when every pendency shares them (a confession may gather debts
     * born in several contracts and cars of the same driver - each item keeps its own origin).
     */
    private DebtConfessionPreviewDTO buildPreview(List<Pendency> pendencies) {
        Driver debtor = pendencies.get(0).getDebtor();
        Set<Long> contractIds = pendencies.stream().map(pendency -> pendency.getDriverCar().getId()).collect(Collectors.toSet());
        Set<Long> carIds = pendencies.stream().map(pendency -> pendency.getDriverCar().getCar().getId()).collect(Collectors.toSet());
        List<DebtItemType> activeTypes = debtItemTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc();

        DebtConfessionPreviewDTO preview = new DebtConfessionPreviewDTO();
        preview.setDriverId(debtor.getId());
        preview.setDriverName(debtor.getName());
        preview.setDriverCpf(debtor.getCpf());
        DriverCar singleContract = contractIds.size() == 1 ? pendencies.get(0).getDriverCar() : null;
        if (singleContract != null) {
            preview.setDriverCarId(singleContract.getId());
            preview.setContractNumber(blankToNull(singleContract.getContractNumber()));
            preview.setContractStartDate(singleContract.getStartDate());
            preview.setContractEndDate(singleContract.getEndDate());
            preview.setContractConcluded(singleContract.getConcluded());
        }
        if (carIds.size() == 1) {
            Car car = pendencies.get(0).getDriverCar().getCar();
            preview.setCarId(car.getId());
            preview.setCarPlate(car.getPlate());
            preview.setCarModel(car.getModel());
        }
        preview.setOrigemDaDivida(
            singleContract != null
                ? buildOrigin(singleContract, singleContract.getCar())
                : "Pendências em aberto registradas em nome do motorista, referentes aos contratos e veículos discriminados nos itens abaixo."
        );

        BigDecimal total = BigDecimal.ZERO;
        List<DebtConfessionPreviewDTO.Item> items = new ArrayList<>();
        for (Pendency pendency : pendencies) {
            DebtConfessionPreviewDTO.Item item = toItem(pendency, activeTypes);
            items.add(item);
            total = total.add(item.getValorItem());
        }
        preview.setItems(items);
        preview.setValorTotal(total);
        return preview;
    }

    /**
     * The current user's pendencies for {@code pendencyIds}, oldest first. All of them must exist for the current
     * user, have the same debtor (the driver who owes them - possibly from different contracts and cars), come from
     * operational vehicles and still have an outstanding balance.
     */
    public List<Pendency> loadEligible(List<Long> pendencyIds) {
        validateIds(pendencyIds);
        List<Pendency> pendencies = pendencyRepository.findByCurrentUserAndIdIn(pendencyIds);
        if (pendencies == null || pendencies.size() != pendencyIds.size()) {
            // Never reveals which ids exist in other accounts.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pendency not found for current user");
        }

        if (pendencies.stream().anyMatch(pendency -> pendency.getDebtor() == null || pendency.getDriverCar() == null)) {
            throw new BadRequestAlertException("Pendência sem motorista devedor.", ENTITY_NAME, "pendencywithoutdebtor");
        }
        Set<Long> debtorIds = pendencies.stream().map(pendency -> pendency.getDebtor().getId()).collect(Collectors.toSet());
        if (debtorIds.size() != 1) {
            throw new BadRequestAlertException(
                "Todas as pendências da confissão devem ser do mesmo motorista.",
                ENTITY_NAME,
                "pendenciesdifferentdebtors"
            );
        }

        for (Pendency pendency : pendencies) {
            VehicleLifecycleService.requireOperational(pendency.getDriverCar().getCar());
        }

        for (Pendency pendency : pendencies) {
            if (pendency.getStatus() == PendencyStatus.PAID || outstandingAmount(pendency).compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestAlertException(
                    "Somente pendências com saldo em aberto podem entrar na confissão.",
                    ENTITY_NAME,
                    "pendencynotopen"
                );
            }
        }

        List<Pendency> ordered = new ArrayList<>(pendencies);
        ordered.sort(
            Comparator.comparing(Pendency::getDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Pendency::getId)
        );
        return ordered;
    }

    /** True when the payload carries an origin block, whatever its content. */
    public static boolean hasOrigin(Map<String, Object> payload) {
        return payload != null && payload.containsKey(ORIGIN_KEY);
    }

    /** True for a Confissão de Dívida originated from pendencies (origem.tipo = PENDENCIAS). */
    public static boolean isPendencyOrigin(Map<String, Object> payload) {
        if (!hasOrigin(payload)) {
            return false;
        }
        Object origin = payload.get(ORIGIN_KEY);
        return origin instanceof Map && ORIGIN_TYPE_PENDENCIES.equals(((Map<?, ?>) origin).get("tipo"));
    }

    /** True when the client payload keeps the stored origin: PENDENCIAS, same debtor and same set of pendencies. */
    public static boolean sameOriginSelection(Map<String, Object> storedPayload, Map<String, Object> clientPayload) {
        if (!isPendencyOrigin(storedPayload) || !isPendencyOrigin(clientPayload)) {
            return false;
        }
        Map<?, ?> stored = (Map<?, ?>) storedPayload.get(ORIGIN_KEY);
        Map<?, ?> client = (Map<?, ?>) clientPayload.get(ORIGIN_KEY);
        List<Long> storedIds = requireIds(stored.get("pendencyIds"));
        List<Long> clientIds = requireIds(client.get("pendencyIds"));
        return (
            requireId(stored.get("driverId")).equals(requireId(client.get("driverId"))) &&
            storedIds.size() == clientIds.size() &&
            new HashSet<>(storedIds).equals(new HashSet<>(clientIds))
        );
    }

    /** True when any item of the payload points to a pendency (sourcePendencyId). */
    public static boolean referencesPendencies(Map<String, Object> payload) {
        Object items = payload == null ? null : payload.get(ITEMS_KEY);
        if (!(items instanceof Collection)) {
            return false;
        }
        for (Object item : (Collection<?>) items) {
            if (item instanceof Map && ((Map<?, ?>) item).get(SOURCE_PENDENCY_KEY) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * CREATE / PATCH of a confession originated from pendencies. The client only states the debtor and the
     * pendencies; everything is reloaded from the database: the pendencies must be the current user's and all owed by
     * that same driver (their frozen debtor - never the current driver of any car). The document's driver must be the
     * debtor; its car must be the single car the debts came from, or none when they came from several cars. The
     * values the client shows must still be the database values (otherwise {@link DebtConfessionOutdatedException}).
     * Returns the payload to store, rebuilt by the server.
     */
    public Map<String, Object> confirmPendencyOrigin(Long documentDriverId, Long documentCarId, Map<String, Object> clientPayload) {
        Map<?, ?> origin = requirePendencyOrigin(clientPayload);
        Long claimedDebtorId = requireId(origin.get("driverId"));
        List<Long> pendencyIds = requireIds(origin.get("pendencyIds"));
        DebtConfessionPreviewDTO preview = buildPreview(loadEligible(pendencyIds));

        if (
            !preview.getDriverId().equals(claimedDebtorId) ||
            !preview.getDriverId().equals(documentDriverId) ||
            !Objects.equals(documentCarId, preview.getCarId()) ||
            (origin.get("carId") != null && !Objects.equals(requireId(origin.get("carId")), preview.getCarId())) ||
            (origin.get("driverCarId") != null && !Objects.equals(requireId(origin.get("driverCarId")), preview.getDriverCarId()))
        ) {
            throw originMismatch();
        }

        requireClientValuesMatch(clientPayload, preview);
        return buildStoredPayload(clientPayload, preview);
    }

    /**
     * FINALIZE of a confession originated from pendencies: reloads the pendencies and rejects the finalization when
     * anything financial or identifying no longer matches the stored snapshot. Never writes anything.
     */
    public void revalidateForFinalize(Long documentDriverId, Long documentCarId, Map<String, Object> storedPayload) {
        Map<?, ?> origin = requirePendencyOrigin(storedPayload);
        Long storedDriverId = requireId(origin.get("driverId"));
        Long storedCarId = origin.get("carId") == null ? null : requireId(origin.get("carId"));
        List<Long> pendencyIds = requireIds(origin.get("pendencyIds"));
        if (!storedDriverId.equals(documentDriverId) || !Objects.equals(storedCarId, documentCarId)) {
            throw originMismatch();
        }

        List<Pendency> pendencies;
        try {
            pendencies = loadEligible(pendencyIds);
        } catch (ResponseStatusException removed) {
            // The ids were the user's when the draft was saved: missing now means deleted since then.
            throw new DebtConfessionOutdatedException();
        } catch (BadRequestAlertException changed) {
            if (
                "pendencynotopen".equals(changed.getErrorKey()) ||
                "pendenciesdifferentdebtors".equals(changed.getErrorKey()) ||
                "pendencywithoutdebtor".equals(changed.getErrorKey())
            ) {
                throw new DebtConfessionOutdatedException();
            }
            throw changed;
        }

        DebtConfessionPreviewDTO current = buildPreview(pendencies);
        if (
            !storedDriverId.equals(current.getDriverId()) ||
            !Objects.equals(storedCarId, current.getCarId()) ||
            !text(origin.get("driverName")).equals(text(current.getDriverName())) ||
            !text(origin.get("driverCpf")).equals(text(current.getDriverCpf()))
        ) {
            throw new DebtConfessionOutdatedException();
        }

        Map<Long, Map<?, ?>> snapshots = new HashMap<>();
        Object rawSnapshots = origin.get("pendencias");
        if (rawSnapshots instanceof Collection) {
            for (Object raw : (Collection<?>) rawSnapshots) {
                if (raw instanceof Map) {
                    snapshots.put(requireId(((Map<?, ?>) raw).get("id")), (Map<?, ?>) raw);
                }
            }
        }
        Map<Long, BigDecimal> storedItems = itemValuesBySource(storedPayload.get(ITEMS_KEY), true);
        if (snapshots.size() != current.getItems().size() || storedItems.size() != current.getItems().size()) {
            throw new DebtConfessionOutdatedException();
        }
        for (DebtConfessionPreviewDTO.Item item : current.getItems()) {
            Map<?, ?> snapshot = snapshots.get(item.getPendencyId());
            if (
                snapshot == null ||
                !sameMoney(snapshot.get("cost"), item.getCost()) ||
                !sameMoney(snapshot.get("paidAmount"), item.getPaidAmount()) ||
                !sameMoney(snapshot.get("remainingAmount"), item.getRemainingAmount()) ||
                !text(snapshot.get("status")).equals(item.getStatus() == null ? "" : item.getStatus().name()) ||
                !sameMoney(storedItems.get(item.getPendencyId()), item.getValorItem()) ||
                !text(snapshot.get("driverCarId")).equals(text(item.getOriginDriverCarId())) ||
                !text(snapshot.get("carPlate")).equals(text(item.getOriginCarPlate()))
            ) {
                throw new DebtConfessionOutdatedException();
            }
        }
        if (!sameMoney(storedPayload.get("valorTotal"), current.getValorTotal()) || !sameMoney(origin.get("valorTotal"), current.getValorTotal())) {
            throw new DebtConfessionOutdatedException();
        }
    }

    /** Every item the client sent must be one selected pendency with exactly its current outstanding balance. */
    private void requireClientValuesMatch(Map<String, Object> clientPayload, DebtConfessionPreviewDTO preview) {
        Map<Long, BigDecimal> clientItems = itemValuesBySource(clientPayload.get(ITEMS_KEY), false);
        Set<Long> selected = preview.getItems().stream().map(DebtConfessionPreviewDTO.Item::getPendencyId).collect(Collectors.toSet());
        if (!clientItems.keySet().equals(selected)) {
            throw originMismatch();
        }
        for (DebtConfessionPreviewDTO.Item item : preview.getItems()) {
            if (!sameMoney(clientItems.get(item.getPendencyId()), item.getValorItem())) {
                throw new DebtConfessionOutdatedException();
            }
        }
        Object clientTotal = clientPayload.get("valorTotal");
        if (clientTotal != null && !sameMoney(clientTotal, preview.getValorTotal())) {
            throw new DebtConfessionOutdatedException();
        }
    }

    /** sourcePendencyId -> valorItem of the payload items; malformed structures are rejected (stored ones: outdated). */
    private Map<Long, BigDecimal> itemValuesBySource(Object rawItems, boolean stored) {
        if (!(rawItems instanceof Collection) || ((Collection<?>) rawItems).isEmpty()) {
            throw stored ? new DebtConfessionOutdatedException() : originInvalid();
        }
        Map<Long, BigDecimal> values = new HashMap<>();
        for (Object raw : (Collection<?>) rawItems) {
            if (!(raw instanceof Map)) {
                throw stored ? new DebtConfessionOutdatedException() : originInvalid();
            }
            Map<?, ?> item = (Map<?, ?>) raw;
            Long sourceId = requireId(item.get(SOURCE_PENDENCY_KEY));
            BigDecimal value = toMoney(item.get("valorItem"));
            if (value == null || values.put(sourceId, value) != null) {
                throw stored ? new DebtConfessionOutdatedException() : originInvalid();
            }
        }
        return values;
    }

    private Map<String, Object> buildStoredPayload(Map<String, Object> clientPayload, DebtConfessionPreviewDTO preview) {
        Map<String, Object> stored = new LinkedHashMap<>(clientPayload);
        SERVER_OWNED_KEYS.forEach(stored::remove);

        // General description is free text of the user; everything identifying or financial comes from the database.
        String clientOriginText = text(clientPayload.get("origemDaDivida"));
        stored.put("origemDaDivida", clientOriginText.isEmpty() ? preview.getOrigemDaDivida() : clientOriginText);
        stored.put("driverName", preview.getDriverName());
        stored.put("driverCpf", preview.getDriverCpf());
        stored.put("carPlate", preview.getCarPlate());
        stored.put("carModel", preview.getCarModel());

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();
        List<Long> pendencyIds = new ArrayList<>();
        for (DebtConfessionPreviewDTO.Item item : preview.getItems()) {
            Map<String, Object> documentItem = new LinkedHashMap<>();
            documentItem.put("typeId", item.getTypeId());
            documentItem.put("typeNameSnapshot", item.getTypeNameSnapshot());
            documentItem.put("descricaoItem", item.getDescricaoItem());
            documentItem.put("valorItem", money(item.getValorItem()));
            documentItem.put(SOURCE_PENDENCY_KEY, item.getPendencyId());
            items.add(documentItem);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", item.getPendencyId());
            snapshot.put("name", item.getName());
            snapshot.put("date", item.getDate() == null ? null : item.getDate().toString());
            snapshot.put("note", item.getNote());
            snapshot.put("status", item.getStatus() == null ? null : item.getStatus().name());
            snapshot.put("cost", money(item.getCost()));
            snapshot.put("paidAmount", money(item.getPaidAmount()));
            snapshot.put("remainingAmount", money(item.getRemainingAmount()));
            // Where this debt was born: kept per item, a confession may span several contracts and cars.
            snapshot.put("driverCarId", item.getOriginDriverCarId());
            snapshot.put("contractNumber", item.getOriginContractNumber());
            snapshot.put("carId", item.getOriginCarId());
            snapshot.put("carPlate", item.getOriginCarPlate());
            snapshot.put("carModel", item.getOriginCarModel());
            snapshots.add(snapshot);
            pendencyIds.add(item.getPendencyId());
        }
        stored.put(ITEMS_KEY, items);
        stored.put("valorTotal", money(preview.getValorTotal()));

        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("tipo", ORIGIN_TYPE_PENDENCIES);
        origin.put("versao", ORIGIN_VERSION);
        origin.put("driverId", preview.getDriverId());
        origin.put("pendencyIds", pendencyIds);
        // Single contract / single car only; null when the debts come from several of them.
        origin.put("driverCarId", preview.getDriverCarId());
        origin.put("carId", preview.getCarId());
        origin.put("contractNumber", preview.getContractNumber());
        origin.put("contractStartDate", preview.getContractStartDate() == null ? null : preview.getContractStartDate().toString());
        origin.put("contractEndDate", preview.getContractEndDate() == null ? null : preview.getContractEndDate().toString());
        origin.put("contractConcluded", preview.getContractConcluded());
        origin.put("driverName", preview.getDriverName());
        origin.put("driverCpf", preview.getDriverCpf());
        origin.put("carPlate", preview.getCarPlate());
        origin.put("carModel", preview.getCarModel());
        origin.put("pendencias", snapshots);
        origin.put("valorTotal", money(preview.getValorTotal()));
        origin.put("snapshotEm", Instant.now().toString());
        stored.put(ORIGIN_KEY, origin);
        return stored;
    }

    private static Map<?, ?> requirePendencyOrigin(Map<String, Object> payload) {
        if (!isPendencyOrigin(payload)) {
            throw originInvalid();
        }
        return (Map<?, ?>) payload.get(ORIGIN_KEY);
    }

    private static List<Long> requireIds(Object value) {
        if (!(value instanceof Collection) || ((Collection<?>) value).isEmpty()) {
            throw new BadRequestAlertException("Selecione ao menos uma pendência.", ENTITY_NAME, "pendencyidsrequired");
        }
        List<Long> ids = new ArrayList<>();
        for (Object raw : (Collection<?>) value) {
            ids.add(requireId(raw));
        }
        return ids;
    }

    private static Long requireId(Object value) {
        try {
            if (value instanceof Number || value instanceof String) {
                return new BigDecimal(String.valueOf(value).trim()).longValueExact();
            }
        } catch (ArithmeticException | NumberFormatException invalid) {
            // falls through
        }
        throw originInvalid();
    }

    /** Plain numbers only (as sent by the API clients and stored in payload_json); anything else is null. */
    private static BigDecimal toMoney(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number || value instanceof String) {
            try {
                return new BigDecimal(String.valueOf(value).trim());
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return null;
    }

    /** Equal once rounded to cents (clients sum floating point values). */
    private static boolean sameMoney(Object value, BigDecimal expected) {
        BigDecimal actual = toMoney(value);
        return actual != null && expected != null && money(actual).compareTo(money(expected)) == 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return nonNull(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BadRequestAlertException originInvalid() {
        return new BadRequestAlertException("Origem da confissão inválida.", "document", "confessionorigininvalid");
    }

    private static BadRequestAlertException originMismatch() {
        return new BadRequestAlertException(
            "As pendências não pertencem ao motorista, veículo e vínculo informados.",
            "document",
            "confessionoriginmismatch"
        );
    }

    /** Same rule as the pendency payment flow: the stored balance, or cost minus what was paid. */
    static BigDecimal outstandingAmount(Pendency pendency) {
        if (pendency.getRemainingAmount() != null) {
            return pendency.getRemainingAmount();
        }
        return nonNull(pendency.getCost()).subtract(nonNull(pendency.getPaidAmount()));
    }

    private void validateIds(List<Long> pendencyIds) {
        if (pendencyIds == null || pendencyIds.isEmpty()) {
            throw new BadRequestAlertException("Selecione ao menos uma pendência.", ENTITY_NAME, "pendencyidsrequired");
        }
        if (pendencyIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestAlertException("Pendência inválida.", ENTITY_NAME, "pendencyidinvalid");
        }
        if (new HashSet<>(pendencyIds).size() != pendencyIds.size()) {
            throw new BadRequestAlertException("Pendência selecionada mais de uma vez.", ENTITY_NAME, "pendencyidsduplicated");
        }
        if (pendencyIds.size() > MAX_PENDENCIES) {
            throw new BadRequestAlertException(
                "Selecione no máximo " + MAX_PENDENCIES + " pendências.",
                ENTITY_NAME,
                "toomanypendencies"
            );
        }
    }

    private DebtConfessionPreviewDTO.Item toItem(Pendency pendency, List<DebtItemType> activeTypes) {
        BigDecimal outstanding = outstandingAmount(pendency);
        DebtItemType type = findType(pendency.getName(), activeTypes);

        DebtConfessionPreviewDTO.Item item = new DebtConfessionPreviewDTO.Item();
        item.setPendencyId(pendency.getId());
        item.setName(pendency.getName());
        item.setDate(pendency.getDate());
        item.setNote(pendency.getNote());
        item.setStatus(pendency.getStatus());
        item.setCost(pendency.getCost());
        item.setPaidAmount(nonNull(pendency.getPaidAmount()));
        item.setRemainingAmount(outstanding);
        item.setTypeId(type == null ? null : type.getId());
        item.setTypeNameSnapshot(type == null ? FALLBACK_TYPE_NAME : type.getName());
        item.setDescricaoItem(buildDescription(pendency, outstanding));
        item.setValorItem(outstanding);
        DriverCar origin = pendency.getDriverCar();
        item.setOriginDriverCarId(origin.getId());
        item.setOriginContractNumber(blankToNull(origin.getContractNumber()));
        item.setOriginCarId(origin.getCar().getId());
        item.setOriginCarPlate(origin.getCar().getPlate());
        item.setOriginCarModel(origin.getCar().getModel());
        return item;
    }

    /** Exact name match (ignoring case, accents and spacing), otherwise "Outros": never a guess by substring. */
    private DebtItemType findType(String pendencyName, List<DebtItemType> activeTypes) {
        String target = normalizeName(pendencyName);
        DebtItemType fallback = null;
        for (DebtItemType type : activeTypes) {
            String typeName = normalizeName(type.getName());
            if (!target.isEmpty() && target.equals(typeName)) {
                return type;
            }
            if (fallback == null && normalizeName(FALLBACK_TYPE_NAME).equals(typeName)) {
                fallback = type;
            }
        }
        return fallback;
    }

    private String buildDescription(Pendency pendency, BigDecimal outstanding) {
        String name = blankToNull(pendency.getName());
        StringBuilder description = new StringBuilder(name == null ? "Pendência" : name);
        if (pendency.getDate() != null) {
            description.append(" (").append(pendency.getDate().format(DATE_FORMAT)).append(")");
        }
        BigDecimal paid = nonNull(pendency.getPaidAmount());
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            description
                .append(" - saldo remanescente; valor original ")
                .append(formatCurrency(nonNull(pendency.getCost())))
                .append(", já pago ")
                .append(formatCurrency(paid));
        }
        String note = blankToNull(pendency.getNote());
        if (note != null) {
            description.append(". ").append(note);
        }
        return description.toString();
    }

    private String buildOrigin(DriverCar driverCar, Car car) {
        StringBuilder origin = new StringBuilder("Pendências em aberto registradas no contrato");
        String contractNumber = blankToNull(driverCar.getContractNumber());
        if (contractNumber != null) {
            origin.append(" nº ").append(contractNumber);
        }
        LocalDate startDate = driverCar.getStartDate();
        if (startDate != null) {
            origin.append(" iniciado em ").append(startDate.format(DATE_FORMAT));
        }
        String plate = blankToNull(car.getPlate());
        if (plate != null) {
            origin.append(", veículo placa ").append(plate);
        }
        return origin.append(".").toString();
    }

    private static String formatCurrency(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + format.format(value);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
