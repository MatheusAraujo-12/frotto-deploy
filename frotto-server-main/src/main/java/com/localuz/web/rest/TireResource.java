package com.localuz.web.rest;

import com.localuz.repository.TireRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing {@link com.localuz.domain.Tire}. */
@RestController
@RequestMapping("/api")
@Transactional
public class TireResource {

    private final Logger log = LoggerFactory.getLogger(TireResource.class);

    private static final String ENTITY_NAME = "tire";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TireRepository tireRepository;

    public TireResource(TireRepository tireRepository) {
        this.tireRepository = tireRepository;
    }
    //
    //    /**
    //     * {@code POST  /tires} : Create a new tire.
    //     *
    //     * @param tire the tire to create.
    //     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the
    // new tire, or with status {@code 400 (Bad Request)} if the tire has already an ID.
    //     * @throws URISyntaxException if the Location URI syntax is incorrect.
    //     */
    //    @PostMapping("/tires")
    //    public ResponseEntity<Tire> createTire(@Valid @RequestBody Tire tire) throws
    // URISyntaxException {
    //        log.debug("REST request to save Tire : {}", tire);
    //        if (tire.getId() != null) {
    //            throw new BadRequestAlertException("A new tire cannot already have an ID",
    // ENTITY_NAME, "idexists");
    //        }
    //        Tire result = tireRepository.save(tire);
    //        return ResponseEntity
    //            .created(new URI("/api/tires/" + result.getId()))
    //            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME,
    // result.getId().toString()))
    //            .body(result);
    //    }
    //
    //    /**
    //     * {@code PUT  /tires/:id} : Updates an existing tire.
    //     *
    //     * @param id the id of the tire to save.
    //     * @param tire the tire to update.
    //     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated
    // tire,
    //     * or with status {@code 400 (Bad Request)} if the tire is not valid,
    //     * or with status {@code 500 (Internal Server Error)} if the tire couldn't be updated.
    //     * @throws URISyntaxException if the Location URI syntax is incorrect.
    //     */
    //    @PutMapping("/tires/{id}")
    //    public ResponseEntity<Tire> updateTire(@PathVariable(value = "id", required = false) final
    // Long id, @Valid @RequestBody Tire tire)
    //        throws URISyntaxException {
    //        log.debug("REST request to update Tire : {}, {}", id, tire);
    //        if (tire.getId() == null) {
    //            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
    //        }
    //        if (!Objects.equals(id, tire.getId())) {
    //            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
    //        }
    //
    //        if (!tireRepository.existsById(id)) {
    //            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
    //        }
    //
    //        Tire result = tireRepository.save(tire);
    //        return ResponseEntity
    //            .ok()
    //            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME,
    // tire.getId().toString()))
    //            .body(result);
    //    }
    //
    //    /**
    //     * {@code PATCH  /tires/:id} : Partial updates given fields of an existing tire, field will
    // ignore if it is null
    //     *
    //     * @param id the id of the tire to save.
    //     * @param tire the tire to update.
    //     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated
    // tire,
    //     * or with status {@code 400 (Bad Request)} if the tire is not valid,
    //     * or with status {@code 404 (Not Found)} if the tire is not found,
    //     * or with status {@code 500 (Internal Server Error)} if the tire couldn't be updated.
    //     * @throws URISyntaxException if the Location URI syntax is incorrect.
    //     */
    //    @PatchMapping(value = "/tires/{id}", consumes = { "application/json",
    // "application/merge-patch+json" })
    //    public ResponseEntity<Tire> partialUpdateTire(
    //        @PathVariable(value = "id", required = false) final Long id,
    //        @NotNull @RequestBody Tire tire
    //    ) throws URISyntaxException {
    //        log.debug("REST request to partial update Tire partially : {}, {}", id, tire);
    //        if (tire.getId() == null) {
    //            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
    //        }
    //        if (!Objects.equals(id, tire.getId())) {
    //            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
    //        }
    //
    //        if (!tireRepository.existsById(id)) {
    //            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
    //        }
    //
    //        Optional<Tire> result = tireRepository
    //            .findById(tire.getId())
    //            .map(existingTire -> {
    //                if (tire.getModel() != null) {
    //                    existingTire.setModel(tire.getModel());
    //                }
    //                if (tire.getIntegrity() != null) {
    //                    existingTire.setIntegrity(tire.getIntegrity());
    //                }
    //
    //                return existingTire;
    //            })
    //            .map(tireRepository::save);
    //
    //        return ResponseUtil.wrapOrNotFound(
    //            result,
    //            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME,
    // tire.getId().toString())
    //        );
    //    }
    //
    //    /**
    //     * {@code GET  /tires} : get all the tires.
    //     *
    //     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of tires in
    // body.
    //     */
    //    @GetMapping("/tires")
    //    public List<Tire> getAllTires() {
    //        log.debug("REST request to get all Tires");
    //        return tireRepository.findAll();
    //    }
    //
    //    /**
    //     * {@code GET  /tires/:id} : get the "id" tire.
    //     *
    //     * @param id the id of the tire to retrieve.
    //     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the tire,
    // or with status {@code 404 (Not Found)}.
    //     */
    //    @GetMapping("/tires/{id}")
    //    public ResponseEntity<Tire> getTire(@PathVariable Long id) {
    //        log.debug("REST request to get Tire : {}", id);
    //        Optional<Tire> tire = tireRepository.findById(id);
    //        return ResponseUtil.wrapOrNotFound(tire);
    //    }
    //
    //    /**
    //     * {@code DELETE  /tires/:id} : delete the "id" tire.
    //     *
    //     * @param id the id of the tire to delete.
    //     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
    //     */
    //    @DeleteMapping("/tires/{id}")
    //    public ResponseEntity<Void> deleteTire(@PathVariable Long id) {
    //        log.debug("REST request to delete Tire : {}", id);
    //        tireRepository.deleteById(id);
    //        return ResponseEntity
    //            .noContent()
    //            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME,
    // id.toString()))
    //            .build();
    //    }
}
