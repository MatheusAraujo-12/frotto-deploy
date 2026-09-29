package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import com.localuz.service.dto.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicFleetBillingTest {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UserRepository users = mock(UserRepository.class);
    CarRepository cars = mock(CarRepository.class);
    SubscriptionPlanUpgradeRepository upgrades = mock(SubscriptionPlanUpgradeRepository.class);
    PricingService pricing = mock(PricingService.class);
    MercadoPagoClient client = mock(MercadoPagoClient.class);
    DynamicFleetBillingSteps steps = new DynamicFleetBillingSteps(subscriptions, users, cars, upgrades, pricing);
    DynamicFleetBillingService service = new DynamicFleetBillingService(steps, client);
    Instant now = Instant.parse("2026-09-29T12:00:00Z");
    Instant due = now.plusSeconds(86400);
    Subscription subscription;
    Plan platinum;

    @BeforeEach void setup() {
        User user = new User(); user.setId(1L);
        platinum = new Plan(); platinum.setId(5L); platinum.setCode(PlanCode.PLATINUM); platinum.setMaxVehicles(100);
        subscription = new Subscription(); subscription.setId(7L); subscription.setUser(user); subscription.setPlan(platinum);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER); subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setContractedPrice(new BigDecimal("82.40")); subscription.setContractedVehicleCount(31);
        subscription.setExternalSubscriptionId("existing"); subscription.setCurrentPeriodEnd(due);
        when(subscriptions.findByIdForUpdate(7L)).thenReturn(Optional.of(subscription));
        when(users.findByIdForBillingCheckoutLock(1L)).thenReturn(Optional.of(user));
        when(subscriptions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(cars.countBillableByUserId(1L)).thenReturn(32L);
        when(pricing.calculatePriceForPlan(PlanCode.PLATINUM, 32)).thenReturn(new PricingResult(PlanCode.PLATINUM, "Platinum", 32, new BigDecimal("84.90"), List.of()));
    }

    MercadoPagoPreapproval remote(String amount) {
        return new MercadoPagoPreapproval("existing", "authorized", "reference", null, now.minusSeconds(100000), due, now,
            1, "months", new BigDecimal(amount), "BRL");
    }

    @Test void locksAtTwentyFourHoursAndConfirmsSamePreapprovalWithoutChangingCurrentCycle() {
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"), remote("84.90"));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalVehicleCount()).isEqualTo(32);
        assertThat(subscription.getNextRenewalPrice()).isEqualByComparingTo("84.90");
        assertThat(subscription.getNextRenewalState()).isEqualTo("SYNCED");
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("82.40");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(31);
        verify(client).updatePreapprovalAmount(eq("existing"), eq(new BigDecimal("84.90")), eq("BRL"), anyString());
        verify(client, never()).createPreapproval(any(), any());
        verify(client, never()).createPaymentPreference(any());
    }

    @Test void beforeWindowDoesNotCloseOrPut() {
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        service.reconcile(subscription, now.minusSeconds(1));
        assertThat(subscription.getNextRenewalLockedAt()).isNull();
        verify(client, never()).updatePreapprovalAmount(any(), any(), any(), any());
    }

    @Test void frottaUsesConfiguredPricingAtTheNewFleetCount() {
        platinum.setCode(PlanCode.FROTTA); platinum.setMaxVehicles(null);
        subscription.setContractedPrice(new BigDecimal("256.90")); subscription.setContractedVehicleCount(101);
        when(cars.countBillableByUserId(1L)).thenReturn(102L);
        when(pricing.calculatePriceForPlan(PlanCode.FROTTA, 102)).thenReturn(new PricingResult(PlanCode.FROTTA, "Frotta", 102, new BigDecimal("258.90"), List.of()));
        when(client.getPreapproval("existing")).thenReturn(remote("256.90"), remote("258.90"));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalPrice()).isEqualByComparingTo("258.90");
        assertThat(subscription.getNextRenewalVehicleCount()).isEqualTo(102);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("256.90");
        verify(client, never()).createPaymentPreference(any());
    }

    @Test void repeatedSchedulerAndFleetChangesKeepFrozenSnapshot() {
        when(client.getPreapproval("existing")).thenReturn(remote("84.90"));
        service.reconcile(subscription, now);
        when(cars.countBillableByUserId(1L)).thenReturn(33L);
        service.reconcile(subscription, now.plusSeconds(10));
        assertThat(subscription.getNextRenewalVehicleCount()).isEqualTo(32);
        verify(cars, times(1)).countBillableByUserId(1L);
        verify(client, never()).updatePreapprovalAmount(any(), any(), any(), any());
    }

    @Test void providerFailureLeavesRetryAndCurrentContractUnchanged() {
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        when(client.updatePreapprovalAmount(any(), any(), any(), any())).thenThrow(new MercadoPagoException("failure", false));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalSyncedAt()).isNull();
        assertThat(subscription.getNextRenewalState()).isEqualTo("RETRY");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(31);
    }

    @Test void unconfirmedGetDoesNotClaimSyncAndRetryUsesOriginalSnapshot() {
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        service.reconcile(subscription, now);
        String token = subscription.getNextRenewalToken();
        assertThat(subscription.getNextRenewalSyncedAt()).isNull();
        when(client.getPreapproval("existing")).thenReturn(remote("84.90"));
        service.reconcile(subscription, now.plusSeconds(1));
        assertThat(subscription.getNextRenewalState()).isEqualTo("SYNCED");
        assertThat(subscription.getNextRenewalToken()).isEqualTo(token);
        verify(client, times(1)).updatePreapprovalAmount(any(), any(), any(), any());
    }

    @Test void nearRenewalUnconfirmedSnapshotIsEscalated() {
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        service.reconcile(subscription, due.minusSeconds(60));
        assertThat(subscription.getNextRenewalState()).isEqualTo("REVIEW");
    }

    @Test void pendingProgressivePlanUsesFreshCountWithoutChangingIntent() {
        Plan frotta = new Plan(); frotta.setId(6L); frotta.setCode(PlanCode.FROTTA);
        subscription.setPlan(frotta); subscription.setPendingPlan(platinum); subscription.setPlanChangeProviderConfirmed(true);
        when(client.getPreapproval("existing")).thenReturn(remote("84.90"));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalPlan()).isSameAs(platinum);
        assertThat(subscription.getPendingPlan()).isSameAs(platinum);
        assertThat(subscription.getPlan()).isSameAs(frotta);
        verify(pricing).calculatePriceForPlan(PlanCode.PLATINUM, 32);
    }

    @Test void openUpgradePreventsClosing() {
        when(upgrades.findBySubscriptionIdAndStatusIn(eq(7L), any())).thenReturn(List.of(new SubscriptionPlanUpgrade()));
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalLockedAt()).isNull();
    }

    @Test void cancellationNeverCallsProvider() {
        subscription.setCancelAtPeriodEnd(true);
        service.reconcile(subscription, now);
        verifyNoInteractions(client);
    }

    @Test void undoInFlightPreventsClosing() {
        subscription.setPendingPlan(platinum); subscription.setPlanChangeToken("intent"); subscription.setPlanChangeProviderConfirmed(false);
        when(client.getPreapproval("existing")).thenReturn(remote("82.40"));
        service.reconcile(subscription, now);
        assertThat(subscription.getNextRenewalLockedAt()).isNull();
    }
}
