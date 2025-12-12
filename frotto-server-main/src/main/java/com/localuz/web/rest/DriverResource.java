package com.localuz.web.rest;

import com.localuz.domain.Driver;
import com.localuz.repository.DriverRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing {@link com.localuz.domain.Driver}. */
@RestController
@RequestMapping("/api")
@Transactional
public class DriverResource {

    private final Logger log = LoggerFactory.getLogger(DriverResource.class);

    private static final String ENTITY_NAME = "driver";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DriverRepository driverRepository;

    public DriverResource(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @GetMapping("/drivers/{cpf}")
    public Driver findByCpf(@PathVariable String cpf) {
        Optional<Driver> driver = driverRepository.findByCpf(cpf);
        if (driver.isPresent()) {
            return driver.get();
        }
        return null;
    }
    //
    //  /**
    //   * {@code POST /drivers} : Create a new driver.
    //   *
    //   * @param driver the driver to create.
    //   * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new
    //   *     driver, or with status {@code 400 (Bad Request)} if the driver has already an ID.
    //   * @throws URISyntaxException if the Location URI syntax is incorrect.
    //   */
    //  @PostMapping("/drivers")
    //  public ResponseEntity<Driver> createDriver(@Valid @RequestBody Driver driver)
    //      throws URISyntaxException {
    //    log.debug("REST request to save Driver : {}", driver);
    //    if (driver.getId() != null) {
    //      throw new BadRequestAlertException(
    //          "A new driver cannot already have an ID", ENTITY_NAME, "idexists");
    //    }
    //    Driver result = driverRepository.save(driver);
    //    return ResponseEntity.created(new URI("/api/drivers/" + result.getId()))
    //        .headers(
    //            HeaderUtil.createEntityCreationAlert(
    //                applicationName, false, ENTITY_NAME, result.getId().toString()))
    //        .body(result);
    //  }
    //
    //  /**
    //   * {@code PUT /drivers/:id} : Updates an existing driver.
    //   *
    //   * @param id the id of the driver to save.
    //   * @param driver the driver to update.
    //   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated
    //   *     driver, or with status {@code 400 (Bad Request)} if the driver is not valid, or with
    // status
    //   *     {@code 500 (Internal Server Error)} if the driver couldn't be updated.
    //   * @throws URISyntaxException if the Location URI syntax is incorrect.
    //   */
    //  @PutMapping("/drivers/{id}")
    //  public ResponseEntity<Driver> updateDriver(
    //      @PathVariable(value = "id", required = false) final Long id,
    //      @Valid @RequestBody Driver driver)
    //      throws URISyntaxException {
    //    log.debug("REST request to update Driver : {}, {}", id, driver);
    //    if (driver.getId() == null) {
    //      throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
    //    }
    //    if (!Objects.equals(id, driver.getId())) {
    //      throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
    //    }
    //
    //    if (!driverRepository.existsById(id)) {
    //      throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
    //    }
    //
    //    Driver result = driverRepository.save(driver);
    //    return ResponseEntity.ok()
    //        .headers(
    //            HeaderUtil.createEntityUpdateAlert(
    //                applicationName, false, ENTITY_NAME, driver.getId().toString()))
    //        .body(result);
    //  }
    //
    //  /**
    //   * {@code PATCH /drivers/:id} : Partial updates given fields of an existing driver, field will
    //   * ignore if it is null
    //   *
    //   * @param id the id of the driver to save.
    //   * @param driver the driver to update.
    //   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated
    //   *     driver, or with status {@code 400 (Bad Request)} if the driver is not valid, or with
    // status
    //   *     {@code 404 (Not Found)} if the driver is not found, or with status {@code 500 (Internal
    //   *     Server Error)} if the driver couldn't be updated.
    //   * @throws URISyntaxException if the Location URI syntax is incorrect.
    //   */
    //  @PatchMapping(
    //      value = "/drivers/{id}",
    //      consumes = {"application/json", "application/merge-patch+json"})
    //  public ResponseEntity<Driver> partialUpdateDriver(
    //      @PathVariable(value = "id", required = false) final Long id,
    //      @NotNull @RequestBody Driver driver)
    //      throws URISyntaxException {
    //    log.debug("REST request to partial update Driver partially : {}, {}", id, driver);
    //    if (driver.getId() == null) {
    //      throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
    //    }
    //    if (!Objects.equals(id, driver.getId())) {
    //      throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
    //    }
    //
    //    if (!driverRepository.existsById(id)) {
    //      throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
    //    }
    //
    //    Optional<Driver> result =
    //        driverRepository
    //            .findById(driver.getId())
    //            .map(
    //                existingDriver -> {
    //                  if (driver.getName() != null) {
    //                    existingDriver.setName(driver.getName());
    //                  }
    //                  if (driver.getCpf() != null) {
    //                    existingDriver.setCpf(driver.getCpf());
    //                  }
    //                  if (driver.getEmail() != null) {
    //                    existingDriver.setEmail(driver.getEmail());
    //                  }
    //                  if (driver.getContact() != null) {
    //                    existingDriver.setContact(driver.getContact());
    //                  }
    //                  if (driver.getEmergencyContact() != null) {
    //                    existingDriver.setEmergencyContact(driver.getEmergencyContact());
    //                  }
    //                  if (driver.getPublicScore() != null) {
    //                    existingDriver.setPublicScore(driver.getPublicScore());
    //                  }
    //                  if (driver.getAverageKm() != null) {
    //                    existingDriver.setAverageKm(driver.getAverageKm());
    //                  }
    //                  if (driver.getAverageInspectioScore() != null) {
    //                    existingDriver.setAverageInspectioScore(driver.getAverageInspectioScore());
    //                  }
    //                  if (driver.getAverageDriverCarScore() != null) {
    //                    existingDriver.setAverageDriverCarScore(driver.getAverageDriverCarScore());
    //                  }
    //
    //                  return existingDriver;
    //                })
    //            .map(driverRepository::save);
    //
    //    return ResponseUtil.wrapOrNotFound(
    //        result,
    //        HeaderUtil.createEntityUpdateAlert(
    //            applicationName, false, ENTITY_NAME, driver.getId().toString()));
    //  }
    //
    //  /**
    //   * {@code GET /drivers} : get all the drivers.
    //   *
    //   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of drivers in
    //   *     body.
    //   */
    //  @GetMapping("/drivers")
    //  public List<Driver> getAllDrivers() {
    //    log.debug("REST request to get all Drivers");
    //    return driverRepository.findAll();
    //  }
    //
    //  /**
    //   * {@code GET /drivers/:id} : get the "id" driver.
    //   *
    //   * @param id the id of the driver to retrieve.
    //   * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the driver,
    // or
    //   *     with status {@code 404 (Not Found)}.
    //   */
    //  @GetMapping("/drivers/{id}")
    //  public ResponseEntity<Driver> getDriver(@PathVariable Long id) {
    //    log.debug("REST request to get Driver : {}", id);
    //    Optional<Driver> driver = driverRepository.findById(id);
    //    return ResponseUtil.wrapOrNotFound(driver);
    //  }
    //
    //  /**
    //   * {@code DELETE /drivers/:id} : delete the "id" driver.
    //   *
    //   * @param id the id of the driver to delete.
    //   * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
    //   */
    //  @DeleteMapping("/drivers/{id}")
    //  public ResponseEntity<Void> deleteDriver(@PathVariable Long id) {
    //    log.debug("REST request to delete Driver : {}", id);
    //    driverRepository.deleteById(id);
    //    return ResponseEntity.noContent()
    //        .headers(
    //            HeaderUtil.createEntityDeletionAlert(
    //                applicationName, false, ENTITY_NAME, id.toString()))
    //        .build();
    //  }
}
