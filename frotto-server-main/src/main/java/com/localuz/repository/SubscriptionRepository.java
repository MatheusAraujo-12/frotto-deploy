package com.localuz.repository;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the Subscription entity. */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    /**
     * All ACTIVE/PAST_DUE rows for a user, most recently started first. Plural (not
     * findFirst...) because SubscriptionService must skip an expired ADMIN_GRANT and fall
     * through to the next one - see SubscriptionService#getCurrentSubscription.
     */
    List<Subscription> findByUserIdAndStatusInOrderByStartDateDesc(Long userId, List<SubscriptionStatus> statuses);

    /**
     * Used by SubscriptionAdminService to close a conflicting ADMIN_GRANT before creating a
     * new one - scoped to source=ADMIN_GRANT specifically so a PAYMENT_PROVIDER or
     * GRANDFATHERED subscription is never touched as a side effect of granting a plan.
     */
    List<Subscription> findByUserIdAndSourceAndStatusIn(Long userId, SubscriptionSource source, List<SubscriptionStatus> statuses);

    List<Subscription> findByUserIdOrderByStartDateDesc(Long userId);

    Optional<Subscription> findByExternalProviderAndExternalSubscriptionId(String provider, String subscriptionId);

    Optional<Subscription> findFirstByUserIdAndSourceOrderByStartDateDesc(Long userId, SubscriptionSource source);
}
