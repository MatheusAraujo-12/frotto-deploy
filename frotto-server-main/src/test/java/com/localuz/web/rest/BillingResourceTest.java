package com.localuz.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.PlanPricingTier;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.service.EntitlementService;
import com.localuz.service.PricingService;
import com.localuz.service.UserService;
import com.localuz.service.dto.BillingMeDTO;
import com.localuz.service.dto.EntitlementSnapshot;
import com.localuz.service.dto.PricePreviewDTO;
import com.localuz.service.dto.PricingResult;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Plain unit tests (no Spring context / no MockMvc, matching this project's existing service
 * test style) for BillingResource. Security is exercised at the level that matters here: the
 * resource must never accept a userId and must always resolve the caller from UserService,
 * which itself reads only the authenticated SecurityContext (see SecurityUtils).
 */
class BillingResourceTest {

    private UserService userService;
    private EntitlementService entitlementService;
    private PricingService pricingService;
    private PlanRepository planRepository;
    private PlanPricingTierRepository planPricingTierRepository;
    private BillingResource billingResource;
    private User currentUser;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        entitlementService = Mockito.mock(EntitlementService.class);
        pricingService = Mockito.mock(PricingService.class);
        planRepository = Mockito.mock(PlanRepository.class);
        planPricingTierRepository = Mockito.mock(PlanPricingTierRepository.class);
        billingResource = new BillingResource(userService, entitlementService, pricingService, planRepository, planPricingTierRepository);

        currentUser = new User();
        currentUser.setId(9L);
    }

    private static Plan plan(PlanCode code, Integer maxVehicles) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code.name());
        plan.setMaxVehicles(maxVehicles);
        plan.setMonthlyBasePrice(BigDecimal.ZERO);
        return plan;
    }

    @Test
    void getMyBillingResolvesTheUserFromTheSecurityContextOnly() {
        when(userService.getUserWithAuthorities()).thenReturn(Optional.of(currentUser));
        Plan free = plan(PlanCode.FREE, 2);
        EntitlementSnapshot snapshot = new EntitlementSnapshot(null, free, free, 0L, 2, true, false);
        when(entitlementService.getSnapshot(currentUser)).thenReturn(snapshot);

        BillingMeDTO dto = billingResource.getMyBilling();

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.FREE);
        Mockito.verify(entitlementService).getSnapshot(currentUser);
    }

    @Test
    void getMyBillingRejectsWhenThereIsNoAuthenticatedUser() {
        when(userService.getUserWithAuthorities()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingResource.getMyBilling()).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void previewPriceRejectsNegativeVehicleCount() {
        assertThatThrownBy(() -> billingResource.previewPrice(-1, BillingCycle.MONTHLY)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(pricingService);
    }

    @Test
    void previewPriceRejectsYearlyBillingCycle() {
        assertThatThrownBy(() -> billingResource.previewPrice(50, BillingCycle.YEARLY)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(pricingService);
    }

    @Test
    void previewPriceDelegatesToPricingServiceWithoutReimplementingTheFormula() {
        PricingResult result = new PricingResult(PlanCode.PLATINUM, "Platinum", 50, new BigDecimal("129.90"), List.of());
        when(pricingService.calculateMonthlyPrice(50)).thenReturn(result);

        PricePreviewDTO dto = billingResource.previewPrice(50, BillingCycle.MONTHLY);

        assertThat(dto.getPlanCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(dto.getMonthlyPrice()).isEqualByComparingTo(new BigDecimal("129.90"));
        assertThat(dto.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test
    void previewPriceDefaultsToMonthlyWhenBillingCycleIsOmitted() {
        PricingResult result = new PricingResult(PlanCode.FREE, "Gratuito", 1, BigDecimal.ZERO, List.of());
        when(pricingService.calculateMonthlyPrice(1)).thenReturn(result);

        PricePreviewDTO dto = billingResource.previewPrice(1, BillingCycle.MONTHLY);

        assertThat(dto.getBillingCycle()).isEqualTo(BillingCycle.MONTHLY);
    }

    @Test
    void getPlansReturnsOnlyActivePlansMappedToDTOsWithTheirTiers() {
        Plan platinum = plan(PlanCode.PLATINUM, 100);
        platinum.setId(5L);
        when(planRepository.findByActiveTrueOrderByMinVehiclesAsc()).thenReturn(List.of(platinum));

        PlanPricingTier tier = new PlanPricingTier();
        tier.setFromVehicleCount(31);
        tier.setToVehicleCount(100);
        tier.setPricePerVehicle(new BigDecimal("2.50"));
        when(planPricingTierRepository.findByPlanIdOrderByTierOrderAsc(5L)).thenReturn(List.of(tier));

        List<com.localuz.service.dto.PlanDTO> plans = billingResource.getPlans();

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).getCode()).isEqualTo(PlanCode.PLATINUM);
        assertThat(plans.get(0).getTiers()).hasSize(1);
    }
}
