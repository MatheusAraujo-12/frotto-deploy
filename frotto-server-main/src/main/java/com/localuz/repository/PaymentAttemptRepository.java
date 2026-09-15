package com.localuz.repository;

import com.localuz.domain.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    /** Snapshot read for entitlement; deliberately does not acquire ingestion's write locks. */
    List<PaymentAttempt> findByBillingInvoiceSubscriptionId(Long subscriptionId);
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<PaymentAttempt> findByBillingInvoiceIdOrderByIdAsc(Long billingInvoiceId);
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByProviderAndExternalPaymentId(String provider, String externalPaymentId);
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByProviderAndExternalAttemptId(String provider, String externalAttemptId);
}
