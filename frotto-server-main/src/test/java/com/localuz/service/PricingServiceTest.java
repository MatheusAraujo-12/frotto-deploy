package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.PlanPricingTier;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.service.dto.PricingResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

/**
 * Plain unit tests (no Spring context) for PricingService, mocking the plan/tier
 * repositories so the exact configuration seeded by the Plan/PlanPricingTier migrations
 * is reproduced in-memory. All expected values here were independently validated against
 * the ticket's worked examples before this class was written.
 */
class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        PlanRepository planRepository = Mockito.mock(PlanRepository.class);
        PlanPricingTierRepository tierRepository = Mockito.mock(PlanPricingTierRepository.class);

        Plan free = plan(1L, PlanCode.FREE, 0, 2, "0.00");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(3L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(4L, PlanCode.GOLD, 16, 30, "79.90");
        Plan platinum = plan(5L, PlanCode.PLATINUM, 31, 100, "79.90");
        Plan frotta = plan(6L, PlanCode.FROTTA, 101, null, "254.90");

        when(planRepository.findByActiveTrueOrderByMinVehiclesAsc())
            .thenReturn(List.of(free, bronze, silver, gold, platinum, frotta));

        when(tierRepository.findByPlanIdOrderByTierOrderAsc(free.getId())).thenReturn(List.of());
        when(tierRepository.findByPlanIdOrderByTierOrderAsc(bronze.getId())).thenReturn(List.of());
        when(tierRepository.findByPlanIdOrderByTierOrderAsc(silver.getId())).thenReturn(List.of());
        when(tierRepository.findByPlanIdOrderByTierOrderAsc(gold.getId())).thenReturn(List.of());
        when(tierRepository.findByPlanIdOrderByTierOrderAsc(platinum.getId()))
            .thenReturn(List.of(tier(platinum, 1, 31, 100, "2.50")));
        when(tierRepository.findByPlanIdOrderByTierOrderAsc(frotta.getId()))
            .thenReturn(
                List.of(
                    tier(frotta, 1, 101, 200, "2.00"),
                    tier(frotta, 2, 201, 500, "1.50"),
                    tier(frotta, 3, 501, null, "1.00")
                )
            );

        pricingService = new PricingService(planRepository, tierRepository);
    }

    private static Plan plan(Long id, PlanCode code, int min, Integer max, String price) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(code.name());
        plan.setMinVehicles(min);
        plan.setMaxVehicles(max);
        plan.setMonthlyBasePrice(new BigDecimal(price));
        plan.setActive(true);
        return plan;
    }

    private static PlanPricingTier tier(Plan plan, int order, int from, Integer to, String unitPrice) {
        PlanPricingTier tier = new PlanPricingTier();
        tier.setPlan(plan);
        tier.setTierOrder(order);
        tier.setFromVehicleCount(from);
        tier.setToVehicleCount(to);
        tier.setPricePerVehicle(new BigDecimal(unitPrice));
        return tier;
    }

    private static Stream<Arguments> officialExamples() {
        return Stream.of(
            Arguments.of(0, PlanCode.FREE, "0.00"),
            Arguments.of(1, PlanCode.FREE, "0.00"),
            Arguments.of(2, PlanCode.FREE, "0.00"),
            Arguments.of(3, PlanCode.BRONZE, "15.90"),
            Arguments.of(5, PlanCode.BRONZE, "15.90"),
            Arguments.of(6, PlanCode.SILVER, "44.90"),
            Arguments.of(15, PlanCode.SILVER, "44.90"),
            Arguments.of(16, PlanCode.GOLD, "79.90"),
            Arguments.of(30, PlanCode.GOLD, "79.90"),
            Arguments.of(31, PlanCode.PLATINUM, "82.40"),
            Arguments.of(50, PlanCode.PLATINUM, "129.90"),
            Arguments.of(100, PlanCode.PLATINUM, "254.90"),
            Arguments.of(101, PlanCode.FROTTA, "256.90"),
            Arguments.of(150, PlanCode.FROTTA, "354.90"),
            Arguments.of(200, PlanCode.FROTTA, "454.90"),
            Arguments.of(201, PlanCode.FROTTA, "456.40"),
            Arguments.of(300, PlanCode.FROTTA, "604.90"),
            Arguments.of(500, PlanCode.FROTTA, "904.90"),
            Arguments.of(501, PlanCode.FROTTA, "905.90"),
            Arguments.of(1000, PlanCode.FROTTA, "1404.90")
        );
    }

    @ParameterizedTest(name = "{0} vehicles -> {1} / R$ {2}")
    @MethodSource("officialExamples")
    void matchesOfficialExamples(int vehicleCount, PlanCode expectedPlan, String expectedPrice) {
        PricingResult result = pricingService.calculateMonthlyPrice(vehicleCount);

        assertThat(result.getPlanCode()).isEqualTo(expectedPlan);
        assertThat(result.getVehicleCount()).isEqualTo(vehicleCount);
        assertThat(result.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal(expectedPrice));
    }

    private static Stream<Arguments> planTransitionBoundaries() {
        // (vehicleCountJustBelow, planJustBelow, vehicleCountJustAbove, planJustAbove)
        return Stream.of(
            Arguments.of(2, PlanCode.FREE, 3, PlanCode.BRONZE),
            Arguments.of(5, PlanCode.BRONZE, 6, PlanCode.SILVER),
            Arguments.of(15, PlanCode.SILVER, 16, PlanCode.GOLD),
            Arguments.of(30, PlanCode.GOLD, 31, PlanCode.PLATINUM),
            Arguments.of(100, PlanCode.PLATINUM, 101, PlanCode.FROTTA)
        );
    }

    @ParameterizedTest(name = "boundary {0}->{2}: plan flips from {1} to {3}")
    @MethodSource("planTransitionBoundaries")
    void planFlipsExactlyAtEachBoundary(int below, PlanCode planBelow, int above, PlanCode planAbove) {
        assertThat(pricingService.calculateMonthlyPrice(below).getPlanCode()).isEqualTo(planBelow);
        assertThat(pricingService.calculateMonthlyPrice(above).getPlanCode()).isEqualTo(planAbove);
    }

    @Test
    void rejectsNegativeVehicleCount() {
        assertThrows(IllegalArgumentException.class, () -> pricingService.calculateMonthlyPrice(-1));
    }

    @Test
    void yearlyBillingCycleIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> pricingService.calculatePrice(10, BillingCycle.YEARLY));
    }

    @Test
    void monthlyBillingCycleDelegatesToCalculateMonthlyPrice() {
        PricingResult viaCycle = pricingService.calculatePrice(31, BillingCycle.MONTHLY);
        assertThat(viaCycle.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("82.40"));
    }

    @Test
    void isMonotonicNonDecreasingAcrossALargeRange() {
        BigDecimal previous = pricingService.calculateMonthlyPrice(0).getMonthlyPrice();
        for (int n = 1; n <= 2000; n++) {
            BigDecimal current = pricingService.calculateMonthlyPrice(n).getMonthlyPrice();
            assertThat(current).as("price(%d) should be >= price(%d)", n, n - 1).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void resultsAreScaledToTwoDecimalPlaces() {
        for (int n : new int[] { 0, 3, 31, 101, 1000 }) {
            BigDecimal price = pricingService.calculateMonthlyPrice(n).getMonthlyPrice();
            assertThat(price.scale()).isEqualTo(2);
            assertThat(price.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(price);
        }
    }

    @Test
    void frottaBreaksDownIntoTierComponents() {
        PricingResult result = pricingService.calculateMonthlyPrice(300);

        assertThat(result.getComponents()).hasSize(2);
        assertThat(result.getComponents().get(0).getQuantity()).isEqualTo(100);
        assertThat(result.getComponents().get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getComponents().get(1).getQuantity()).isEqualTo(100);
        assertThat(result.getComponents().get(1).getSubtotal()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void flatPlansHaveNoTierComponents() {
        assertThat(pricingService.calculateMonthlyPrice(4).getComponents()).isEmpty();
    }

    @Test
    void handlesVeryLargeFleetsWithoutOverflowOrPrecisionLoss() {
        int hugeCount = 10_000_000;
        PricingResult result = pricingService.calculateMonthlyPrice(hugeCount);

        assertThat(result.getPlanCode()).isEqualTo(PlanCode.FROTTA);

        BigDecimal expected = new BigDecimal("254.90")
            .add(new BigDecimal("2.00").multiply(BigDecimal.valueOf(100))) // tier 101-200, full
            .add(new BigDecimal("1.50").multiply(BigDecimal.valueOf(300))) // tier 201-500, full
            .add(new BigDecimal("1.00").multiply(BigDecimal.valueOf(hugeCount - 501 + 1))); // tier 501+

        assertThat(result.getMonthlyPrice()).isEqualByComparingTo(expected);
    }
}
