package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.*;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicFleetBillingSteps {
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final CarRepository cars;
    private final SubscriptionPlanUpgradeRepository upgrades;
    private final PricingService pricing;

    public DynamicFleetBillingSteps(SubscriptionRepository subscriptions, UserRepository users, CarRepository cars,
        SubscriptionPlanUpgradeRepository upgrades, PricingService pricing) {
        this.subscriptions = subscriptions; this.users = users; this.cars = cars; this.upgrades = upgrades; this.pricing = pricing;
    }

    public static boolean locked(Subscription s) {
        return s.getNextRenewalLockedAt() != null && s.getNextRenewalAppliedAt() == null;
    }

    public static boolean eligible(Subscription s) {
        return s.getSource() == SubscriptionSource.PAYMENT_PROVIDER && s.getStatus() == SubscriptionStatus.ACTIVE
            && !Boolean.TRUE.equals(s.getCancelAtPeriodEnd()) && s.getCanceledAt() == null;
    }

    @Transactional
    public Subscription lockSnapshot(Long userId, Long subscriptionId, MercadoPagoPreapproval remote, Instant now) {
        users.findByIdForBillingCheckoutLock(userId).orElseThrow();
        Subscription s = subscriptions.findByIdForUpdate(subscriptionId).orElseThrow();
        if (!eligible(s) || !Objects.equals(s.getExternalSubscriptionId(), remote.getId())
            || !"authorized".equals(remote.getStatus()) || !"BRL".equals(remote.getCurrencyId())) return null;
        if (s.getPlanChangeOperationUntil() != null && s.getPlanChangeOperationUntil().isAfter(now)) return null;
        if (!upgrades.findBySubscriptionIdAndStatusIn(s.getId(), SubscriptionPlanChangeSteps.OPEN_UPGRADE_STATUSES).isEmpty()) return null;
        // Legacy schedules have no token. A fresh provider GET can confirm those without trusting a migration guess.
        if (s.getPendingPlan() != null && s.getPlanChangeToken() == null && remote.getTransactionAmount() != null
            && s.getPendingContractedPrice() != null && remote.getTransactionAmount().compareTo(s.getPendingContractedPrice()) == 0) {
            s.setPlanChangeProviderConfirmed(true);
        }
        if (s.getPendingPlan() != null && !Boolean.TRUE.equals(s.getPlanChangeProviderConfirmed())) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal subscriptionId={} outcome=PLAN_CHANGE_UNCONFIRMED", s.getId());
            return null;
        }
        if (locked(s)) return s;
        Instant due = remote.getNextPaymentDate();
        if (due == null || !due.isAfter(now) || due.isAfter(now.plus(Duration.ofHours(24)))) return null;
        if (s.getNextRenewalAt() != null && !due.isAfter(s.getNextRenewalAt())) return null;
        var plan = s.getPendingPlan() != null ? s.getPendingPlan() : s.getPlan();
        int count = Math.toIntExact(cars.countBillableByUserId(userId));
        var price = pricing.calculatePriceForPlan(plan.getCode(), count).getMonthlyPrice();
        s.setNextRenewalAt(due); s.setNextRenewalPlan(plan); s.setNextRenewalPrice(price);
        s.setNextRenewalVehicleCount(count); s.setNextRenewalLockedAt(now); s.setNextRenewalSyncedAt(null);
        s.setNextRenewalAppliedAt(null); s.setNextRenewalToken(UUID.randomUUID().toString()); s.setNextRenewalState("LOCKED");
        return subscriptions.saveAndFlush(s);
    }

    @Transactional
    public void confirmed(Long id, String token, Instant now) {
        Subscription s = subscriptions.findByIdForUpdate(id).orElseThrow();
        if (!eligible(s) || !locked(s) || !Objects.equals(token, s.getNextRenewalToken())) return;
        s.setNextRenewalSyncedAt(now); s.setNextRenewalState("SYNCED");
        subscriptions.saveAndFlush(s);
    }

    @Transactional
    public void unconfirmed(Long id, String token, Instant now) {
        Subscription s = subscriptions.findByIdForUpdate(id).orElseThrow();
        if (!locked(s) || !Objects.equals(token, s.getNextRenewalToken()) || s.getNextRenewalSyncedAt() != null) return;
        s.setNextRenewalState(s.getNextRenewalAt().isBefore(now.plus(Duration.ofHours(1))) ? "REVIEW" : "RETRY");
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal subscriptionId={} outcome={}", s.getId(), s.getNextRenewalState());
        subscriptions.saveAndFlush(s);
    }
}
