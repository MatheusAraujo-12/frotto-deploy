package com.localuz.service;

import static com.localuz.service.dto.RecurringReconciliationResult.Outcome.*;

import com.localuz.config.RecurringBillingReconciliationProperties;
import com.localuz.service.RecurringBillingReservationService.Candidate;
import com.localuz.service.dto.MercadoPagoAuthorizedPaymentPage;
import com.localuz.service.dto.RecurringReconciliationResult;
import com.localuz.service.dto.RecurringReconciliationResult.Outcome;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringBillingReconciliationService {
    private final MercadoPagoClient client;
    private final MercadoPagoFinancialIngestion ingestion;
    private final RecurringBillingReservationService reservations;
    private final RecurringBillingReconciliationProperties config;
    private final Clock clock;

    @Autowired
    public RecurringBillingReconciliationService(MercadoPagoClient client, MercadoPagoFinancialIngestion ingestion,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationProperties config) {
        this(client, ingestion, reservations, config, Clock.systemUTC());
    }
    public RecurringBillingReconciliationService(MercadoPagoClient client, MercadoPagoFinancialIngestion ingestion,
        RecurringBillingReservationService reservations, RecurringBillingReconciliationProperties config, Clock clock) {
        this.client = client; this.ingestion = ingestion; this.reservations = reservations; this.config = config; this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecurringReconciliationResult reconcile(Candidate candidate, RecurringReconciliationBudget budget) {
        config.validate();
        int initialCalls = budget.used(), ingested = 0, skipped = 0;
        TreeSet<String> ids = new TreeSet<>();
        if (candidate == null || candidate.id() == null || candidate.externalSubscriptionId() == null
            || candidate.externalSubscriptionId().isBlank()) {
            return new RecurringReconciliationResult(candidate == null ? null : candidate.id(), INVALID_CANDIDATE, 0, 0, 0, 0);
        }
        Outcome outcome = COMPLETE;
        try {
            if (!budget.available()) return new RecurringReconciliationResult(candidate.id(), DISCOVERY_INCOMPLETE, 0, 0, 0, 0);
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (!reservations.reserve(candidate, now, now.minus(config.getMinIntervalMinutes(), ChronoUnit.MINUTES))) {
                return new RecurringReconciliationResult(candidate.id(), SKIP_ALREADY_RESERVED, 0, 0, 0, 0);
            }
            int offset = 0;
            boolean complete = false;
            for (int pageNumber = 0; pageNumber < config.getMaxPages(); pageNumber++) {
                budget.beforeHttp();
                int requestedLimit = Math.min(config.getPageSize(), config.getMaxDiscoveredItems() - ids.size());
                MercadoPagoAuthorizedPaymentPage page = client.searchAuthorizedPayments(candidate.externalSubscriptionId(), offset, requestedLimit);
                if (!validPage(page, offset, requestedLimit)) {
                    return new RecurringReconciliationResult(candidate.id(), INVALID_RESPONSE, ids.size(), 0, 0, budget.used() - initialCalls);
                }
                int previousSize = ids.size();
                ids.addAll(page.ids());
                long next = (long) offset + page.limit();
                if (next >= page.total()) { complete = true; break; }
                if (ids.size() == previousSize || next > Integer.MAX_VALUE) {
                    return new RecurringReconciliationResult(candidate.id(), INVALID_RESPONSE, ids.size(), 0, 0, budget.used() - initialCalls);
                }
                offset = (int) next;
                if (ids.size() >= config.getMaxDiscoveredItems()) break;
            }
            if (!complete) outcome = DISCOVERY_INCOMPLETE;
            for (String id : ids) {
                var snapshot = ingestion.fetch("subscription_authorized_payment", id, candidate.externalSubscriptionId(), budget::beforeHttp);
                if (snapshot == null) {
                    skipped++;
                    if (outcome == COMPLETE) outcome = CORRELATION_MISMATCH;
                    continue;
                }
                if (ingestion.ingestSnapshot(snapshot, candidate.id())) ingested++;
                else { skipped++; if (outcome == COMPLETE) outcome = INGESTION_INCONCLUSIVE; }
            }
        } catch (RecurringReconciliationBudget.Exhausted exhausted) {
            outcome = DISCOVERY_INCOMPLETE;
        } catch (MercadoPagoException failure) {
            if (Integer.valueOf(429).equals(failure.getHttpStatus())) { budget.stopForRateLimit(); outcome = RATE_LIMITED; }
            else outcome = PROVIDER_FAILURE;
        } catch (org.springframework.dao.DataAccessException failure) {
            outcome = PERSISTENCE_FAILURE;
        } catch (RuntimeException failure) {
            outcome = OPERATIONAL_FAILURE;
        }
        return new RecurringReconciliationResult(candidate.id(), outcome, ids.size(), ingested, skipped, budget.used() - initialCalls);
    }

    private boolean validPage(MercadoPagoAuthorizedPaymentPage page, int offset, int requestedLimit) {
        return page != null && page.offset() == offset && page.limit() > 0 && page.limit() <= requestedLimit && page.total() >= 0
            && page.ids().size() == Math.min(page.limit(), Math.max(0, (long) page.total() - offset))
            && page.ids().stream().allMatch(id -> id != null && id.matches("[A-Za-z0-9_-]+"));
    }
}
