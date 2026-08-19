package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read/query-oriented foundation for subscriptions. Creation, upgrade, downgrade and
 * cancellation flows are intentionally not implemented in this stage - they depend on a
 * payment gateway that does not exist yet. A user with no ACTIVE/PAST_DUE Subscription
 * row is implicitly on the FREE plan; see the PlanCode/SubscriptionStatus javadocs for
 * why FREE is modeled as "absence of subscription" rather than as a status value.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final List<SubscriptionStatus> CURRENT_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    public Optional<Subscription> getCurrentSubscription(User user) {
        return subscriptionRepository.findFirstByUserIdAndStatusInOrderByStartDateDesc(user.getId(), CURRENT_STATUSES);
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
