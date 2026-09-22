package com.localuz.web.rest;

import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.repository.PlanPricingTierRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.service.EntitlementService;
import com.localuz.service.PricingService;
import com.localuz.service.BillingCheckoutService;
import com.localuz.service.BillingPaymentStateService;
import com.localuz.service.SubscriptionCancellationService;
import com.localuz.service.SubscriptionPlanChangeService;
import com.localuz.service.UserService;
import com.localuz.service.dto.BillingMeDTO;
import com.localuz.service.dto.PlanChangeRequest;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanDTO;
import com.localuz.service.dto.PricePreviewDTO;
import com.localuz.service.dto.PricingResult;
import com.localuz.service.dto.BillingCheckoutDTO;
import com.localuz.service.dto.BillingCheckoutRequest;
import com.localuz.service.dto.BillingPaymentStateDTO;
import com.localuz.service.dto.SubscriptionCancellationResultDTO;
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
    private final BillingPaymentStateService billingPaymentStateService;
    private final SubscriptionCancellationService subscriptionCancellationService;
    private final SubscriptionPlanChangeService subscriptionPlanChangeService;

    public BillingResource(
        UserService userService,
        EntitlementService entitlementService,
        PricingService pricingService,
        PlanRepository planRepository,
        PlanPricingTierRepository planPricingTierRepository,
        BillingCheckoutService billingCheckoutService,
        BillingPaymentStateService billingPaymentStateService,
        SubscriptionCancellationService subscriptionCancellationService,
        SubscriptionPlanChangeService subscriptionPlanChangeService
    ) {
        this.userService = userService;
        this.entitlementService = entitlementService;
        this.pricingService = pricingService;
        this.planRepository = planRepository;
        this.planPricingTierRepository = planPricingTierRepository;
        this.billingCheckoutService = billingCheckoutService;
        this.billingPaymentStateService = billingPaymentStateService;
        this.subscriptionCancellationService = subscriptionCancellationService;
        this.subscriptionPlanChangeService = subscriptionPlanChangeService;
    }

    @PostMapping("/checkout")
    public BillingCheckoutDTO createCheckout(@Valid @RequestBody BillingCheckoutRequest request) {
        return BillingCheckoutDTO.from(billingCheckoutService.createCheckout(getCurrentUser(), request.getPlanCode()));
    }

    /**
     * Cancels the caller's own PAYMENT_PROVIDER subscription - the user is always resolved
     * server-side (see class javadoc), never accepted from the request. Schedules the
     * cancellation for the end of the already-paid period rather than revoking access
     * immediately; see SubscriptionCancellationService for the full flow and how the PUT/webhook
     * race is avoided.
     *
     * Deliberately does NOT reuse BillingMeDTO here: cancelAtPeriodEnd alone cannot distinguish a
     * cancellation the provider has actually confirmed from one only recorded locally as pending
     * (see SubscriptionCancellationSteps#markIntents) - SubscriptionCancellationResultDTO makes
     * that distinction explicit via its CONFIRMED/PENDING_CONFIRMATION state, same safe-field
     * philosophy as BillingMeDTO (no provider identifiers, no idempotency keys).
     *
     * 5G.11: a pre-5G.9 user can have more than one PAYMENT_PROVIDER contract; cancel() already
     * attempts every one of them and returns the primary (most-recently-started) result, but a
     * historical duplicate can still fail independently - hasResidualActiveContract is checked
     * fresh after cancel() so the response never claims full success while a hidden recurrence is
     * still chargeable.
     */
    @PostMapping("/cancel")
    public SubscriptionCancellationResultDTO cancelSubscription() {
        User user = getCurrentUser();
        Subscription result = subscriptionCancellationService.cancel(user);
        boolean hasResidualActiveContract = subscriptionCancellationService.hasResidualActiveContract(user);
        return SubscriptionCancellationResultDTO.from(result, hasResidualActiveContract);
    }

    /**
     * 5G.12: alters the SAME Mercado Pago recurrence (auto_recurring.transaction_amount) instead
     * of creating a second preapproval - see SubscriptionPlanChangeService. Never accepts price or
     * userId from the request; the backend derives vehicle count, current plan, target plan, price
     * and change direction itself.
     */
    @PostMapping("/change-plan")
    public PlanChangeResultDTO changePlan(@Valid @RequestBody PlanChangeRequest request) {
        return subscriptionPlanChangeService.changePlan(getCurrentUser(), request.getTargetPlanCode());
    }

    @GetMapping("/me")
    public BillingMeDTO getMyBilling() {
        User user = getCurrentUser();
        subscriptionPlanChangeService.effectuateDueChangesForUser(user.getId());
        return BillingMeDTO.from(entitlementService.getSnapshot(user));
    }

    @GetMapping("/payment-state")
    public BillingPaymentStateDTO getMyPaymentState() {
        User user = getCurrentUser();
        subscriptionPlanChangeService.effectuateDueChangesForUser(user.getId());
        return billingPaymentStateService.getState(user);
    }

    @GetMapping("/price-preview")
    public PricePreviewDTO previewPrice(
        @RequestParam int vehicleCount,
        @RequestParam(required = false, defaultValue = "MONTHLY") BillingCycle billingCycle,
        @RequestParam(required = false) com.localuz.domain.enumeration.PlanCode planCode
    ) {
        if (vehicleCount < 0) {
            throw new BadRequestAlertException("vehicleCount must be >= 0", ENTITY_NAME, "vehiclecountinvalid");
        }
        if (billingCycle == BillingCycle.YEARLY) {
            throw new BadRequestAlertException("Yearly billing is not available yet", ENTITY_NAME, "yearlynotavailable");
        }
        // 5G.12: an explicit planCode prices exactly the plan the user selected (which may be
        // ABOVE what resolvePlanForVehicleCount would recommend, e.g. a user with 2 vehicles
        // deliberately choosing Bronze) - never guessed or approximated on the frontend.
        PricingResult result = planCode != null
            ? pricingService.calculatePriceForPlan(planCode, vehicleCount)
            : pricingService.calculateMonthlyPrice(vehicleCount);
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
