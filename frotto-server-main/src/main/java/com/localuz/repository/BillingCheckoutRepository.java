package com.localuz.repository;
import com.localuz.domain.BillingCheckout;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
public interface BillingCheckoutRepository extends JpaRepository<BillingCheckout, Long> {
    Optional<BillingCheckout> findByExternalReference(String externalReference);
    Optional<BillingCheckout> findByProviderSubscriptionId(String providerSubscriptionId);
    Optional<BillingCheckout> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndStatusIn(Long userId, List<BillingCheckoutStatus> statuses);
}
