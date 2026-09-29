package com.localuz.service;

import static org.assertj.core.api.Assertions.*;
import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FleetRenewalEvidenceTest {
    Subscription subscription = new Subscription();
    BillingInvoice invoice = new BillingInvoice();
    PaymentAttempt payment = new PaymentAttempt();
    Instant due = Instant.parse("2026-10-01T12:00:00Z");
    @BeforeEach void setup() {
        subscription.setContractedPrice(new BigDecimal("82.40")); subscription.setContractedVehicleCount(31);
        subscription.setNextRenewalAt(due); subscription.setNextRenewalPrice(new BigDecimal("84.90"));
        subscription.setNextRenewalVehicleCount(32); subscription.setNextRenewalLockedAt(due.minusSeconds(86400));
        invoice.setSubscription(subscription); invoice.setPeriodStart(due.minusSeconds(180)); invoice.setPeriodEnd(due.plusSeconds(2678400));
        invoice.setAmount(new BigDecimal("84.90")); invoice.setCurrency("BRL"); invoice.setStatus(BillingInvoiceStatus.PAID);
        payment.setBillingInvoice(invoice); payment.setStatus(PaymentAttemptStatus.APPROVED);
        payment.setAmount(new BigDecimal("84.90")); payment.setCurrency("BRL");
    }
    @Test void confirmedRenewalUsesFrozenCountAndActuallyPaidAmount() {
        FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("84.90");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(32);
        assertThat(invoice.getFleetVehicleCount()).isEqualTo(32);
        assertThat(subscription.getNextRenewalState()).isEqualTo("APPLIED");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(invoice.getPeriodEnd());
    }
    @Test void amountMismatchNeverUpdatesContractAndRequiresReview() {
        payment.setAmount(new BigDecimal("82.40"));
        FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("82.40");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(31);
        assertThat(subscription.getNextRenewalState()).isEqualTo("REVIEW");
    }
    @Test void currencyMismatchNeverUpdatesContract() {
        payment.setCurrency("USD"); FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getNextRenewalAppliedAt()).isNull();
    }
    @Test void previousCompetencyCannotConsumeNewSnapshot() {
        invoice.setPeriodStart(due.minusSeconds(2592000));
        FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(31);
    }
    @Test void reversalNeverAppliesTheSnapshot() {
        payment.setStatus(PaymentAttemptStatus.REFUNDED);
        FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(31);
    }
    @Test void pendingProgressiveDowngradeReceivesExactPaidSnapshot() {
        Plan target = new Plan(); target.setId(3L);
        subscription.setPendingPlan(target); subscription.setNextRenewalPlan(target);
        FleetRenewalEvidence.expectedMoney(subscription, invoice);
        FleetRenewalEvidence.apply(subscription, invoice, List.of(payment));
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("84.90");
        assertThat(subscription.getPendingContractedVehicleCount()).isEqualTo(32);
        assertThat(subscription.getPendingPlan()).isSameAs(target);
    }
}
