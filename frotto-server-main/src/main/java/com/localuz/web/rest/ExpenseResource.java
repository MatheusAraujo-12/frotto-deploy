package com.localuz.web.rest;

import com.localuz.domain.Expense;
import com.localuz.domain.Inspection;
import com.localuz.repository.ExpenseRepository;
import com.localuz.repository.InspectionRepository;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
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

/** REST controller for managing {@link com.localuz.domain.Expense}. */
@RestController
@RequestMapping("/api")
@Transactional
public class ExpenseResource {

    private final Logger log = LoggerFactory.getLogger(ExpenseResource.class);

    private static final String ENTITY_NAME = "expense";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExpenseRepository expenseRepository;

    private final InspectionRepository inspectionRepository;

    public ExpenseResource(ExpenseRepository expenseRepository, InspectionRepository inspectionRepository) {
        this.expenseRepository = expenseRepository;
        this.inspectionRepository = inspectionRepository;
    }

    @GetMapping("/expenses/{id}")
    public Expense getExpenseById(@PathVariable Long id) {
        log.debug("REST request to get Expense  by id : {}", id);
        Optional<Expense> existingExpenseOpt = expenseRepository.findByCurrentUserAndExpenseId(id);
        if (existingExpenseOpt.isPresent()) {
            return existingExpenseOpt.get();
        }
        return null;
    }

    @PostMapping("/expenses/inspection/{inspectionId}")
    public ResponseEntity<Expense> createExpense(@Valid @RequestBody Expense expense, @PathVariable Long inspectionId)
        throws URISyntaxException {
        log.debug("REST request to save Expense : {}", expense);
        if (expense.getId() != null) {
            throw new BadRequestAlertException("A new expense cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Optional<Inspection> existingInspectionOpt = inspectionRepository.findByCurrentUserAndInspectionId(inspectionId);
        if (!existingInspectionOpt.isPresent()) {
            throw new BadRequestAlertException("Car-Inspection not found for current user", ENTITY_NAME, "notcurrentuser");
        }
        expense.setInspection(existingInspectionOpt.get());

        Expense result = expenseRepository.save(expense);
        return ResponseEntity
            .created(new URI("/api/expenses/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        log.debug("REST request to delete Expense : {}", id);
        Optional<Expense> existingExpenseOpt = expenseRepository.findByCurrentUserAndExpenseId(id);
        if (!existingExpenseOpt.isPresent()) {
            throw new BadRequestAlertException("Expense-Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        expenseRepository.deleteById(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<Expense> updateExpense(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Expense expense
    ) {
        log.debug("REST request to update Expense : {}, {}", id, expense);
        if (expense.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, expense.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        Optional<Expense> existingExpenseOpt = expenseRepository.findByCurrentUserAndExpenseId(id);
        if (!existingExpenseOpt.isPresent()) {
            throw new BadRequestAlertException("Expense-Car not found for current user", ENTITY_NAME, "notcurrentuser");
        }

        expense.setInspection(existingExpenseOpt.get().getInspection());

        Expense result = expenseRepository.save(expense);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, expense.getId().toString()))
            .body(result);
    }
}
