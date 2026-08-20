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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read/query-oriented foundation for subscriptions. Real payment-gateway-driven creation,
 * upgrade, downgrade and cancellation flows are intentionally not implemented in this stage -
 * they depend on a payment gateway that does not exist yet. Administrative creation
 * (ADMIN_GRANT/GRANDFATHERED) is handled by SubscriptionAdminService/GrandfatheringService,
 * not here, so this class stays read-only. A user with no ACTIVE/PAST_DUE Subscription row
 * is implicitly on the FREE plan; see the PlanCode/SubscriptionStatus javadocs for why FREE
 * is modeled as "absence of subscription" rather than as a status value.
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
        .thenComparing(Subscription::getStartDate, Comparator.reverseOrder());

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    /**
     * The effective ACTIVE/PAST_DUE subscription, chosen by source priority (see
     * SOURCE_PRIORITY) among the ones that are still valid right now - not simply "most
     * recently started". A subscription is excluded from consideration (without being mutated
     * or deleted) if:
     * - it's an expired ADMIN_GRANT (grantExpiresAt in the past), or
     * - its billing period has already ended (currentPeriodEnd in the past, for whenever a
     *   real payment-gateway subscription sets one).
     * Both checks are evaluated dynamically on every call, with no scheduler involved. This is
     * what makes an expiring/revoked ADMIN_GRANT transparently fall back to the
     * PAYMENT_PROVIDER or GRANDFATHERED subscription underneath it, if one exists, and only
     * fall back to FREE when none remain.
     */
    public Optional<Subscription> getCurrentSubscription(User user) {
        return subscriptionRepository
            .findByUserIdAndStatusInOrderByStartDateDesc(user.getId(), CURRENT_STATUSES)
            .stream()
            .filter(SubscriptionService::isCurrentlyValid)
            .min(BY_EFFECTIVE_PRIORITY);
    }

    private static boolean isCurrentlyValid(Subscription subscription) {
        Instant now = Instant.now();
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
