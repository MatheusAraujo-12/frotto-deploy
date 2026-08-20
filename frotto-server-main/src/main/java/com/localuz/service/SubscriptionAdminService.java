package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.GrantPlanRequestDTO;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative (ROLE_ADMIN-only) write operations on Subscription: manually granting a plan
 * and revoking a grant. Kept separate from the read-only SubscriptionService on purpose - see
 * that class's javadoc.
 *
 * A new grant only ever closes a conflicting ADMIN_GRANT the user already has - it never
 * touches a PAYMENT_PROVIDER or GRANDFATHERED subscription. This is deliberate: an admin grant
 * is meant to sit *on top of* a real subscription temporarily (e.g. "give this Platinum
 * customer FROTTA for 30 days as a courtesy"), not replace it. Once the grant expires or is
 * revoked, SubscriptionService's source-priority selection transparently falls back to
 * whatever PAYMENT_PROVIDER/GRANDFATHERED subscription was underneath it all along - nothing
 * needs to be restored, because it was never canceled in the first place. "Closed, not
 * deleted" still applies to the ADMIN_GRANT itself - see revoke()'s javadoc for why this never
 * uses DELETE.
 *
 * ADMIN_GRANT subscriptions never represent a real charge (no payment gateway exists yet):
 * contractedPrice is always 0.00 and billingCycle is always MONTHLY (arbitrary - the column is
 * NOT NULL but nothing here is actually billed on any cycle). See Subscription's javadoc for
 * why contractedPrice isn't used to store a "reference price" instead.
 */
@Service
@Transactional
public class SubscriptionAdminService {

    private static final String ENTITY_NAME = "adminBillingGrant";
    private static final List<SubscriptionStatus> OPEN_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;

    public SubscriptionAdminService(
        SubscriptionRepository subscriptionRepository,
        PlanRepository planRepository,
        UserRepository userRepository,
        CarRepository carRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.carRepository = carRepository;
    }

    public Subscription grantPlan(GrantPlanRequestDTO request, User grantedByAdmin) {
        User targetUser = userRepository
            .findById(request.getUserId())
            .orElseThrow(() -> new BadRequestAlertException("User not found", ENTITY_NAME, "usernotfound"));

        Plan plan = planRepository
            .findByCode(request.getPlanCode())
            .orElseThrow(() -> new BadRequestAlertException("Plan not found", ENTITY_NAME, "plannotfound"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestAlertException("Plan is not active", ENTITY_NAME, "planinactive");
        }
        if (plan.getCode() == PlanCode.FREE) {
            // FREE is "no subscription" by design (see SubscriptionSource javadoc) - granting
            // it as an ADMIN_GRANT row would fight that model instead of just revoking whatever
            // grant the user currently has.
            throw new BadRequestAlertException(
                "FREE cannot be granted; revoke the user's current grant instead",
                ENTITY_NAME,
                "freeplanotgrantable"
            );
        }
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now())) {
            throw new BadRequestAlertException("expiresAt must be in the future", ENTITY_NAME, "expiresatinpast");
        }

        closeConflictingAdminGrants(targetUser);

        Subscription grant = new Subscription();
        grant.setUser(targetUser);
        grant.setPlan(plan);
        grant.setBillingCycle(BillingCycle.MONTHLY);
        grant.setStatus(SubscriptionStatus.ACTIVE);
        grant.setSource(SubscriptionSource.ADMIN_GRANT);
        grant.setContractedPrice(BigDecimal.ZERO.setScale(2));
        grant.setContractedVehicleCount((int) carRepository.countByUserIdAndActiveTrue(targetUser.getId()));
        grant.setGrantedBy(grantedByAdmin);
        grant.setGrantedAt(Instant.now());
        grant.setGrantReason(request.getReason());
        grant.setGrantExpiresAt(request.getExpiresAt());

        return subscriptionRepository.save(grant);
    }

    /**
     * Marks an ADMIN_GRANT subscription CANCELED. Never a physical DELETE - Billing history
     * must stay reconstructable (who granted what, when, and when it stopped being current).
     * Idempotent: revoking an already-CANCELED grant returns it unchanged rather than
     * overwriting its original canceledAt.
     */
    public Subscription revokeGrant(Long subscriptionId) {
        Subscription subscription = subscriptionRepository
            .findById(subscriptionId)
            .orElseThrow(() -> new BadRequestAlertException("Subscription not found", ENTITY_NAME, "subscriptionnotfound"));

        if (subscription.getSource() != SubscriptionSource.ADMIN_GRANT) {
            throw new BadRequestAlertException(
                "Only ADMIN_GRANT subscriptions can be revoked through this endpoint",
                ENTITY_NAME,
                "notanadmingrant"
            );
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return subscription;
        }

        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(Instant.now());
        return subscriptionRepository.save(subscription);
    }

    /**
     * Closes every ADMIN_GRANT row still ACTIVE/PAST_DUE for this user (normally at most one,
     * but this is defensive rather than trusting that invariant blindly) - see this class's
     * javadoc for why PAYMENT_PROVIDER/GRANDFATHERED are never touched here.
     */
    private void closeConflictingAdminGrants(User user) {
        List<Subscription> conflicting = subscriptionRepository.findByUserIdAndSourceAndStatusIn(
            user.getId(),
            SubscriptionSource.ADMIN_GRANT,
            OPEN_STATUSES
        );
        Instant now = Instant.now();
        for (Subscription previous : conflicting) {
            previous.setStatus(SubscriptionStatus.CANCELED);
            previous.setCanceledAt(now);
            subscriptionRepository.save(previous);
        }
    }
}
