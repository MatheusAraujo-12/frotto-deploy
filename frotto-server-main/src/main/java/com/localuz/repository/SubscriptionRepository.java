package com.localuz.repository;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the Subscription entity. */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByUserIdAndStatusInOrderByStartDateDesc(Long userId, List<SubscriptionStatus> statuses);

    List<Subscription> findByUserIdOrderByStartDateDesc(Long userId);
}
