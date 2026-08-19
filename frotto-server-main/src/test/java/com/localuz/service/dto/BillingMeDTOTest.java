package com.localuz.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionStatus;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Verifies BillingMeDTO#from maps EntitlementSnapshot correctly and, most importantly, that the
 * class never gains a field that would leak gateway/admin-facing subscription data.
 */
class BillingMeDTOTest {

    private static Plan plan(PlanCode code, String name, Integer maxVehicles, String basePrice) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(name);
        plan.setMaxVehicles(maxVehicles);
        plan.setMonthlyBasePrice(new BigDecimal(basePrice));
        return plan;
    }

    @Test
    void freeUserHasNullSubscriptionFieldsAndZeroPrice() {
        Plan free = plan(PlanCode.FREE, "Gratuito", 2, "0.00");
        EntitlementSnapshot snapshot = new EntitlementSnapshot(null, free, free, 2L, 2, false, true);

        BillingMeDTO dto = BillingMeDTO.from(snapshot);

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.FREE);
        assertThat(dto.getSubscriptionStatus()).isNull();
        assertThat(dto.getBillingCycle()).isNull();
        assertThat(dto.getCurrentMonthlyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getCurrentPeriodStart()).isNull();
        assertThat(dto.getCurrentPeriodEnd()).isNull();
        assertThat(dto.isCancelAtPeriodEnd()).isFalse();
        assertThat(dto.isCanAddVehicle()).isFalse();
        assertThat(dto.isNeedsUpgrade()).isTrue();
        assertThat(dto.getActiveVehicleCount()).isEqualTo(2L);
    }

    @Test
    void payingUserUsesTheContractedPriceSnapshotNotARecalculation() {
        Plan platinum = plan(PlanCode.PLATINUM, "Platinum", 100, "79.90");
        Subscription subscription = new Subscription();
        subscription.setPlan(platinum);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setContractedPrice(new BigDecimal("129.90"));
        subscription.setCurrentPeriodStart(Instant.parse("2026-08-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2026-09-01T00:00:00Z"));
        subscription.setCancelAtPeriodEnd(true);

        EntitlementSnapshot snapshot = new EntitlementSnapshot(subscription, platinum, platinum, 50L, 100, true, false);

        BillingMeDTO dto = BillingMeDTO.from(snapshot);

        assertThat(dto.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dto.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(dto.getCurrentMonthlyPrice()).isEqualByComparingTo(new BigDecimal("129.90"));
        assertThat(dto.getCurrentPeriodStart()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(dto.getCurrentPeriodEnd()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(dto.isCancelAtPeriodEnd()).isTrue();
    }

    @Test
    void requiredPlanIsExposedSeparatelyFromContractedPlan() {
        Plan bronze = plan(PlanCode.BRONZE, "Bronze", 5, "15.90");
        Plan silver = plan(PlanCode.SILVER, "Prata", 15, "44.90");
        Subscription subscription = new Subscription();
        subscription.setPlan(bronze);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setContractedPrice(new BigDecimal("15.90"));

        EntitlementSnapshot snapshot = new EntitlementSnapshot(subscription, bronze, silver, 6L, 5, false, true);

        BillingMeDTO dto = BillingMeDTO.from(snapshot);

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(dto.getRequiredPlanCode()).isEqualTo(PlanCode.SILVER);
        assertThat(dto.getPlanCode()).isNotEqualTo(dto.getRequiredPlanCode());
    }

    @Test
    void neverDeclaresAFieldThatCouldLeakGatewayOrInternalSubscriptionData() {
        Field[] fields = BillingMeDTO.class.getDeclaredFields();
        String[] forbiddenNamesFragments = { "external", "gateway", "id", "userId", "subscriptionId" };

        for (Field field : fields) {
            String lower = field.getName().toLowerCase();
            assertThat(Arrays.stream(forbiddenNamesFragments).anyMatch(lower::contains))
                .as("BillingMeDTO must not declare a field named '%s'", field.getName())
                .isFalse();
        }
    }
}
