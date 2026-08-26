package com.localuz.web.rest;

import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.service.EntitlementService;
import com.localuz.service.PricingService;
import com.localuz.service.BillingCheckoutService;
import com.localuz.service.UserService;
import com.localuz.service.dto.BillingMeDTO;
import com.localuz.service.dto.PlanDTO;
import com.localuz.service.dto.PricePreviewDTO;
import com.localuz.service.dto.PricingResult;
import com.localuz.service.dto.BillingCheckoutDTO;
import com.localuz.service.dto.BillingCheckoutRequest;
import com.localuz.web.rest.errors.BadRequestAlertException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import javax.validation.Valid;

/**
 * Read-only Billing API for the currently authenticated user. All three endpoints require
 * authentication (covered by SecurityConfiguration's blanket "/api/**" -> authenticated()
 * matcher; no security config change was needed). /plans was kept authenticated rather than
 * public because no public plans page exists yet - see the Billing Etapa 2 report for the
 * reasoning and how to flip it later if a pre-login pricing page is built.
 *
 * The authenticated user is always resolved server-side from the security context
 * (UserService#getUserWithAuthorities); no endpoint here accepts a userId.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingResource {

    private static final String ENTITY_NAME = "billing";

    private final UserService userService;
    private final EntitlementService entitlementService;
    private final PricingService pricingService;
    private final PlanRepository planRepository;
    private final PlanPricingTierRepository planPricingTierRepository;
    private final BillingCheckoutService billingCheckoutService;

    public BillingResource(
        UserService userService,
        EntitlementService entitlementService,
        PricingService pricingService,
        PlanRepository planRepository,
        PlanPricingTierRepository planPricingTierRepository,
        BillingCheckoutService billingCheckoutService
    ) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.pricingService = pricingService;
        this.planRepository = planRepository;
        this.planPricingTierRepository = planPricingTierRepository;
        this.billingCheckoutService = billingCheckoutService;
    }

    @PostMapping("/checkout")
    public BillingCheckoutDTO createCheckout(@Valid @RequestBody BillingCheckoutRequest request) {
        return BillingCheckoutDTO.from(billingCheckoutService.createCheckout(getCurrentUser(), request.getPlanCode()));
    }

    @GetMapping("/me")
    public BillingMeDTO getMyBilling() {
        return BillingMeDTO.from(entitlementService.getSnapshot(getCurrentUser()));
    }

    @GetMapping("/price-preview")
    public PricePreviewDTO previewPrice(
        @RequestParam int vehicleCount,
        @RequestParam(required = false, defaultValue = "MONTHLY") BillingCycle billingCycle
    ) {
        if (vehicleCount < 0) {
            throw new BadRequestAlertException("vehicleCount must be >= 0", ENTITY_NAME, "vehiclecountinvalid");
        }
        if (billingCycle == BillingCycle.YEARLY) {
            throw new BadRequestAlertException("Yearly billing is not available yet", ENTITY_NAME, "yearlynotavailable");
        }
        PricingResult result = pricingService.calculateMonthlyPrice(vehicleCount);
        return PricePreviewDTO.from(result, billingCycle);
    }

    @GetMapping("/plans")
    public List<PlanDTO> getPlans() {
        return planRepository
            .findByActiveTrueOrderByMinVehiclesAsc()
            .stream()
            .map(plan -> PlanDTO.from(plan, planPricingTierRepository.findByPlanIdOrderByTierOrderAsc(plan.getId())))
            .collect(Collectors.toList());
    }

    private User getCurrentUser() {
        return userService
            .getUserWithAuthorities()
            .orElseThrow(() -> new BadRequestAlertException("Usuario autenticado nao encontrado", ENTITY_NAME, "usernotfound"));
    }
}
