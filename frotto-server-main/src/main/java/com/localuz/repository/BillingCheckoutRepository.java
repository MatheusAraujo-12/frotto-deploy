package com.localuz.repository;
import com.localuz.domain.BillingCheckout;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
public interface BillingCheckoutRepository extends JpaRepository<BillingCheckout, Long> {
    Optional<BillingCheckout> findByExternalReference(String externalReference);
    Optional<BillingCheckout> findByProviderSubscriptionId(String providerSubscriptionId);
    Optional<BillingCheckout> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<BillingCheckout> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<BillingCheckoutStatus> statuses);
    boolean existsByUserIdAndStatusIn(Long userId, List<BillingCheckoutStatus> statuses);
    List<BillingCheckout> findByStatusInAndProviderSubscriptionIdIsNotNullAndCreatedAtBefore(List<BillingCheckoutStatus> statuses, Instant cutoff);
}
