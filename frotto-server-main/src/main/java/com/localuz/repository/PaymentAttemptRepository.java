package com.localuz.repository;

import com.localuz.domain.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByBillingInvoiceIdOrderByIdAsc(Long billingInvoiceId);
    Optional<PaymentAttempt> findByProviderAndExternalPaymentId(String provider, String externalPaymentId);
    Optional<PaymentAttempt> findByProviderAndExternalAttemptId(String provider, String externalAttemptId);
}
