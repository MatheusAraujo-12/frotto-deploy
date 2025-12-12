package com.localuz.web.rest;

import com.localuz.domain.Car;
import com.localuz.domain.Reminder;
import com.localuz.repository.CarRepository;
import com.localuz.repository.ReminderRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

/** REST controller for managing {@link com.localuz.domain.Reminder}. */
@RestController
@RequestMapping("/api")
@Transactional
public class ReminderResource {

    private final Logger log = LoggerFactory.getLogger(ReminderResource.class);

    private static final String ENTITY_NAME = "reminder";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ReminderRepository reminderRepository;

    private final CarRepository carRepository;

    public ReminderResource(ReminderRepository reminderRepository, CarRepository carRepository) {
        this.reminderRepository = reminderRepository;
        this.carRepository = carRepository;
    }

    @GetMapping("/reminders/car/{carId}")
    public List<Reminder> getReminderByCar(@PathVariable Long carId) {
        log.debug("REST request to get Reminder  by carId : {}", carId);
        List<Reminder> reminders = reminderRepository.findByCurrentUserAndCarId(carId);
        return reminders;
    }

    @GetMapping("/reminders/{id}")
    public Reminder getReminderById(@PathVariable Long id) {
        log.debug("REST request to get Reminder  by id : {}", id);
        Optional<Reminder> reminder = reminderRepository.findByCurrentUserAndReminderId(id);
        if (reminder.isPresent()) {
            return reminder.get();
        }
        return null;
    }

    @PostMapping("/reminders/car/{carId}")
    public ResponseEntity<Reminder> createReminder(@PathVariable Long carId, @Valid @RequestBody Reminder reminder)
        throws URISyntaxException {
        log.debug("REST request to save Reminder : {}", reminder);
        if (reminder.getId() != null) {
            throw new BadRequestAlertException("A new reminder cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Car> existingCarOpt = carRepository.findByCurrentUserAndId(carId);
        if (!existingCarOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car reminderCar = existingCarOpt.get();
        reminder.setCar(reminderCar);

        Reminder result = reminderRepository.save(reminder);
        return ResponseEntity
            .created(new URI("/api/reminders/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        log.debug("REST request to delete Reminder : {}", id);
        Optional<Reminder> existingReminderOpt = reminderRepository.findByCurrentUserAndReminderId(id);
        if (!existingReminderOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Reminder not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        reminderRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/reminders/{id}")
    public ResponseEntity<Reminder> updateReminder(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Reminder reminder
    ) throws URISyntaxException {
        log.debug("REST request to update Reminder : {}, {}", id, reminder);
        if (reminder.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, reminder.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Reminder> existingReminderOpt = reminderRepository.findByCurrentUserAndReminderId(id);
        if (!existingReminderOpt.isPresent()) {
            throw new BadRequestAlertException("Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        Car reminderCar = existingReminderOpt.get().getCar();
        reminder.setCar(reminderCar);

        Reminder result = reminderRepository.save(reminder);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, reminder.getId().toString()))
            .body(result);
    }
}
