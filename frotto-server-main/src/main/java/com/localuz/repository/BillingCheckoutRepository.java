package com.localuz.repository;
import com.localuz.domain.BillingCheckout;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BillingCheckoutRepository extends JpaRepository<BillingCheckout, Long> {
    Optional<BillingCheckout> findByExternalReference(String externalReference);
}
