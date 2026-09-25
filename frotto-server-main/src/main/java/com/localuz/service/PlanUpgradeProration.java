package com.localuz.service;

import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 5G.12.1: pure prorated-upgrade arithmetic plus the resolution of the cycle it is applied to.
 * Never estimates: any missing/inconsistent input yields "no cycle" or an exception, and the caller
 * refuses the upgrade (fail closed) instead of guessing a charge.
 */
public final class PlanUpgradeProration {

    public static final int MONEY_SCALE = 2;
    /** Single, explicit rounding step applied to the final amount only - never to intermediate ratios. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public record Cycle(Instant start, Instant end) {}

    private PlanUpgradeProration() {}

    /**
     * upgradeCharge = (targetPrice - currentPrice) * (cycleEnd - now) / (cycleEnd - cycleStart),
     * computed at millisecond precision and rounded once to 2 decimals (HALF_UP). A non-positive
     * price difference charges nothing (0.00) - never a negative amount / credit.
     *
     * @throws IllegalArgumentException when a value is missing, the cycle is empty/inverted, or
     *     {@code now} is not strictly inside [cycleStart, cycleEnd).
     */
    public static BigDecimal charge(BigDecimal currentPrice, BigDecimal targetPrice, Instant cycleStart, Instant cycleEnd, Instant now) {
        if (currentPrice == null || targetPrice == null || cycleStart == null || cycleEnd == null || now == null) {
            throw new IllegalArgumentException("Proration input is incomplete");
        }
        if (currentPrice.signum() < 0 || targetPrice.signum() < 0) {
            throw new IllegalArgumentException("Prices cannot be negative");
        }
        if (!cycleStart.isBefore(cycleEnd)) {
            throw new IllegalArgumentException("Cycle must have a positive duration");
        }
        if (now.isBefore(cycleStart) || !now.isBefore(cycleEnd)) {
            throw new IllegalArgumentException("Now is outside the current cycle");
        }
        BigDecimal difference = targetPrice.subtract(currentPrice);
        if (difference.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        long remaining = Duration.between(now, cycleEnd).toMillis();
        long total = Duration.between(cycleStart, cycleEnd).toMillis();
        BigDecimal charge = difference.multiply(BigDecimal.valueOf(remaining)).divide(BigDecimal.valueOf(total), MONEY_SCALE, ROUNDING);
        return charge.signum() < 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE) : charge;
    }

    /**
     * The cycle the user has already paid for, from the most authoritative source available:
     *
     * 1. A PAID financial competency (SubscriptionFinancialCoverageService) - its invoice period is
     *    already anchored on the provider's own debit date + recurrence (MercadoPagoInvoiceTemporalEnricher).
     * 2. Only when there is no invoice evidence at all yet (the same narrow NO_INVOICE case
     *    SubscriptionService#isProviderSubscriptionEntitled already accepts for an authorized
     *    contract): the freshly GET'd preapproval's next_payment_date is the cycle end, and the start
     *    is that date minus auto_recurring.frequency calendar months in the provider's own offset.
     *    That is the provider's recurrence contract, not an invented fixed 30-day duration.
     *
     * Anything else (grace, past due, conflicts, expired, missing/other frequency units, a start
     * before the subscription itself began) returns empty - the caller must refuse the upgrade.
     */
    public static Optional<Cycle> resolveCycle(FinancialCoverageEvaluation evaluation, MercadoPagoPreapproval preapproval,
        Instant subscriptionStart, Instant now) {
        if (evaluation == null || now == null) return Optional.empty();
        if (evaluation.reason() == FinancialCoverageEvaluation.Reason.PAID) {
            return valid(evaluation.coverageStart(), evaluation.coverageEnd(), now);
        }
        if (evaluation.reason() != FinancialCoverageEvaluation.Reason.NO_INVOICE || preapproval == null) {
            return Optional.empty();
        }
        OffsetDateTime next = preapproval.getNextPaymentDateWithOffset();
        Integer frequency = preapproval.getFrequency();
        if (next == null || frequency == null || frequency <= 0 || !"months".equals(preapproval.getFrequencyType())) {
            return Optional.empty();
        }
        Instant start;
        try {
            start = next.minusMonths(frequency).toInstant();
        } catch (DateTimeException | ArithmeticException invalid) {
            return Optional.empty();
        }
        // The first cycle starts when the contract started (at most a few minutes before its
        // first charge); a derived start meaningfully earlier than that means the provider data
        // does not describe the cycle we think it does.
        if (subscriptionStart != null && start.isBefore(subscriptionStart.minus(Duration.ofDays(1)))) {
            return Optional.empty();
        }
        return valid(start, next.toInstant(), now);
    }

    private static Optional<Cycle> valid(Instant start, Instant end, Instant now) {
        if (start == null || end == null || !start.isBefore(end) || now.isBefore(start) || !now.isBefore(end)) {
            return Optional.empty();
        }
        return Optional.of(new Cycle(start, end));
    }
}
