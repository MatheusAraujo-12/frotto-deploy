package com.localuz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState;
import com.localuz.service.dto.FinancialCoverageEvaluation.Reason;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanChangeType;
import com.localuz.service.dto.PricingResult;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingPlanChangeAlreadyPendingException;
import com.localuz.web.rest.errors.BillingPlanChangeAmbiguousSubscriptionException;
import com.localuz.web.rest.errors.BillingPlanChangeNoOpException;
import com.localuz.web.rest.errors.BillingPlanChangeProviderRejectedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 5G.12: covers SubscriptionPlanChangeService end to end against a REAL SubscriptionPlanChangeSteps
 * wired with mocked repositories/collaborators - same style as SubscriptionCancellationServiceTest,
 * so the lock -> validate -> provider call -> confirm -> finalize ordering is exercised for real,
 * only the provider HTTP boundary and persistence are mocked.
 */
class SubscriptionPlanChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant PERIOD_END = NOW.plusSeconds(10 * 24 * 3600L);

    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private SubscriptionFinancialCoverageService financialCoverage;
    private SubscriptionPlanChangeSteps steps;
    private SubscriptionCancellationService cancellationService;
    private MercadoPagoClient client;
    private PricingService pricingService;
    private CarRepository carRepository;
    private SubscriptionPlanChangeService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        recurringSubscriptionGuard = mock(RecurringSubscriptionGuardService.class);
        financialCoverage = mock(SubscriptionFinancialCoverageService.class);
        steps = new SubscriptionPlanChangeSteps(userRepository, subscriptionRepository,
            recurringSubscriptionGuard, financialCoverage, Clock.fixed(NOW, ZoneOffset.UTC));
        cancellationService = mock(SubscriptionCancellationService.class);
        client = mock(MercadoPagoClient.class);
        pricingService = mock(PricingService.class);
        carRepository = mock(CarRepository.class);
        service = new SubscriptionPlanChangeService(steps, cancellationService, client, pricingService,
            planRepository, subscriptionRepository, carRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User();
        user.setId(1L);
        when(userRepository.findByIdForBillingCheckoutLock(1L)).thenReturn(Optional.of(user));
        when(recurringSubscriptionGuard.isStillChargeable(any())).thenReturn(true);
        when(financialCoverage.evaluate(any(Subscription.class), any(Instant.class)))
            .thenReturn(new FinancialCoverageEvaluation(true, CommercialState.ACTIVE, null, null, null, null, Reason.PAID));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Pricing is computed BEFORE the lock/validation (needed to write it atomically together
        // with the pending intent - see SubscriptionPlanChangeSteps#markPlanChangeIntent), so it is
        // unavoidably called even for requests that are later rejected by validation. This harmless
        // default keeps every "should be rejected" test from needing its own stub; tests that
        // actually assert on the computed price override it with a specific stub.
        when(pricingService.calculatePriceForPlan(any(), Mockito.anyInt()))
            .thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 0, BigDecimal.ZERO, List.of()));
    }

    // Real-world plan ranges/prices per docs/billing-plan-change-5g12.md section 3.
    private static Plan plan(Long id, PlanCode code, int minVehicles, Integer maxVehicles, String price) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(code.name());
        plan.setMinVehicles(minVehicles);
        plan.setMaxVehicles(maxVehicles);
        plan.setMonthlyBasePrice(new BigDecimal(price));
        plan.setActive(true);
        return plan;
    }

    private static Subscription subscription(Long id, Plan plan, SubscriptionStatus status, Instant periodEnd, String externalId, String contractedPrice) {
        Subscription subscription = new Subscription();
        subscription.setId(id);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setExternalProvider("MERCADO_PAGO");
        subscription.setExternalSubscriptionId(externalId);
        subscription.setContractedPrice(new BigDecimal(contractedPrice));
        subscription.setContractedVehicleCount(6);
        return subscription;
    }

    private void stubFindById(Subscription subscription) {
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
    }

    private void stubPlanById(Plan plan) {
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
    }

    private MercadoPagoPreapproval confirming(String id, String status, BigDecimal amount) {
        return new MercadoPagoPreapproval(id, status, "ref", null, null, null, null, null, null, amount, "BRL");
    }

    // --- Upgrade -------------------------------------------------------------------------------

    @Test
    void bronzeToSilverUsesTheSameExternalSubscriptionIdNeverCreatesAPreapprovalAndAppliesImmediately() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        stubPlanById(silver);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(6L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 6)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 6, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-1"), eq(new BigDecimal("44.90")), eq("BRL"), anyString()))
            .thenReturn(confirming("pre-1", "authorized", new BigDecimal("44.90")));
        when(client.getPreapproval("pre-1")).thenReturn(confirming("pre-1", "authorized", new BigDecimal("44.90")));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.SILVER);

        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.UPGRADE);
        assertThat(result.isPending()).isFalse();
        assertThat(result.getContractedPrice()).isEqualByComparingTo("44.90");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("44.90");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(6);
        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("pre-1");
        verify(client, never()).createPreapproval(any(), anyString());
        verify(client).updatePreapprovalAmount(eq("pre-1"), eq(new BigDecimal("44.90")), eq("BRL"), anyString());
        verify(client).getPreapproval("pre-1");
    }

    @Test
    void upgradePriceAlwaysComesFromPricingServiceForTheVehicleCountTheBackendItselfCounted() {
        // PLATINUM is progressive - its price cannot be its own monthlyBasePrice for 35 vehicles.
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan platinum = plan(2L, PlanCode.PLATINUM, 31, 100, "79.90");
        Subscription subscription = subscription(11L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-2", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.PLATINUM)).thenReturn(Optional.of(platinum));
        stubPlanById(platinum);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(35L);
        when(pricingService.calculatePriceForPlan(PlanCode.PLATINUM, 35)).thenReturn(new PricingResult(PlanCode.PLATINUM, "PLATINUM", 35, new BigDecimal("92.40"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-2"), eq(new BigDecimal("92.40")), eq("BRL"), anyString()))
            .thenReturn(confirming("pre-2", "authorized", new BigDecimal("92.40")));
        when(client.getPreapproval("pre-2")).thenReturn(confirming("pre-2", "authorized", new BigDecimal("92.40")));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.PLATINUM);

        assertThat(result.getContractedPrice()).isEqualByComparingTo("92.40");
        verify(pricingService).calculatePriceForPlan(PlanCode.PLATINUM, 35);
        verify(client).updatePreapprovalAmount(eq("pre-2"), eq(new BigDecimal("92.40")), eq("BRL"), anyString());
    }

    @Test
    void aFewVehiclesDoesNotBlockChoosingAHigherPlan() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(12L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-3", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        stubPlanById(silver);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(2L); // below Bronze's own minimum
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 2)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 2, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-3"), any(), eq("BRL"), anyString())).thenReturn(confirming("pre-3", "authorized", new BigDecimal("44.90")));
        when(client.getPreapproval("pre-3")).thenReturn(confirming("pre-3", "authorized", new BigDecimal("44.90")));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.SILVER);

        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.UPGRADE);
    }

    // --- Downgrade -------------------------------------------------------------------------------

    @Test
    void goldToBronzeWithCompatibleFleetSchedulesAPendingDowngradeWithoutChangingTheCurrentPlan() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(13L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        stubPlanById(bronze);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-4"), eq(new BigDecimal("15.90")), eq("BRL"), anyString()))
            .thenReturn(confirming("pre-4", "authorized", new BigDecimal("15.90")));
        when(client.getPreapproval("pre-4")).thenReturn(confirming("pre-4", "authorized", new BigDecimal("15.90")));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.BRONZE);

        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.DOWNGRADE);
        assertThat(result.isPending()).isTrue();
        assertThat(result.getEffectiveAt()).isEqualTo(PERIOD_END);
        verify(client).updatePreapprovalAmount(eq("pre-4"), eq(new BigDecimal("15.90")), eq("BRL"), anyString());
        // The current plan/price never change immediately for a downgrade.
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("79.90");
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("15.90");
        assertThat(subscription.getPendingContractedVehicleCount()).isEqualTo(4);
        assertThat(subscription.getPlanChangeEffectiveAt()).isEqualTo(PERIOD_END);
    }

    @Test
    void downgradeWithIncompatibleFleetIsRejectedBeforeAnyProviderCall() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(8L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BadRequestAlertException.class);

        Mockito.verifyNoInteractions(client);
        Mockito.verifyNoInteractions(subscriptionRepository); // never even locked/looked up the subscription
        Mockito.verify(pricingService, never()).calculatePriceForPlan(any(), Mockito.anyInt());
    }

    // --- No-op / FREE ----------------------------------------------------------------------------

    @Test
    void sameTargetPlanIsAnExplicitConflictWithNoProviderCall() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(14L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-5", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeNoOpException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void freeToPaidThroughTheEndpointIsRejectedInFavorOfCheckout() {
        Plan silver = plan(1L, PlanCode.SILVER, 6, 15, "44.90");
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
        Mockito.verifyNoInteractions(cancellationService);
    }

    @Test
    void paidToFreeReusesCancellationAndNeverCallsTheProviderThroughChangePlan() {
        Plan free = plan(1L, PlanCode.FREE, 0, 2, "0.00");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        when(planRepository.findByCode(PlanCode.FREE)).thenReturn(Optional.of(free));
        Subscription cancelled = subscription(15L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-6", "44.90");
        when(cancellationService.cancel(user)).thenReturn(cancelled);

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.FREE);

        assertThat(result.getTargetPlan()).isEqualTo(PlanCode.FREE);
        assertThat(result.getCurrentPlan()).isEqualTo(PlanCode.SILVER);
        assertThat(result.isPending()).isTrue();
        assertThat(result.getEffectiveAt()).isEqualTo(PERIOD_END);
        verify(cancellationService).cancel(user);
        Mockito.verifyNoInteractions(client);
    }

    // --- Blocked states ----------------------------------------------------------------------------

    @Test
    void cancelAtPeriodEndBlocksAPlanChange() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(16L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-7", "15.90");
        subscription.setCancelAtPeriodEnd(true);
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = SubscriptionStatus.class, names = {"PAST_DUE", "PAUSED", "EXPIRED"})
    void onlyActiveStatusAllowsAPlanChange(SubscriptionStatus status) {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(17L, bronze, status, PERIOD_END, "pre-8", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void adminGrantOrGrandfatheredSourcesNeverReachTheProvider() {
        // No PAYMENT_PROVIDER row at all - ADMIN_GRANT/GRANDFATHERED rows are simply not returned here.
        Plan silver = plan(1L, PlanCode.SILVER, 6, 15, "44.90");
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void financialConflictBlocksAPlanChangeWithoutGrantingOrDenyingEntitlementItself() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(18L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-9", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(financialCoverage.evaluate(eq(subscription), any(Instant.class)))
            .thenReturn(new FinancialCoverageEvaluation(false, CommercialState.UNRESOLVED, null, null, null, null, Reason.FINANCIAL_CONFLICT));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    // --- Concurrency / ambiguous subscriptions --------------------------------------------------

    @Test
    void twoHistoricalChargeableSubscriptionsRejectThePlanChangeWithoutChoosingOneArbitrarily() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription older = subscription(19L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-old", "15.90");
        Subscription newer = subscription(20L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-new", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(older, newer));
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangeAmbiguousSubscriptionException.class);
        Mockito.verifyNoInteractions(client);
        Mockito.verify(subscriptionRepository, never()).findById(any());
    }

    @Test
    void cancellationForOneUserNeverQueriesAnotherUsersSubscriptions() {
        User otherUser = new User();
        otherUser.setId(2L);
        when(userRepository.findByIdForBillingCheckoutLock(2L)).thenReturn(Optional.of(otherUser));
        when(subscriptionRepository.findByUserIdAndSource(2L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
        Plan silver = plan(1L, PlanCode.SILVER, 6, 15, "44.90");
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of());
        when(carRepository.countByUserIdAndActiveTrue(any())).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.changePlan(otherUser, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        verify(subscriptionRepository).findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER);
        verify(subscriptionRepository).findByUserIdAndSource(2L, SubscriptionSource.PAYMENT_PROVIDER);
    }

    // --- Provider failure handling -----------------------------------------------------------------

    @Test
    void definite4xxNeverFinalizesAnUpgradeLocally() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(21L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-10", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 4)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 4, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-10"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("15.90");
        verify(client, never()).getPreapproval(anyString());
    }

    @Test
    void definite4xxOnADowngradeRollsBackTheAlreadyCommittedPendingIntent() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(22L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-11", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        stubPlanById(bronze);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-11"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);

        // The pending intent WAS committed before the PUT (see markPendingDowngrade), but the
        // definite rejection must roll it back - never leave a pending change the provider refused.
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPendingContractedPrice()).isNull();
        assertThat(subscription.getPlanChangeEffectiveAt()).isNull();
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
    }

    @Test
    void ambiguousFailureConfirmedByNewPriceFinalizesTheUpgradeSafely() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(23L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-12", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        stubPlanById(silver);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 4)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 4, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-12"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("timeout", true));
        when(client.getPreapproval("pre-12")).thenReturn(confirming("pre-12", "authorized", new BigDecimal("44.90")));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.SILVER);

        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.UPGRADE);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void ambiguousFailureConfirmedByOldPriceDoesNotFinalize() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(24L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-13", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 4)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 4, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-13"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("timeout", true));
        when(client.getPreapproval("pre-13")).thenReturn(confirming("pre-13", "authorized", new BigDecimal("15.90")));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void confirmingGetShowingAnUnrelatedAmountFailsClosed() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(25L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-14", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 4)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 4, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-14"), any(), eq("BRL"), anyString()))
            .thenReturn(confirming("pre-14", "authorized", new BigDecimal("44.90")));
        // Divergent value/currency neither matches the new nor the old price - fail closed.
        when(client.getPreapproval("pre-14")).thenReturn(confirming("pre-14", "authorized", new BigDecimal("999.99")));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    // --- Replay / idempotency -----------------------------------------------------------------------

    @Test
    void repeatingTheSameUpgradeAfterItAlreadyAppliedIsANoOpNotADuplicatePut() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(26L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-15", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        stubPlanById(silver);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.SILVER, 4)).thenReturn(new PricingResult(PlanCode.SILVER, "SILVER", 4, new BigDecimal("44.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-15"), any(), eq("BRL"), anyString())).thenReturn(confirming("pre-15", "authorized", new BigDecimal("44.90")));
        when(client.getPreapproval("pre-15")).thenReturn(confirming("pre-15", "authorized", new BigDecimal("44.90")));

        service.changePlan(user, PlanCode.SILVER);
        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangeNoOpException.class);

        verify(client, times(1)).updatePreapprovalAmount(eq("pre-15"), any(), eq("BRL"), anyString());
    }

    @Test
    void repeatingADowngradeRequestWhileOnePendingRefusesRatherThanReplacingItSilently() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(27L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-16", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        stubPlanById(bronze);
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-16"), any(), eq("BRL"), anyString())).thenReturn(confirming("pre-16", "authorized", new BigDecimal("15.90")));
        when(client.getPreapproval("pre-16")).thenReturn(confirming("pre-16", "authorized", new BigDecimal("15.90")));

        service.changePlan(user, PlanCode.BRONZE);
        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeAlreadyPendingException.class);

        verify(client, times(1)).updatePreapprovalAmount(eq("pre-16"), any(), eq("BRL"), anyString());
    }

    // --- Effectuation of a pending downgrade ------------------------------------------------------

    @Test
    void beforeEffectiveDateThePendingDowngradeIsNeverApplied() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(28L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-17", "79.90");
        subscription.setPendingPlan(bronze);
        subscription.setPendingContractedPrice(new BigDecimal("15.90"));
        subscription.setPendingContractedVehicleCount(4);
        subscription.setPlanChangeEffectiveAt(PERIOD_END);
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);

        service.effectuateDueChangesForUser(1L); // NOW is well before PERIOD_END

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan()).isNotNull();
        Mockito.verifyNoInteractions(financialCoverage);
    }

    @Test
    void effectiveDateReachedButRenewalNotYetConfirmedLeavesTheDowngradePending() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(29L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-18", "79.90");
        subscription.setPendingPlan(bronze);
        subscription.setPendingContractedPrice(new BigDecimal("15.90"));
        subscription.setPendingContractedVehicleCount(4);
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1)); // already reached
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(financialCoverage.evaluate(eq(subscription), eq(NOW)))
            .thenReturn(new FinancialCoverageEvaluation(false, CommercialState.AWAITING_PAYMENT, null, null, null, null, Reason.NO_APPROVED_PAYMENT));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan()).isNotNull();
    }

    @Test
    void validRenewalOnOrAfterTheEffectiveDatePromotesThePendingPlanAndClearsPendingFields() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(30L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-19", "79.90");
        subscription.setPendingPlan(bronze);
        subscription.setPendingContractedPrice(new BigDecimal("15.90"));
        subscription.setPendingContractedVehicleCount(4);
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(financialCoverage.evaluate(eq(subscription), eq(NOW)))
            .thenReturn(new FinancialCoverageEvaluation(true, CommercialState.ACTIVE, 100L, NOW, NOW.plusSeconds(3600), null, Reason.PAID));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("15.90");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(4);
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPendingContractedPrice()).isNull();
        assertThat(subscription.getPendingContractedVehicleCount()).isNull();
        assertThat(subscription.getPlanChangeEffectiveAt()).isNull();
        assertThat(subscription.getPlanChangeRequestedAt()).isNull();
    }

    @Test
    void chargebackOrRefundEvidenceAtEffectuationTimeNeverPromotesThePendingPlan() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(31L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-20", "79.90");
        subscription.setPendingPlan(bronze);
        subscription.setPendingContractedPrice(new BigDecimal("15.90"));
        subscription.setPendingContractedVehicleCount(4);
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        stubFindById(subscription);
        when(financialCoverage.evaluate(eq(subscription), eq(NOW)))
            .thenReturn(new FinancialCoverageEvaluation(false, CommercialState.UNRESOLVED, null, null, null, null, Reason.REVERSED_OR_CANCELED));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan()).isNotNull();
    }
}
