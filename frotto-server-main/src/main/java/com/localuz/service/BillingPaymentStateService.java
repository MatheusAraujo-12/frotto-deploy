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
    public BillingPaymentStateService(SubscriptionRepository subscriptions,BillingCheckoutRepository checkouts){this.subscriptions=subscriptions;this.checkouts=checkouts;}
    public BillingPaymentStateDTO getState(User user){
        Subscription paid=subscriptions.findFirstByUserIdAndSourceOrderByStartDateDesc(user.getId(),SubscriptionSource.PAYMENT_PROVIDER).orElse(null);
        BillingCheckout checkout=checkouts.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        return new BillingPaymentStateDTO(paid,checkout);
    }
}
