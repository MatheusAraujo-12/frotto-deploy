package com.localuz.web.rest;

import com.localuz.domain.DriverCar;
import com.localuz.domain.Pendency;
import com.localuz.domain.enumeration.PendencyStatus;
import com.localuz.repository.DriverCarRepository;
import com.localuz.repository.PendencyRepository;
import com.localuz.service.dto.DebtSummaryDTO;
import com.localuz.service.dto.PendencyPaymentDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.jhipster.web.util.HeaderUtil;

/** REST controller for managing {@link com.localuz.domain.Pendency}. */
@RestController
@RequestMapping("/api")
@Transactional
public class PendencyResource {

    private final Logger log = LoggerFactory.getLogger(PendencyResource.class);

    private static final String ENTITY_NAME = "pendency";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PendencyRepository pendencyRepository;

    private final DriverCarRepository driverCarRepository;

    public PendencyResource(PendencyRepository pendencyRepository, DriverCarRepository driverCarRepository) {
        this.pendencyRepository = pendencyRepository;
        this.driverCarRepository = driverCarRepository;
    }

    @GetMapping("/pendencies/car-driver/{carDriverId}")
    public List<Pendency> getPendencyByCarDriver(
        @PathVariable Long carDriverId,
        @RequestParam(name = "status", required = false) List<PendencyStatus> statuses
    ) {
        log.debug("REST request to get Pendency  by carDriverId : {}", carDriverId);
        getDriverCarOrThrow(carDriverId);
        return findDebtsByDriverAndStatus(carDriverId, statuses);
    }

    @GetMapping("/drivers/{driverId}/debts")
    public List<Pendency> getDebtsByDriver(
        @PathVariable(value = "driverId") Long driverCarId,
        @RequestParam(name = "status", required = false) List<PendencyStatus> statuses
    ) {
        log.debug("REST request to get debts by driverCarId : {}", driverCarId);
        getDriverCarOrThrow(driverCarId);
        return findDebtsByDriverAndStatus(driverCarId, statuses);
    }

    @GetMapping("/debts")
    public List<Pendency> getDebts(
        @RequestParam(name = "driverId", required = false) Long driverCarId,
        @RequestParam(name = "status", required = false) List<PendencyStatus> statuses
    ) {
        log.debug("REST request to get debts with filters, driverId={}, statuses={}", driverCarId, statuses);
        if (driverCarId != null) {
            getDriverCarOrThrow(driverCarId);
            return findDebtsByDriverAndStatus(driverCarId, statuses);
        }
        if (hasStatuses(statuses)) {
            return pendencyRepository.findByCurrentUserAndStatusInOrderByDateDesc(statuses);
        }
        return pendencyRepository.findByCurrentUserOrderByDateDesc();
    }

    @GetMapping("/drivers/{driverId}/debt-summary")
    public DebtSummaryDTO getDriverDebtSummary(@PathVariable(value = "driverId") Long driverCarId) {
        log.debug("REST request to get debt summary by driverCarId : {}", driverCarId);
        getDriverCarOrThrow(driverCarId);
        BigDecimal totalOutstanding = pendencyRepository.findOutstandingTotalByCurrentUserAndDriverCarId(driverCarId);
        long openCount = pendencyRepository.countOpenByCurrentUserAndDriverCarId(driverCarId);
        long totalCount = pendencyRepository.countByCurrentUserAndDriverCarId(driverCarId);
        long paidCount = Math.max(totalCount - openCount, 0);
        return new DebtSummaryDTO(driverCarId, totalOutstanding, openCount, totalCount, paidCount);
    }

    @GetMapping("/pendencies/{id}")
    public Pendency getPendencyById(@PathVariable Long id) {
        log.debug("REST request to get Pendency  by id : {}", id);
        Optional<Pendency> pendency = pendencyRepository.findByCurrentUserAndPendencyId(id);
        if (pendency.isPresent()) {
            return pendency.get();
        }
        return null;
    }

    @PostMapping("/pendencies/car-driver/{carDriverId}")
    public ResponseEntity<Pendency> createPendency(@PathVariable Long carDriverId, @Valid @RequestBody Pendency pendency)
        throws URISyntaxException {
        log.debug("REST request to save Pendency : {}", pendency);
        if (pendency.getId() != null) {
            throw new BadRequestAlertException("A new pendency cannot already have an ID", ENTITY_NAME, "idexists");
        }
        DriverCar pendencyCarDriver = getDriverCarOrThrow(carDriverId);
        initializePendencyForCreate(pendency);
        pendency.setDriverCar(pendencyCarDriver);

        Pendency result = pendencyRepository.save(pendency);
        return ResponseEntity
            .created(new URI("/api/pendencies/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/pendencies/{id}")
    public ResponseEntity<Void> deletePendency(@PathVariable Long id) {
        log.debug("REST request to delete Pendency : {}", id);
        getPendencyOrThrow(id);
        pendencyRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/pendencies/{id}")
    public ResponseEntity<Pendency> updatePendency(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Pendency pendency
    ) throws URISyntaxException {
        log.debug("REST request to update Pendency : {}, {}", id, pendency);
        if (pendency.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, pendency.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Pendency existingPendency = getPendencyOrThrow(id);
        if (existingPendency.getStatus() == PendencyStatus.PAID) {
            throw new BadRequestAlertException("Paid pendencies cannot be edited", ENTITY_NAME, "pendencypaid");
        }

        applyEditableFields(existingPendency, pendency);
        recalculatePaymentFields(existingPendency);
        Pendency result = pendencyRepository.save(existingPendency);

        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, pendency.getId().toString()))
            .body(result);
    }

    @PostMapping("/pendencies/{id}/pay")
    public ResponseEntity<Pendency> payPendency(
        @PathVariable Long id,
        @RequestBody(required = false) PendencyPaymentDTO payment
    ) {
        log.debug("REST request to fully pay Pendency : {}", id);
        Pendency pendency = getPendencyOrThrow(id);
        if (pendency.getStatus() == PendencyStatus.PAID) {
            throw new BadRequestAlertException("Pendency is already paid", ENTITY_NAME, "pendencyalreadypaid");
        }
        BigDecimal remainingAmount = getCurrentRemainingAmount(pendency);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Pendency has no outstanding amount", ENTITY_NAME, "pendencynoopenamount");
        }
        pendency.setPaidAmount(nonNull(pendency.getCost()));
        pendency.setRemainingAmount(BigDecimal.ZERO);
        pendency.setStatus(PendencyStatus.PAID);
        pendency.setPaidAt(Instant.now());
        applyPaymentMethod(pendency, payment);
        Pendency result = pendencyRepository.save(pendency);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pendencies/{id}/payments")
    public ResponseEntity<Pendency> addPaymentToPendency(@PathVariable Long id, @Valid @RequestBody PendencyPaymentDTO payment) {
        log.debug("REST request to add partial payment to Pendency : {}, {}", id, payment);
        if (payment == null || payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Payment amount must be > 0", ENTITY_NAME, "invalidpaymentamount");
        }
        Pendency pendency = getPendencyOrThrow(id);
        if (pendency.getStatus() == PendencyStatus.PAID) {
            throw new BadRequestAlertException("Pendency is already paid", ENTITY_NAME, "pendencyalreadypaid");
        }

        BigDecimal remainingAmount = getCurrentRemainingAmount(pendency);
        if (payment.getAmount().compareTo(remainingAmount) > 0) {
            throw new BadRequestAlertException(
                "Payment amount cannot be greater than remaining amount",
                ENTITY_NAME,
                "invalidpaymentamount"
            );
        }

        BigDecimal paidAmount = nonNull(pendency.getPaidAmount()).add(payment.getAmount());
        BigDecimal newRemaining = remainingAmount.subtract(payment.getAmount());
        pendency.setPaidAmount(paidAmount);
        pendency.setRemainingAmount(newRemaining);
        applyPaymentMethod(pendency, payment);
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            pendency.setStatus(PendencyStatus.PAID);
            pendency.setPaidAt(Instant.now());
        } else {
            pendency.setStatus(PendencyStatus.PARTIALLY_PAID);
        }
        Pendency result = pendencyRepository.save(pendency);
        return ResponseEntity.ok(result);
    }

    private DriverCar getDriverCarOrThrow(Long carDriverId) {
        return driverCarRepository
            .findByCurrentUserAndId(carDriverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found for current user"));
    }

    private Pendency getPendencyOrThrow(Long pendencyId) {
        return pendencyRepository
            .findByCurrentUserAndPendencyId(pendencyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pendency not found for current user"));
    }

    private boolean hasStatuses(List<PendencyStatus> statuses) {
        return statuses != null && !statuses.isEmpty();
    }

    private List<Pendency> findDebtsByDriverAndStatus(Long driverCarId, List<PendencyStatus> statuses) {
        if (hasStatuses(statuses)) {
            return pendencyRepository.findByCurrentUserAndDriverCarIdAndStatusInOrderByDateDesc(driverCarId, statuses);
        }
        return pendencyRepository.findByCurrentUserAndDriverCarIdOrderByDateDesc(driverCarId);
    }

    private void initializePendencyForCreate(Pendency pendency) {
        validateCost(pendency.getCost());
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setPaidAt(null);
        pendency.setPaidAmount(BigDecimal.ZERO);
        pendency.setRemainingAmount(nonNull(pendency.getCost()));
        if (pendency.getPaymentMethod() != null && pendency.getPaymentMethod().trim().isEmpty()) {
            pendency.setPaymentMethod(null);
        }
    }

    private void applyEditableFields(Pendency existingPendency, Pendency pendency) {
        if (pendency.getName() != null) {
            existingPendency.setName(pendency.getName());
        }
        if (pendency.getDate() != null) {
            existingPendency.setDate(pendency.getDate());
        }
        if (pendency.getCost() != null) {
            existingPendency.setCost(pendency.getCost());
        }
        existingPendency.setNote(pendency.getNote());
    }

    private void recalculatePaymentFields(Pendency pendency) {
        validateCost(pendency.getCost());
        BigDecimal cost = nonNull(pendency.getCost());
        BigDecimal paidAmount = nonNull(pendency.getPaidAmount());

        if (paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Paid amount must be >= 0", ENTITY_NAME, "paidamountinvalid");
        }
        if (paidAmount.compareTo(cost) > 0) {
            throw new BadRequestAlertException(
                "Paid amount cannot be greater than debt value",
                ENTITY_NAME,
                "paidamountinvalid"
            );
        }

        BigDecimal remainingAmount = cost.subtract(paidAmount);
        pendency.setPaidAmount(paidAmount);
        pendency.setRemainingAmount(remainingAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) == 0) {
            pendency.setStatus(PendencyStatus.PAID);
            if (paidAmount.compareTo(BigDecimal.ZERO) > 0 && pendency.getPaidAt() == null) {
                pendency.setPaidAt(Instant.now());
            }
            return;
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            pendency.setStatus(PendencyStatus.PARTIALLY_PAID);
            return;
        }
        pendency.setStatus(PendencyStatus.OPEN);
        pendency.setPaidAt(null);
    }

    private BigDecimal getCurrentRemainingAmount(Pendency pendency) {
        if (pendency.getRemainingAmount() != null) {
            return pendency.getRemainingAmount();
        }
        return nonNull(pendency.getCost()).subtract(nonNull(pendency.getPaidAmount()));
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateCost(BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Debt value must be >= 0", ENTITY_NAME, "costinvalid");
        }
    }

    private void applyPaymentMethod(Pendency pendency, PendencyPaymentDTO payment) {
        if (payment == null || payment.getPaymentMethod() == null) {
            return;
        }
        String paymentMethod = payment.getPaymentMethod().trim();
        pendency.setPaymentMethod(paymentMethod.isEmpty() ? null : paymentMethod);
    }
}
