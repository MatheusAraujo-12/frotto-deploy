package com.localuz.repository;

import com.localuz.domain.SubscriptionPlanUpgrade;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 5G.12.1: prorated upgrade attempts - see SubscriptionPlanUpgrade. */
public interface SubscriptionPlanUpgradeRepository extends JpaRepository<SubscriptionPlanUpgrade, Long> {
    List<SubscriptionPlanUpgrade> findBySubscriptionIdAndStatusIn(Long subscriptionId, Collection<SubscriptionPlanUpgradeStatus> statuses);

    List<SubscriptionPlanUpgrade> findBySubscriptionUserIdAndStatusIn(Long userId, Collection<SubscriptionPlanUpgradeStatus> statuses);

    Optional<SubscriptionPlanUpgrade> findFirstBySubscriptionUserIdOrderByIdDesc(Long userId);

    Optional<SubscriptionPlanUpgrade> findByExternalReference(String externalReference);

    List<SubscriptionPlanUpgrade> findByStatusInOrderByUpdatedAtAsc(Collection<SubscriptionPlanUpgradeStatus> statuses, Pageable pageable);

    /** Serializes every state transition of one attempt (webhook, polling, scheduler and user request may race). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SubscriptionPlanUpgrade u where u.id = :id")
    Optional<SubscriptionPlanUpgrade> findByIdForUpdate(@Param("id") Long id);
}
