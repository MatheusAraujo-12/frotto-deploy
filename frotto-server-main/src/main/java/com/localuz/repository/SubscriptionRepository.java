package com.localuz.repository;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for the Subscription entity. */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    interface FinancialCandidate {
        Long getId();
        String getExternalSubscriptionId();
    }

    @org.springframework.data.jpa.repository.Query(value = "select id as id, external_subscription_id as externalSubscriptionId " +
        "from subscription where source = 'PAYMENT_PROVIDER' and external_provider = 'MERCADO_PAGO' " +
        "and external_subscription_id is not null and trim(external_subscription_id) <> '' " +
        "and (last_financial_reconciliation_at is null or last_financial_reconciliation_at <= :cutoff) " +
        "order by last_financial_reconciliation_at asc, id asc", nativeQuery = true)
    List<FinancialCandidate> findFinancialCandidates(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
        org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "update subscription set last_financial_reconciliation_at = :now " +
        "where id = :id and source = 'PAYMENT_PROVIDER' and external_provider = 'MERCADO_PAGO' " +
        "and external_subscription_id = :externalId " +
        "and (last_financial_reconciliation_at is null or last_financial_reconciliation_at <= :cutoff)", nativeQuery = true)
    int reserveFinancialReconciliation(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("externalId") String externalId,
        @org.springframework.data.repository.query.Param("now") java.time.Instant now,
        @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    /** Serialize financial upserts for an existing provider subscription, including first insert. */
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.externalProvider = :provider and s.externalSubscriptionId = :externalId")
    Optional<Subscription> findForFinancialIngestion(
        @org.springframework.data.repository.query.Param("provider") String provider,
        @org.springframework.data.repository.query.Param("externalId") String externalId);

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
