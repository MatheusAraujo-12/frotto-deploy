package com.localuz.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
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
        assertThat(dto.getSubscriptionSource()).isNull();
        assertThat(dto.getGrantExpiresAt()).isNull();
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
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setContractedPrice(new BigDecimal("129.90"));
        subscription.setCurrentPeriodStart(Instant.parse("2026-08-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2026-09-01T00:00:00Z"));
        subscription.setCancelAtPeriodEnd(true);

        EntitlementSnapshot snapshot = new EntitlementSnapshot(subscription, platinum, platinum, 50L, 100, true, false);

        BillingMeDTO dto = BillingMeDTO.from(snapshot);

        assertThat(dto.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dto.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(dto.getSubscriptionSource()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
        assertThat(dto.getGrantExpiresAt()).isNull();
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
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setContractedPrice(new BigDecimal("15.90"));

        EntitlementSnapshot snapshot = new EntitlementSnapshot(subscription, bronze, silver, 6L, 5, false, true);

        BillingMeDTO dto = BillingMeDTO.from(snapshot);

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(dto.getRequiredPlanCode()).isEqualTo(PlanCode.SILVER);
        assertThat(dto.getPlanCode()).isNotEqualTo(dto.getRequiredPlanCode());
    }

    @Test
    void permanentAdminGrantExposesSourceWithNoExpiryAndPreservesZeroContractedPrice() {
        Plan gold = plan(PlanCode.GOLD, "Ouro", 30, "79.90");
        Subscription grant = subscription(gold, SubscriptionSource.ADMIN_GRANT);

        BillingMeDTO dto = BillingMeDTO.from(new EntitlementSnapshot(grant, gold, gold, 10L, 30, true, false));

        assertThat(dto.getSubscriptionSource()).isEqualTo(SubscriptionSource.ADMIN_GRANT);
        assertThat(dto.getGrantExpiresAt()).isNull();
        assertThat(dto.getCurrentMonthlyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void temporaryAdminGrantExposesItsRealExpiry() {
        Plan gold = plan(PlanCode.GOLD, "Ouro", 30, "79.90");
        Subscription grant = subscription(gold, SubscriptionSource.ADMIN_GRANT);
        Instant expiresAt = Instant.parse("2026-09-30T23:59:59Z");
        grant.setGrantExpiresAt(expiresAt);

        BillingMeDTO dto = BillingMeDTO.from(new EntitlementSnapshot(grant, gold, gold, 10L, 30, true, false));

        assertThat(dto.getSubscriptionSource()).isEqualTo(SubscriptionSource.ADMIN_GRANT);
        assertThat(dto.getGrantExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void grandfatheredExposesSourceButNeverGrantExpiry() {
        Plan silver = plan(PlanCode.SILVER, "Prata", 15, "44.90");
        Subscription grandfathered = subscription(silver, SubscriptionSource.GRANDFATHERED);
        grandfathered.setGrantExpiresAt(Instant.parse("2026-09-30T23:59:59Z"));

        BillingMeDTO dto = BillingMeDTO.from(new EntitlementSnapshot(grandfathered, silver, silver, 8L, 15, true, false));

        assertThat(dto.getSubscriptionSource()).isEqualTo(SubscriptionSource.GRANDFATHERED);
        assertThat(dto.getGrantExpiresAt()).isNull();
        assertThat(dto.getCurrentMonthlyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void paymentProviderExposesSourceButNeverGrantExpiry() {
        Plan bronze = plan(PlanCode.BRONZE, "Bronze", 5, "15.90");
        Subscription payment = subscription(bronze, SubscriptionSource.PAYMENT_PROVIDER);
        payment.setContractedPrice(new BigDecimal("15.90"));
        payment.setGrantExpiresAt(Instant.parse("2026-09-30T23:59:59Z"));

        BillingMeDTO dto = BillingMeDTO.from(new EntitlementSnapshot(payment, bronze, bronze, 4L, 5, true, false));

        assertThat(dto.getSubscriptionSource()).isEqualTo(SubscriptionSource.PAYMENT_PROVIDER);
        assertThat(dto.getGrantExpiresAt()).isNull();
    }

    @Test
    void neverDeclaresAFieldThatCouldLeakGatewayOrInternalSubscriptionData() {
        Field[] fields = BillingMeDTO.class.getDeclaredFields();
        String[] forbiddenNamesFragments = { "external", "gateway", "id", "userId", "subscriptionId" };
        String[] forbiddenNames = { "externalSubscriptionId", "externalProvider", "grantReason", "grantedBy" };

        for (Field field : fields) {
            String lower = field.getName().toLowerCase();
            assertThat(Arrays.asList(forbiddenNames)).doesNotContain(field.getName());
            assertThat(Arrays.stream(forbiddenNamesFragments).anyMatch(lower::contains))
                .as("BillingMeDTO must not declare a field named '%s'", field.getName())
                .isFalse();
        }
    }

    private static Subscription subscription(Plan plan, SubscriptionSource source) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setSource(source);
        subscription.setContractedPrice(BigDecimal.ZERO);
        subscription.setCancelAtPeriodEnd(false);
        return subscription;
    }
}
