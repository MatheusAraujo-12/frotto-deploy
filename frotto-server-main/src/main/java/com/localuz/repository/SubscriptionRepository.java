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

    /**
     * Cancelled PAYMENT_PROVIDER subscriptions remain candidates indefinitely UNLESS a reliable
     * terminal anchor exists and the horizon has elapsed (5G.7): anchor = max(billing_invoice
     * .period_end) for the subscription, falling back to subscription.canceled_at only when no
     * invoice period_end exists. If neither anchor exists, no date is fabricated and the
     * subscription stays a candidate (see docs/billing-hardening-5g7.md limitations). This only
     * gates polling/discovery - it never touches entitlement, history, or a later authoritative
     * webhook.
     */
    @org.springframework.data.jpa.repository.Query(value = "select s.id as id, s.external_subscription_id as externalSubscriptionId " +
        "from subscription s " +
        "left join (select subscription_id, max(period_end) as max_period_end from billing_invoice " +
        "  where period_end is not null group by subscription_id) inv on inv.subscription_id = s.id " +
        "where s.source = 'PAYMENT_PROVIDER' and s.external_provider = 'MERCADO_PAGO' " +
        "and s.external_subscription_id is not null and trim(s.external_subscription_id) <> '' " +
        "and (s.last_financial_reconciliation_at is null or s.last_financial_reconciliation_at <= :cutoff) " +
        "and (s.status <> 'CANCELED' " +
        "  or coalesce(inv.max_period_end, s.canceled_at) is null " +
        "  or :now < date_add(coalesce(inv.max_period_end, s.canceled_at), interval :horizonDays day)) " +
        "order by s.last_financial_reconciliation_at asc, s.id asc", nativeQuery = true)
    List<FinancialCandidate> findFinancialCandidates(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
        @org.springframework.data.repository.query.Param("now") java.time.Instant now,
        @org.springframework.data.repository.query.Param("horizonDays") int horizonDays,
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
