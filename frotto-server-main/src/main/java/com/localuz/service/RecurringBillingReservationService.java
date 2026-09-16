package com.localuz.service;

import com.localuz.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringBillingReservationService {
    public record Candidate(Long id, String externalSubscriptionId) {}
    private final SubscriptionRepository subscriptions;
    public RecurringBillingReservationService(SubscriptionRepository subscriptions) { this.subscriptions = subscriptions; }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Candidate> candidates(Instant cutoff, Instant now, int cancelledTerminalHorizonDays, int batchSize) {
        return subscriptions.findFinancialCandidates(cutoff, now, cancelledTerminalHorizonDays, PageRequest.of(0, batchSize)).stream()
            .map(s -> new Candidate(s.getId(), s.getExternalSubscriptionId())).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(Candidate candidate, Instant now, Instant cutoff) {
        return subscriptions.reserveFinancialReconciliation(candidate.id(), candidate.externalSubscriptionId(), now, cutoff) == 1;
    }
}
