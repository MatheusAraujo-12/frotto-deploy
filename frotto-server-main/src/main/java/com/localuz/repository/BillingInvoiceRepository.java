package com.localuz.repository;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.enumeration.BillingInvoiceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence queries only; callers remain responsible for financial reconciliation. */
public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, Long> {
    List<BillingInvoice> findBySubscriptionIdOrderByPeriodStartAsc(Long subscriptionId);
    Optional<BillingInvoice> findByProviderAndExternalAuthorizedPaymentId(String provider, String externalAuthorizedPaymentId);
    Optional<BillingInvoice> findBySubscriptionIdAndPeriodStartAndPeriodEnd(Long subscriptionId, Instant periodStart, Instant periodEnd);
    Page<BillingInvoice> findByStatusAndDueAtLessThanEqual(BillingInvoiceStatus status, Instant dueAt, Pageable pageable);
    Page<BillingInvoice> findByStatusAndGracePeriodEndLessThanEqual(BillingInvoiceStatus status, Instant gracePeriodEnd, Pageable pageable);
    Page<BillingInvoice> findByLastReconciledAtIsNullOrLastReconciledAtBefore(Instant cutoff, Pageable pageable);
}
