package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only effective subscription selection. Provider coverage comes from financial
 * competencies; administrative grants retain their existing eligibility rules.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final List<SubscriptionStatus> CURRENT_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    /**
     * Deterministic tie-break when a user has more than one valid current subscription (e.g. a
     * PAYMENT_PROVIDER subscription plus a temporary ADMIN_GRANT on top of it). Lower number
     * wins. ADMIN_GRANT outranks PAYMENT_PROVIDER so a manual override always takes effect
     * immediately without touching the underlying paid subscription; PAYMENT_PROVIDER outranks
     * GRANDFATHERED so a real subscription always supersedes a legacy backfill once one exists.
     * SubscriptionAdminService relies on this ordering too: it only ever closes a conflicting
     * ADMIN_GRANT, never a PAYMENT_PROVIDER or GRANDFATHERED row, precisely because this
     * comparator - not "which row is currently on top" - is what decides the effective plan.
     */
    private static final Map<SubscriptionSource, Integer> SOURCE_PRIORITY = new EnumMap<>(SubscriptionSource.class);

    static {
        SOURCE_PRIORITY.put(SubscriptionSource.ADMIN_GRANT, 0);
        SOURCE_PRIORITY.put(SubscriptionSource.PAYMENT_PROVIDER, 1);
        SOURCE_PRIORITY.put(SubscriptionSource.GRANDFATHERED, 2);
    }

    private static final Comparator<Subscription> BY_EFFECTIVE_PRIORITY = Comparator
        .comparingInt((Subscription s) -> SOURCE_PRIORITY.get(s.getSource()))
        .thenComparing(Subscription::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final Clock clock;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        SubscriptionFinancialCoverageService financialCoverage) {
        this(subscriptionRepository, planRepository, financialCoverage, Clock.systemUTC());
    }

    SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        SubscriptionFinancialCoverageService financialCoverage, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.financialCoverage = financialCoverage;
        this.clock = clock;
    }

    /**
     * A single evaluation instant for all candidates. Include canceled/paused provider rows:
     * they may still own a valid paid competency. Their persisted status/period is not paid proof.
     * No mutation, HTTP request or ingestion lock is needed to expire access on a read.
     */
    public Optional<Subscription> getCurrentSubscription(User user) {
        Instant now = clock.instant();
        return subscriptionRepository
            .findByUserIdOrderByStartDateDesc(user.getId())
            .stream()
            .sorted(BY_EFFECTIVE_PRIORITY)
            .filter(subscription -> subscription.getSource() == SubscriptionSource.PAYMENT_PROVIDER
                ? financialCoverage.evaluate(subscription, now).covered()
                : CURRENT_STATUSES.contains(subscription.getStatus()) && isCurrentlyValid(subscription, now))
            .findFirst();
    }

    private static boolean isCurrentlyValid(Subscription subscription, Instant now) {
        Instant expiresAt = subscription.getGrantExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return false;
        }
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        return periodEnd == null || !periodEnd.isBefore(now);
    }

    public Plan getEffectivePlan(User user) {
        return getCurrentSubscription(user).map(Subscription::getPlan).orElseGet(this::getFreePlan);
    }

    public Plan getFreePlan() {
        return planRepository
            .findByCode(PlanCode.FREE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "FREE plan is not configured"));
    }
}
