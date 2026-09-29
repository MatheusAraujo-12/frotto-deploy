package com.localuz.service;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingInvoiceStatus;
import java.time.Duration;
import java.time.Instant;

/** Called only from authoritative financial ingestion while holding the subscription row lock. */
final class FleetRenewalEvidence {
    private FleetRenewalEvidence() {}

    static boolean belongs(Subscription s, BillingInvoice invoice) {
        return s.getNextRenewalAt() != null && invoice.getPeriodStart() != null
            && Duration.between(s.getNextRenewalAt(), invoice.getPeriodStart()).abs().compareTo(Duration.ofDays(1)) < 0;
    }

    static void expectedMoney(Subscription s, BillingInvoice invoice) {
        if (!belongs(s, invoice)) return;
        // Establish expected money before evaluating attempts, so a divergent payment cannot grant coverage.
        invoice.setAmount(s.getNextRenewalPrice()); invoice.setCurrency("BRL");
        invoice.setFleetVehicleCount(s.getNextRenewalVehicleCount());
        invoice.setFleetSnapshotToken(s.getNextRenewalToken());
        invoice.setFleetLockedAt(s.getNextRenewalLockedAt()); invoice.setFleetSyncedAt(s.getNextRenewalSyncedAt());
    }

    static void apply(Subscription s, BillingInvoice invoice, java.util.List<com.localuz.domain.PaymentAttempt> evidence) {
        if (!belongs(s, invoice) || s.getNextRenewalAppliedAt() != null) return;
        if (evidence.stream().anyMatch(attempt -> attempt.getStatus() == com.localuz.domain.enumeration.PaymentAttemptStatus.APPROVED
            && !BillingPaymentEvidence.moneyMatches(invoice, attempt.getAmount(), attempt.getCurrency()))) {
            s.setNextRenewalState("REVIEW");
            return;
        }
        if (invoice.getStatus() != BillingInvoiceStatus.PAID || evidence.stream().noneMatch(attempt -> BillingPaymentEvidence.approved(invoice, attempt))) return;
        s.setContractedPrice(invoice.getAmount()); s.setContractedVehicleCount(invoice.getFleetVehicleCount());
        s.setCurrentPeriodStart(invoice.getPeriodStart()); s.setCurrentPeriodEnd(invoice.getPeriodEnd());
        if (s.getPendingPlan() != null && s.getPendingPlan().getId().equals(s.getNextRenewalPlan().getId())) {
            s.setPendingContractedPrice(invoice.getAmount()); s.setPendingContractedVehicleCount(invoice.getFleetVehicleCount());
        }
        s.setNextRenewalAppliedAt(Instant.now()); s.setNextRenewalState("APPLIED");
    }
}
