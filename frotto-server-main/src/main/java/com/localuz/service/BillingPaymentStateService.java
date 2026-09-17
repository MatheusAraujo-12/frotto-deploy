package com.localuz.service;

import com.localuz.domain.BillingCheckout;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.repository.BillingCheckoutRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.BillingPaymentStateDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BillingPaymentStateService {
    private final SubscriptionRepository subscriptions; private final BillingCheckoutRepository checkouts;
    private final SubscriptionFinancialCoverageService financialCoverage;
    public BillingPaymentStateService(SubscriptionRepository subscriptions, BillingCheckoutRepository checkouts,
        SubscriptionFinancialCoverageService financialCoverage) {
        this.subscriptions=subscriptions;this.checkouts=checkouts;this.financialCoverage=financialCoverage;
    }
    /**
     * The raw PAYMENT_PROVIDER Subscription.status is exposed here for every terminal/pending state
     * (5G.5 reconciliation and UI messaging both need it), but per the 5G rule status=ACTIVE alone is
     * never financial proof - financiallyCovered carries the actual SubscriptionFinancialCoverageService
     * verdict so the frontend can tell "checkout/preapproval confirmed" apart from "payment financially
     * confirmed" instead of inferring it from status.
     */
    public BillingPaymentStateDTO getState(User user){
        Subscription paid=subscriptions.findFirstByUserIdAndSourceOrderByStartDateDesc(user.getId(),SubscriptionSource.PAYMENT_PROVIDER).orElse(null);
        boolean covered = paid != null && financialCoverage.evaluate(paid).covered();
        BillingCheckout checkout=checkouts.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        return new BillingPaymentStateDTO(paid,covered,checkout);
    }
}
