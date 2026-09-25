package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState;
import com.localuz.service.dto.FinancialCoverageEvaluation.Reason;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** 5G.12.1 section G: prorated arithmetic and cycle resolution - BigDecimal, one explicit rounding, fail closed. */
class PlanUpgradeProrationTest {

    private static final BigDecimal BRONZE = new BigDecimal("15.90");
    private static final BigDecimal SILVER = new BigDecimal("44.90");
    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-01T00:00:00Z"); // 30 days

    @Test
    void cycleJustStartedChargesTheFullDifference() {
        assertThat(PlanUpgradeProration.charge(BRONZE, SILVER, START, END, START)).isEqualByComparingTo("29.00");
    }

    @Test
    void partiallyConsumedCycleChargesTheRemainingFraction() {
        // 10 of 30 days left: 29.00 / 3 = 9.6666... -> 9.67
        Instant now = END.minus(Duration.ofDays(10));
        assertThat(PlanUpgradeProration.charge(BRONZE, SILVER, START, END, now)).isEqualByComparingTo("9.67");
        // Half the cycle: 14.50 exactly
        assertThat(PlanUpgradeProration.charge(BRONZE, SILVER, START, END, START.plus(Duration.ofDays(15)))).isEqualByComparingTo("14.50");
    }

    @Test
    void nearTheEndChargesAFewCentsAndNeverANegativeAmount() {
        // 1 hour left of 720: 29.00 / 720 = 0.04027... -> 0.04
        assertThat(PlanUpgradeProration.charge(BRONZE, SILVER, START, END, END.minus(Duration.ofHours(1)))).isEqualByComparingTo("0.04");
        // 1 second left rounds to zero, never below.
        BigDecimal lastSecond = PlanUpgradeProration.charge(BRONZE, SILVER, START, END, END.minusSeconds(1));
        assertThat(lastSecond).isEqualByComparingTo("0.00");
        assertThat(lastSecond.signum()).isZero();
    }

    @Test
    void resultAlwaysHasMoneyScaleAndUsesHalfUpOnlyOnce() {
        // 2/3 of 0.05 = 0.0333... -> 0.03 ; 5/6 of 0.05 = 0.041666 -> 0.04 ; exact half-cent rounds up.
        BigDecimal charge = PlanUpgradeProration.charge(new BigDecimal("10.00"), new BigDecimal("10.05"), START, END, START.plus(Duration.ofDays(10)));
        assertThat(charge.scale()).isEqualTo(2);
        assertThat(charge).isEqualByComparingTo("0.03");
        Instant half = START.plus(Duration.ofDays(15));
        assertThat(PlanUpgradeProration.charge(new BigDecimal("10.00"), new BigDecimal("10.01"), START, END, half)).isEqualByComparingTo("0.01");
        assertThat(PlanUpgradeProration.ROUNDING).isEqualTo(java.math.RoundingMode.HALF_UP);
    }

    @Test
    void targetNotMoreExpensiveChargesNothing() {
        assertThat(PlanUpgradeProration.charge(SILVER, SILVER, START, END, START)).isEqualByComparingTo("0.00");
        assertThat(PlanUpgradeProration.charge(SILVER, BRONZE, START, END, START)).isEqualByComparingTo("0.00");
    }

    @Test
    void invalidPeriodsFailClosed() {
        assertThatThrownBy(() -> PlanUpgradeProration.charge(BRONZE, SILVER, END, START, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(BRONZE, SILVER, START, START, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(BRONZE, SILVER, START, END, END)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(BRONZE, SILVER, START, END, START.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(BRONZE, SILVER, null, END, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(null, SILVER, START, END, START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanUpgradeProration.charge(new BigDecimal("-1"), SILVER, START, END, START)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Cycle resolution ---------------------------------------------------------------------------

    private static FinancialCoverageEvaluation evaluation(Reason reason, Instant start, Instant end) {
        return new FinancialCoverageEvaluation(reason == Reason.PAID, CommercialState.ACTIVE, 1L, start, end, null, reason);
    }

    private static MercadoPagoPreapproval preapproval(OffsetDateTime next, Integer frequency, String type) {
        return new MercadoPagoPreapproval("pre-1", "authorized", "ref", null, null, next == null ? null : next.toInstant(), null,
            frequency, type, SILVER, "BRL", next);
    }

    @Test
    void paidCompetencyIsTheAuthoritativeCycle() {
        Instant now = START.plus(Duration.ofDays(3));
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.PAID, START, END), null, START, now))
            .contains(new PlanUpgradeProration.Cycle(START, END));
    }

    @Test
    void withoutAnyInvoiceTheCycleComesFromTheProviderRecurrenceInItsOwnOffset() {
        // Mercado Pago dates carry -04:00; subtracting one calendar month in that offset.
        OffsetDateTime next = OffsetDateTime.parse("2026-10-15T10:00:00.000-04:00");
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        var cycle = PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(next, 1, "months"),
            Instant.parse("2026-09-15T13:59:00Z"), now);
        assertThat(cycle).contains(new PlanUpgradeProration.Cycle(Instant.parse("2026-09-15T14:00:00Z"), Instant.parse("2026-10-15T14:00:00Z")));
    }

    @Test
    void unknownFrequencyStaleNextPaymentOrNonPaidStatesAreNotACycle() {
        OffsetDateTime next = OffsetDateTime.parse("2026-10-15T10:00:00.000-04:00");
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(next, 1, "days"), null, now)).isEmpty();
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(next, null, "months"), null, now)).isEmpty();
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(null, 1, "months"), null, now)).isEmpty();
        // next_payment_date already in the past
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(next, 1, "months"), null,
            Instant.parse("2026-10-20T00:00:00Z"))).isEmpty();
        // Derived start long before the contract began
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.NO_INVOICE, null, null), preapproval(next, 1, "months"),
            Instant.parse("2026-09-30T00:00:00Z"), now)).isEmpty();
        for (Reason reason : new Reason[] {Reason.GRACE, Reason.GRACE_EXPIRED, Reason.FINANCIAL_CONFLICT, Reason.PERIOD_EXPIRED, Reason.INCOMPLETE_PERIOD}) {
            assertThat(PlanUpgradeProration.resolveCycle(evaluation(reason, START, END), preapproval(next, 1, "months"), null, now)).isEmpty();
        }
    }

    @Test
    void paidCompetencyThatDoesNotContainNowIsNotACycle() {
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.PAID, START, END), null, null, END)).isEmpty();
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.PAID, END, START), null, null, START)).isEmpty();
        assertThat(PlanUpgradeProration.resolveCycle(evaluation(Reason.PAID, null, END), null, null, START)).isEmpty();
    }
}
