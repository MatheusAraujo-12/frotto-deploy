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

import com.localuz.config.MercadoPagoProperties;
import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.SubscriptionPlanUpgrade;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.CarRepository;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionPlanUpgradeRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.repository.UserRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState;
import com.localuz.service.dto.FinancialCoverageEvaluation.Reason;
import com.localuz.service.dto.MercadoPagoPayment;
import com.localuz.service.dto.MercadoPagoPaymentPreference;
import com.localuz.service.dto.MercadoPagoPaymentPreferenceRequest;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.PlanChangePreviewDTO;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanChangeStatus;
import com.localuz.service.dto.PlanChangeType;
import com.localuz.service.dto.PlanUpgradeStatusDTO;
import com.localuz.service.dto.PricingResult;
import com.localuz.web.rest.errors.BadRequestAlertException;
import com.localuz.web.rest.errors.BillingDowngradeUndoRejectedException;
import com.localuz.web.rest.errors.BillingPlanChangeAlreadyPendingException;
import com.localuz.web.rest.errors.BillingPlanChangeAmbiguousSubscriptionException;
import com.localuz.web.rest.errors.BillingPlanChangeNoOpException;
import com.localuz.web.rest.errors.BillingPlanChangePeriodUnconfirmedException;
import com.localuz.web.rest.errors.BillingPlanChangeProviderRejectedException;
import com.localuz.web.rest.errors.BillingPlanUpgradeCheckoutUnavailableException;
import com.localuz.web.rest.errors.BillingPlanUpgradeInProgressException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 5G.12 / 5G.12.1: SubscriptionPlanChangeService end to end against REAL SubscriptionPlanChangeSteps,
 * SubscriptionPlanUpgradeSteps and SubscriptionPlanUpgradeService wired with mocked repositories
 * (plus an in-memory store for upgrade attempts) - only the provider HTTP boundary and persistence
 * are mocked, so lock -> validate -> provider call -> confirm -> finalize ordering runs for real.
 */
class SubscriptionPlanChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant PERIOD_END = NOW.plusSeconds(10 * 24 * 3600L);
    /** Paid 30-day cycle with exactly 10 days left: remaining ratio = 1/3. */
    private static final Instant CYCLE_START = NOW.minusSeconds(20 * 24 * 3600L);
    private static final String BACK_URL = "https://app.frotto.test/menu/meu-plano";

    private MutableClock clock;
    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionPlanUpgradeRepository upgradeRepository;
    private PlanRepository planRepository;
    private RecurringSubscriptionGuardService recurringSubscriptionGuard;
    private SubscriptionFinancialCoverageService financialCoverage;
    private SubscriptionPlanChangeSteps steps;
    private SubscriptionCancellationService cancellationService;
    private MercadoPagoClient client;
    private PricingService pricingService;
    private CarRepository carRepository;
    private SubscriptionPlanUpgradeService upgradeService;
    private SubscriptionPlanChangeService service;
    private User user;
    private final Map<Long, SubscriptionPlanUpgrade> upgradeStore = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        userRepository = mock(UserRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        upgradeRepository = mock(SubscriptionPlanUpgradeRepository.class);
        planRepository = mock(PlanRepository.class);
        recurringSubscriptionGuard = mock(RecurringSubscriptionGuardService.class);
        financialCoverage = mock(SubscriptionFinancialCoverageService.class);
        steps = new SubscriptionPlanChangeSteps(userRepository, subscriptionRepository, recurringSubscriptionGuard, financialCoverage,
            upgradeRepository, clock);
        SubscriptionPlanUpgradeSteps upgradeSteps = new SubscriptionPlanUpgradeSteps(userRepository, steps, upgradeRepository,
            subscriptionRepository, clock);
        cancellationService = mock(SubscriptionCancellationService.class);
        client = mock(MercadoPagoClient.class);
        pricingService = mock(PricingService.class);
        carRepository = mock(CarRepository.class);
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setBackUrl(BACK_URL);
        upgradeService = new SubscriptionPlanUpgradeService(upgradeSteps, upgradeRepository, client, financialCoverage, properties, clock);
        service = new SubscriptionPlanChangeService(steps, upgradeService, cancellationService, client, pricingService,
            planRepository, subscriptionRepository, carRepository, clock);

        user = new User();
        user.setId(1L);
        when(userRepository.findByIdForBillingCheckoutLock(1L)).thenReturn(Optional.of(user));
        when(recurringSubscriptionGuard.isStillChargeable(any())).thenReturn(true);
        when(financialCoverage.evaluate(any(Subscription.class), any(Instant.class)))
            .thenReturn(new FinancialCoverageEvaluation(true, CommercialState.ACTIVE, 7L, CYCLE_START, PERIOD_END, null, Reason.PAID));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Pricing is computed BEFORE the lock/validation, so it is unavoidably called even for
        // requests later rejected by validation. Tests asserting on a price stub it explicitly.
        when(pricingService.calculatePriceForPlan(any(), Mockito.anyInt()))
            .thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 0, BigDecimal.ZERO, List.of()));
        stubUpgradeStore();
    }

    // --- Fixtures --------------------------------------------------------------------------------

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

    private Subscription subscription(Long id, Plan plan, SubscriptionStatus status, Instant periodEnd, String externalId, String contractedPrice) {
        Subscription subscription = new Subscription();
        subscription.setId(id);
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setStartDate(CYCLE_START.minusSeconds(60L * 24 * 3600));
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setExternalProvider("MERCADO_PAGO");
        subscription.setExternalSubscriptionId(externalId);
        subscription.setContractedPrice(new BigDecimal(contractedPrice));
        subscription.setContractedVehicleCount(6);
        return subscription;
    }

    private void scheduleDowngrade(Subscription subscription, Plan pendingPlan, String pendingPrice) {
        subscription.setPendingPlan(pendingPlan);
        subscription.setPendingContractedPrice(new BigDecimal(pendingPrice));
        subscription.setPendingContractedVehicleCount(4);
        subscription.setPlanChangeEffectiveAt(PERIOD_END);
        subscription.setPlanChangeRequestedAt(NOW.minusSeconds(3600));
    }

    /** Stubs everything the upgrade path needs for one subscription/target. */
    private void givenLiveContract(Subscription subscription, Plan target, int vehicles, String targetPrice, String recurringAmount) {
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode(target.getCode())).thenReturn(Optional.of(target));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn((long) vehicles);
        when(pricingService.calculatePriceForPlan(target.getCode(), vehicles))
            .thenReturn(new PricingResult(target.getCode(), target.getCode().name(), vehicles, new BigDecimal(targetPrice), List.of()));
        when(client.getPreapproval(subscription.getExternalSubscriptionId()))
            .thenReturn(preapproval(subscription.getExternalSubscriptionId(), recurringAmount));
        when(client.createPaymentPreference(any())).thenAnswer(invocation -> {
            MercadoPagoPaymentPreferenceRequest request = invocation.getArgument(0);
            return new MercadoPagoPaymentPreference("pref-" + request.getExternalReference().hashCode(),
                "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=x", request.getExternalReference());
        });
    }

    private static MercadoPagoPreapproval preapproval(String id, String amount) {
        return new MercadoPagoPreapproval(id, "authorized", "ref", null, null, null, null, 1, "months", new BigDecimal(amount), "BRL");
    }

    private SubscriptionPlanUpgrade onlyUpgrade() {
        assertThat(upgradeStore).hasSize(1);
        return upgradeStore.values().iterator().next();
    }

    private void providerHasPayment(SubscriptionPlanUpgrade upgrade, String paymentId, String status, String amount) {
        when(client.searchPaymentIdsByExternalReference(upgrade.getExternalReference())).thenReturn(List.of(paymentId));
        when(client.getPayment(paymentId)).thenReturn(new MercadoPagoPayment(paymentId, status, null, new BigDecimal(amount), "BRL",
            NOW, "approved".equals(status) ? NOW.plusSeconds(60) : null, NOW.plusSeconds(60), upgrade.getExternalReference(), null));
    }

    private void stubUpgradeStore() {
        AtomicLong sequence = new AtomicLong(100);
        when(upgradeRepository.save(any(SubscriptionPlanUpgrade.class))).thenAnswer(invocation -> {
            SubscriptionPlanUpgrade upgrade = invocation.getArgument(0);
            if (upgrade.getId() == null) upgrade.setId(sequence.incrementAndGet());
            upgrade.setUpdatedAt(clock.instant());
            upgradeStore.put(upgrade.getId(), upgrade);
            return upgrade;
        });
        when(upgradeRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(upgradeStore.get((Long) invocation.getArgument(0))));
        when(upgradeRepository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(upgradeStore.get((Long) invocation.getArgument(0))));
        when(upgradeRepository.findBySubscriptionIdAndStatusIn(any(), any())).thenAnswer(invocation -> upgradeStore.values().stream()
            .filter(upgrade -> upgrade.getSubscription().getId().equals(invocation.getArgument(0)))
            .filter(upgrade -> ((Collection<?>) invocation.getArgument(1)).contains(upgrade.getStatus())).toList());
        when(upgradeRepository.findBySubscriptionUserIdAndStatusIn(any(), any())).thenAnswer(invocation -> upgradeStore.values().stream()
            .filter(upgrade -> upgrade.getSubscription().getUser().getId().equals(invocation.getArgument(0)))
            .filter(upgrade -> ((Collection<?>) invocation.getArgument(1)).contains(upgrade.getStatus())).toList());
        when(upgradeRepository.findFirstBySubscriptionUserIdOrderByIdDesc(any())).thenAnswer(invocation -> upgradeStore.values().stream()
            .filter(upgrade -> upgrade.getSubscription().getUser().getId().equals(invocation.getArgument(0)))
            .max(Comparator.comparing(SubscriptionPlanUpgrade::getId)));
        when(upgradeRepository.findByExternalReference(any())).thenAnswer(invocation -> upgradeStore.values().stream()
            .filter(upgrade -> upgrade.getExternalReference().equals(invocation.getArgument(0))).findFirst());
        when(upgradeRepository.findByStatusInOrderByUpdatedAtAsc(any(), any())).thenAnswer(invocation -> upgradeStore.values().stream()
            .filter(upgrade -> ((Collection<?>) invocation.getArgument(0)).contains(upgrade.getStatus())).toList());
    }

    // --- A) BRONZE -> SILVER prorated upgrade ------------------------------------------------------

    @Test
    void bronzeToSilverOpensAProratedCheckoutAndGrantsNothingBeforePayment() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.SILVER);

        // (44.90 - 15.90) * 10 days / 30 days = 9.666... -> 9.67 (HALF_UP)
        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED);
        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.UPGRADE);
        assertThat(result.getChargeAmount()).isEqualByComparingTo("9.67");
        assertThat(result.getNextRenewalPrice()).isEqualByComparingTo("44.90");
        assertThat(result.getCheckoutUrl()).startsWith("https://");
        assertThat(result.isPending()).isTrue();
        // Nothing granted, nothing touched on the recurrence yet.
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("15.90");
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
        verify(client, never()).createPreapproval(any(), anyString());
        ArgumentCaptor<MercadoPagoPaymentPreferenceRequest> request = ArgumentCaptor.forClass(MercadoPagoPaymentPreferenceRequest.class);
        verify(client).createPaymentPreference(request.capture());
        assertThat(request.getValue().getAmount()).isEqualByComparingTo("9.67");
        assertThat(request.getValue().getCurrencyId()).isEqualTo("BRL");
        assertThat(request.getValue().getBackUrl()).isEqualTo(BACK_URL);
        assertThat(request.getValue().getExternalReference()).startsWith(SubscriptionPlanUpgradeSteps.REFERENCE_PREFIX);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        assertThat(upgrade.getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT);
        assertThat(upgrade.getCycleStart()).isEqualTo(CYCLE_START);
        assertThat(upgrade.getCycleEnd()).isEqualTo(PERIOD_END);
    }

    @Test
    void approvedPaymentConfirmedByTheProviderAppliesSilverOnTheSameRecurrence() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-1", "approved", "9.67");
        when(client.getPreapproval("pre-1")).thenReturn(preapproval("pre-1", "44.90"));

        PlanUpgradeStatusDTO status = service.upgradeStatus(user);

        assertThat(status.getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.APPLIED);
        assertThat(status.getTargetPlan()).isEqualTo(PlanCode.SILVER);
        assertThat(status.getChargeAmount()).isEqualByComparingTo("9.67");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("44.90");
        assertThat(subscription.getContractedVehicleCount()).isEqualTo(6);
        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("pre-1");
        verify(client).updatePreapprovalAmount(eq("pre-1"), eq(new BigDecimal("44.90")), eq("BRL"), anyString());
        verify(client, never()).createPreapproval(any(), anyString());
        assertThat(upgrade.getExternalPaymentId()).isEqualTo("pay-1");
        assertThat(upgrade.getAppliedAt()).isEqualTo(NOW);
    }

    @Test
    void approvedPaymentButRecurrenceNotYetConfirmedNeverGrantsTheNewPlan() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-1", "approved", "9.67");
        // Confirming GET still shows the old amount.

        PlanUpgradeStatusDTO status = service.upgradeStatus(user);

        assertThat(status.getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.APPLYING);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(upgrade.getApplyAttempts()).isEqualTo(1);
    }

    @Test
    void upgradePriceAlwaysComesFromPricingServiceForTheVehicleCountTheBackendItselfCounted() {
        // PLATINUM is progressive - its price cannot be its own monthlyBasePrice for 35 vehicles.
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan platinum = plan(2L, PlanCode.PLATINUM, 31, 100, "79.90");
        Subscription subscription = subscription(11L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-2", "79.90");
        givenLiveContract(subscription, platinum, 35, "92.40", "79.90");

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.PLATINUM);

        verify(pricingService).calculatePriceForPlan(PlanCode.PLATINUM, 35);
        assertThat(result.getNextRenewalPrice()).isEqualByComparingTo("92.40");
        // (92.40 - 79.90) / 3 = 4.1666... -> 4.17
        assertThat(result.getChargeAmount()).isEqualByComparingTo("4.17");
    }

    @Test
    void previewShowsTheServerComputedProratedAmountWithoutOpeningAnything() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");

        PlanChangePreviewDTO preview = service.previewChange(user, PlanCode.SILVER);

        assertThat(preview.getChangeType()).isEqualTo(PlanChangeType.UPGRADE);
        assertThat(preview.getChargeNow()).isEqualByComparingTo("9.67");
        assertThat(preview.getNewMonthlyPrice()).isEqualByComparingTo("44.90");
        assertThat(preview.getCycleEnd()).isEqualTo(PERIOD_END);
        assertThat(upgradeStore).isEmpty();
        verify(client, never()).createPaymentPreference(any());
    }

    // --- B) Rejected payment -------------------------------------------------------------------------

    @Test
    void rejectedPaymentKeepsBronzeAndTheRecurrenceUntouchedAndEventuallyExpires() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-9", "rejected", "9.67");

        PlanUpgradeStatusDTO whileOpen = service.upgradeStatus(user);
        assertThat(whileOpen.getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.AWAITING_PAYMENT);
        assertThat(whileOpen.isPaymentRejected()).isTrue();
        assertThat(whileOpen.getCheckoutUrl()).isNotNull(); // may retry within the checkout window

        clock.advance(SubscriptionPlanUpgradeSteps.CHECKOUT_WINDOW.plus(SubscriptionPlanUpgradeSteps.SETTLEMENT_GRACE));
        PlanUpgradeStatusDTO afterWindow = service.upgradeStatus(user);

        assertThat(afterWindow.getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.EXPIRED);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("15.90");
        assertThat(subscription.getPendingPlan()).isNull();
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    @Test
    void stillPendingPaymentNeverExpiresTheAttemptNorGrantsThePlan() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-2", "in_process", "9.67");
        clock.advance(Duration.ofHours(2));

        PlanUpgradeStatusDTO status = service.upgradeStatus(user);

        assertThat(status.getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.AWAITING_PAYMENT);
        assertThat(status.isPaymentPending()).isTrue();
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    // --- C) Upgrade while a downgrade is scheduled ----------------------------------------------------

    @Test
    void upgradeIsAllowedWhileADowngradeIsScheduledAndNeverClearsItBeforePayment() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(3L, PlanCode.GOLD, 16, 30, "79.90");
        Subscription subscription = subscription(12L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-3", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        // The recurrence already carries the scheduled downgrade's amount.
        givenLiveContract(subscription, gold, 6, "79.90", "15.90");

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.GOLD);

        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED);
        // Prorated against what was PAID for this cycle (SILVER 44.90), not the scheduled price:
        // (79.90 - 44.90) / 3 = 11.666... -> 11.67
        assertThat(result.getChargeAmount()).isEqualByComparingTo("11.67");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("15.90");
    }

    @Test
    void approvedUpgradeOverAScheduledDowngradeBecomesGoldAndDropsTheDowngrade() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(3L, PlanCode.GOLD, 16, 30, "79.90");
        Subscription subscription = subscription(12L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-3", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        givenLiveContract(subscription, gold, 6, "79.90", "15.90");
        service.changePlan(user, PlanCode.GOLD);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-3", "approved", "11.67");
        when(client.getPreapproval("pre-3")).thenReturn(preapproval("pre-3", "79.90"));

        assertThat(service.upgradeStatus(user).getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.APPLIED);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("79.90");
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPendingContractedPrice()).isNull();
        assertThat(subscription.getPendingContractedVehicleCount()).isNull();
        assertThat(subscription.getPlanChangeEffectiveAt()).isNull();
        assertThat(subscription.getPlanChangeRequestedAt()).isNull();
        verify(client).updatePreapprovalAmount(eq("pre-3"), eq(new BigDecimal("79.90")), eq("BRL"), anyString());
    }

    @Test
    void failedUpgradeOverAScheduledDowngradeKeepsSilverAndTheScheduledBronze() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(3L, PlanCode.GOLD, 16, 30, "79.90");
        Subscription subscription = subscription(12L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-3", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        givenLiveContract(subscription, gold, 6, "79.90", "15.90");
        service.changePlan(user, PlanCode.GOLD);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-4", "rejected", "11.67");
        clock.advance(SubscriptionPlanUpgradeSteps.CHECKOUT_WINDOW.plus(SubscriptionPlanUpgradeSteps.SETTLEMENT_GRACE));

        assertThat(service.upgradeStatus(user).getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.EXPIRED);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("44.90");
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("15.90");
        assertThat(subscription.getPlanChangeEffectiveAt()).isEqualTo(PERIOD_END);
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    // --- D) Undo downgrade -----------------------------------------------------------------------------

    @Test
    void undoDowngradeRestoresTheCurrentPlanAmountAndOnlyThenClearsTheSchedule() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(13L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(13L)).thenReturn(Optional.of(subscription));
        when(client.getPreapproval("pre-4")).thenReturn(preapproval("pre-4", "44.90"));

        PlanChangeResultDTO result = service.undoDowngrade(user);

        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.DOWNGRADE_UNDONE);
        assertThat(result.getNextRenewalPrice()).isEqualByComparingTo("44.90");
        assertThat(result.getChargeAmount()).isNull();
        verify(client).updatePreapprovalAmount(eq("pre-4"), eq(new BigDecimal("44.90")), eq("BRL"), anyString());
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPendingContractedPrice()).isNull();
        assertThat(subscription.getPendingContractedVehicleCount()).isNull();
        assertThat(subscription.getPlanChangeEffectiveAt()).isNull();
        assertThat(subscription.getPlanChangeRequestedAt()).isNull();
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
        verify(client, never()).createPaymentPreference(any());
    }

    @Test
    void undoDowngradeRejectedByTheProviderKeepsTheSchedule() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(13L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(13L)).thenReturn(Optional.of(subscription));
        when(client.updatePreapprovalAmount(eq("pre-4"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null));

        assertThatThrownBy(() -> service.undoDowngrade(user)).isInstanceOf(BillingDowngradeUndoRejectedException.class);

        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("15.90");
        verify(client, never()).getPreapproval(anyString());
    }

    @Test
    void undoDowngradeWhoseConfirmingGetStillShowsTheDowngradePriceKeepsTheSchedule() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(13L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(13L)).thenReturn(Optional.of(subscription));
        when(client.getPreapproval("pre-4")).thenReturn(preapproval("pre-4", "15.90"));

        assertThatThrownBy(() -> service.undoDowngrade(user)).isInstanceOf(BillingDowngradeUndoRejectedException.class);
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void undoDowngradeWithAnIndeterminateConfirmationKeepsTheSchedule() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(13L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "44.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(13L)).thenReturn(Optional.of(subscription));
        when(client.updatePreapprovalAmount(eq("pre-4"), any(), eq("BRL"), anyString())).thenThrow(new MercadoPagoException("timeout", true));
        when(client.getPreapproval("pre-4")).thenThrow(new MercadoPagoException("down", false, 503, null, null));

        assertThatThrownBy(() -> service.undoDowngrade(user)).isInstanceOf(BillingDowngradeUndoRejectedException.class);
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void undoDowngradeWithoutAScheduledDowngradeIsRejectedWithoutProviderCalls() {
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(13L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "44.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));

        assertThatThrownBy(() -> service.undoDowngrade(user)).isInstanceOf(BadRequestAlertException.class);
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    // --- E) Idempotency ----------------------------------------------------------------------------

    @Test
    void doubleClickOrRetryReusesTheSameAttemptAndNeverCreatesASecondCharge() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(client.searchPaymentIdsByExternalReference(anyString())).thenReturn(List.of());

        PlanChangeResultDTO first = service.changePlan(user, PlanCode.SILVER);
        PlanChangeResultDTO second = service.changePlan(user, PlanCode.SILVER);

        assertThat(second.getCheckoutUrl()).isEqualTo(first.getCheckoutUrl());
        assertThat(second.getChargeAmount()).isEqualByComparingTo(first.getChargeAmount());
        assertThat(upgradeStore).hasSize(1);
        verify(client, times(1)).createPaymentPreference(any());
    }

    @Test
    void aSecondRequestWhileTheFirstIsStillCreatingTheCheckoutNeverCreatesASecondPaymentLink() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(client.searchPaymentIdsByExternalReference(anyString())).thenReturn(List.of());
        java.util.concurrent.atomic.AtomicReference<Throwable> concurrent = new java.util.concurrent.atomic.AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            try {
                service.changePlan(user, PlanCode.SILVER); // the "double click" lands mid-creation
            } catch (RuntimeException failure) {
                concurrent.set(failure);
            }
            MercadoPagoPaymentPreferenceRequest request = invocation.getArgument(0);
            return new MercadoPagoPaymentPreference("pref-1", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=1", request.getExternalReference());
        }).when(client).createPaymentPreference(any());

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.SILVER);

        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED);
        assertThat(concurrent.get()).isInstanceOf(BillingPlanUpgradeInProgressException.class);
        verify(client, times(1)).createPaymentPreference(any());
        assertThat(upgradeStore).hasSize(1);
    }

    @Test
    void duplicatedWebhooksAndPollingApplyTheUpgradeExactlyOnce() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-1", "approved", "9.67");
        when(client.getPreapproval("pre-1")).thenReturn(preapproval("pre-1", "44.90"));

        assertThat(upgradeService.handlePaymentNotification("pay-1")).isTrue();
        assertThat(upgradeService.handlePaymentNotification("pay-1")).isTrue();
        upgradeService.reconcileOpenUpgrades(10);
        upgradeService.reconcileOpenUpgrades(10);
        service.upgradeStatus(user);

        assertThat(upgrade.getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.APPLIED);
        verify(client, times(1)).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.SILVER);
    }

    @Test
    void aDifferentTargetWhileAPaymentIsOpenIsRefused() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(3L, PlanCode.GOLD, 16, 30, "79.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(planRepository.findByCode(PlanCode.GOLD)).thenReturn(Optional.of(gold));
        when(client.searchPaymentIdsByExternalReference(anyString())).thenReturn(List.of());
        service.changePlan(user, PlanCode.SILVER);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.GOLD)).isInstanceOf(BillingPlanUpgradeInProgressException.class);
        verify(client, times(1)).createPaymentPreference(any());
    }

    @Test
    void aDowngradeWhileAnUpgradePaymentIsOpenIsRefused() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Plan gold = plan(3L, PlanCode.GOLD, 16, 30, "79.90");
        Subscription subscription = subscription(10L, silver, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "44.90");
        givenLiveContract(subscription, gold, 4, "79.90", "44.90");
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(client.searchPaymentIdsByExternalReference(anyString())).thenReturn(List.of());
        service.changePlan(user, PlanCode.GOLD);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanUpgradeInProgressException.class);
        assertThat(subscription.getPendingPlan()).isNull();
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    // --- F) Provider failures never grant the plan ------------------------------------------------

    @Test
    void definitivelyRejectedCheckoutFailsTheAttemptAndChargesNothing() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        Mockito.doThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null)).when(client).createPaymentPreference(any());

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanUpgradeCheckoutUnavailableException.class);

        assertThat(onlyUpgrade().getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.FAILED);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    @Test
    void ambiguousCheckoutCreationIsRetriedOnTheSameAttemptWithTheSameReference() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(client.searchPaymentIdsByExternalReference(anyString())).thenReturn(List.of());
        Mockito.doThrow(new MercadoPagoException("timeout", true))
            .doAnswer(invocation -> {
                MercadoPagoPaymentPreferenceRequest request = invocation.getArgument(0);
                return new MercadoPagoPaymentPreference("pref-2", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=2",
                    request.getExternalReference());
            })
            .when(client).createPaymentPreference(any());

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanUpgradeCheckoutUnavailableException.class);
        PlanChangeResultDTO retry = service.changePlan(user, PlanCode.SILVER);

        assertThat(retry.getStatus()).isEqualTo(PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED);
        assertThat(upgradeStore).hasSize(1);
        ArgumentCaptor<MercadoPagoPaymentPreferenceRequest> requests = ArgumentCaptor.forClass(MercadoPagoPaymentPreferenceRequest.class);
        verify(client, times(2)).createPaymentPreference(requests.capture());
        assertThat(requests.getAllValues().get(0).getExternalReference()).isEqualTo(requests.getAllValues().get(1).getExternalReference());
    }

    @Test
    void paidUpgradeWhoseRecurrenceUpdateKeepsFailingIsEscalatedAndNeverGranted() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        providerHasPayment(upgrade, "pay-1", "approved", "9.67");
        when(client.updatePreapprovalAmount(eq("pre-1"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null));

        for (int attempt = 0; attempt < SubscriptionPlanUpgradeSteps.MAX_APPLY_ATTEMPTS + 2; attempt++) {
            service.upgradeStatus(user);
        }

        assertThat(upgrade.getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.REQUIRES_REVIEW);
        assertThat(upgrade.getFailureReason()).isEqualTo("recurrence_update_failed");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("15.90");
        // Each retry used its own idempotency key, never replaying a failed key.
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(client, times(SubscriptionPlanUpgradeSteps.MAX_APPLY_ATTEMPTS)).updatePreapprovalAmount(eq("pre-1"), any(), eq("BRL"), keys.capture());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void approvedPaymentWithADifferentAmountIsEscalatedWithoutTouchingTheRecurrence() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        providerHasPayment(onlyUpgrade(), "pay-1", "approved", "1.00");

        assertThat(service.upgradeStatus(user).getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.REQUIRES_REVIEW);
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    @Test
    void paidUpgradeForASubscriptionCancelledMeanwhileIsEscalatedAndTheRecurrenceIsNeverRaised() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        providerHasPayment(onlyUpgrade(), "pay-1", "approved", "9.67");
        subscription.setCancelAtPeriodEnd(true);

        upgradeService.reconcileOpenUpgrades(10);

        assertThat(onlyUpgrade().getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.REQUIRES_REVIEW);
        assertThat(onlyUpgrade().getFailureReason()).isEqualTo("subscription_not_eligible");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        verify(client, never()).updatePreapprovalAmount(anyString(), any(), anyString(), anyString());
    }

    @Test
    void recurrenceReadFailureRefusesTheUpgradeBeforeAnyCheckout() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(client.getPreapproval("pre-1")).thenThrow(new MercadoPagoException("down", false, 503, null, null));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangePeriodUnconfirmedException.class);
        assertThat(upgradeStore).isEmpty();
        verify(client, never()).createPaymentPreference(any());
    }

    @Test
    void recurrenceChargingAnUnexpectedAmountRefusesTheUpgrade() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "99.99");

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangePeriodUnconfirmedException.class);
        verify(client, never()).createPaymentPreference(any());
    }

    @Test
    void cycleThatCannotBeConfirmedRefusesTheUpgradeInsteadOfEstimating() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        when(financialCoverage.evaluate(any(Subscription.class), any(Instant.class)))
            .thenReturn(new FinancialCoverageEvaluation(true, CommercialState.PAST_DUE, 7L, CYCLE_START, PERIOD_END, PERIOD_END, Reason.GRACE));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BillingPlanChangePeriodUnconfirmedException.class);
        assertThat(upgradeStore).isEmpty();
    }

    @Test
    void failedUpgradeResultIsNeverReportedAsSuccess() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        upgrade.setStatus(SubscriptionPlanUpgradeStatus.REQUIRES_REVIEW);

        assertThatThrownBy(() -> upgradeService.result(upgrade)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);
    }

    // --- H) Ownership -------------------------------------------------------------------------------

    @Test
    void upgradeStatusOnlyEverReadsTheCallersOwnAttempts() {
        User otherUser = new User();
        otherUser.setId(2L);

        assertThat(service.upgradeStatus(otherUser).getStatus()).isEqualTo(PlanUpgradeStatusDTO.Status.NONE);

        verify(upgradeRepository).findBySubscriptionUserIdAndStatusIn(eq(2L), any());
        verify(upgradeRepository).findFirstBySubscriptionUserIdOrderByIdDesc(2L);
        verify(upgradeRepository, never()).findBySubscriptionUserIdAndStatusIn(eq(1L), any());
    }

    @Test
    void paymentNotificationsOfNonUpgradePaymentsAreLeftToRecurringIngestion() {
        when(client.getPayment("pay-rec")).thenReturn(new MercadoPagoPayment("pay-rec", "approved", null, new BigDecimal("15.90"), "BRL",
            NOW, NOW, NOW, "checkout-ref", null));

        assertThat(upgradeService.handlePaymentNotification("pay-rec")).isFalse();
        verify(upgradeRepository, never()).findByExternalReference(anyString());
    }

    @Test
    void lateApprovedPaymentForAnExpiredAttemptIsEscalatedNeverDropped() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Plan silver = plan(2L, PlanCode.SILVER, 6, 15, "44.90");
        Subscription subscription = subscription(10L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-1", "15.90");
        givenLiveContract(subscription, silver, 6, "44.90", "15.90");
        service.changePlan(user, PlanCode.SILVER);
        SubscriptionPlanUpgrade upgrade = onlyUpgrade();
        when(client.searchPaymentIdsByExternalReference(upgrade.getExternalReference())).thenReturn(List.of());
        clock.advance(SubscriptionPlanUpgradeSteps.CHECKOUT_WINDOW.plus(SubscriptionPlanUpgradeSteps.SETTLEMENT_GRACE));
        service.upgradeStatus(user);
        assertThat(upgrade.getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.EXPIRED);
        providerHasPayment(upgrade, "pay-late", "approved", "9.67");

        assertThat(upgradeService.handlePaymentNotification("pay-late")).isTrue();

        assertThat(upgrade.getStatus()).isEqualTo(SubscriptionPlanUpgradeStatus.REQUIRES_REVIEW);
        assertThat(upgrade.getFailureReason()).isEqualTo("late_payment");
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    // --- Downgrade (5G.12 behavior kept) -------------------------------------------------------------

    @Test
    void goldToBronzeWithCompatibleFleetSchedulesAPendingDowngradeWithoutChangingTheCurrentPlan() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(13L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-4", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(13L)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.getPreapproval("pre-4")).thenReturn(preapproval("pre-4", "15.90"));

        PlanChangeResultDTO result = service.changePlan(user, PlanCode.BRONZE);

        assertThat(result.getChangeType()).isEqualTo(PlanChangeType.DOWNGRADE);
        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.DOWNGRADE_SCHEDULED);
        assertThat(result.isPending()).isTrue();
        assertThat(result.getEffectiveAt()).isEqualTo(PERIOD_END);
        assertThat(result.getChargeAmount()).isNull();
        verify(client).updatePreapprovalAmount(eq("pre-4"), eq(new BigDecimal("15.90")), eq("BRL"), anyString());
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getContractedPrice()).isEqualByComparingTo("79.90");
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingContractedPrice()).isEqualByComparingTo("15.90");
        assertThat(subscription.getPendingContractedVehicleCount()).isEqualTo(4);
        assertThat(subscription.getPlanChangeEffectiveAt()).isEqualTo(PERIOD_END);
        verify(client, never()).createPaymentPreference(any());
    }

    @Test
    void downgradeWithIncompatibleFleetIsRejectedBeforeAnyProviderCall() {
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(8L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BadRequestAlertException.class);

        Mockito.verifyNoInteractions(client);
        Mockito.verifyNoInteractions(subscriptionRepository);
        Mockito.verify(pricingService, never()).calculatePriceForPlan(any(), Mockito.anyInt());
    }

    @Test
    void definite4xxOnADowngradeRollsBackTheAlreadyCommittedPendingIntent() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(22L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-11", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(22L)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-11"), any(), eq("BRL"), anyString()))
            .thenThrow(new MercadoPagoException("rejected", false, 400, "bad_request", null));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);

        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPendingContractedPrice()).isNull();
        assertThat(subscription.getPlanChangeEffectiveAt()).isNull();
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
    }

    @Test
    void downgradeConfirmedByOldPriceRollsBackAndUnrelatedAmountFailsClosed() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(24L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-13", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(24L)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.updatePreapprovalAmount(eq("pre-13"), any(), eq("BRL"), anyString())).thenThrow(new MercadoPagoException("timeout", true));
        when(client.getPreapproval("pre-13")).thenReturn(preapproval("pre-13", "79.90"), preapproval("pre-13", "999.99"));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);
        assertThat(subscription.getPendingPlan()).isNull();

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeProviderRejectedException.class);
        // INDETERMINATE: never guesses a rollback.
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void repeatingADowngradeRequestWhileOnePendingRefusesRatherThanReplacingItSilently() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(27L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-16", "79.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(27L)).thenReturn(Optional.of(subscription));
        when(planRepository.findByCode(PlanCode.BRONZE)).thenReturn(Optional.of(bronze));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(pricingService.calculatePriceForPlan(PlanCode.BRONZE, 4)).thenReturn(new PricingResult(PlanCode.BRONZE, "BRONZE", 4, new BigDecimal("15.90"), List.of()));
        when(client.getPreapproval("pre-16")).thenReturn(preapproval("pre-16", "15.90"));

        service.changePlan(user, PlanCode.BRONZE);
        assertThatThrownBy(() -> service.changePlan(user, PlanCode.BRONZE)).isInstanceOf(BillingPlanChangeAlreadyPendingException.class);

        verify(client, times(1)).updatePreapprovalAmount(eq("pre-16"), any(), eq("BRL"), anyString());
    }

    // --- No-op / FREE ----------------------------------------------------------------------------

    @Test
    void sameTargetPlanIsAnExplicitConflictWithNoProviderCall() {
        Plan bronze = plan(1L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(14L, bronze, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-5", "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
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
        assertThat(result.getStatus()).isEqualTo(PlanChangeStatus.CANCELLATION_SCHEDULED);
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
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void adminGrantOrGrandfatheredSourcesNeverReachTheProvider() {
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
        when(planRepository.findByCode(PlanCode.SILVER)).thenReturn(Optional.of(silver));
        when(carRepository.countByUserIdAndActiveTrue(1L)).thenReturn(4L);
        when(financialCoverage.evaluate(eq(subscription), any(Instant.class)))
            .thenReturn(new FinancialCoverageEvaluation(false, CommercialState.UNRESOLVED, null, null, null, null, Reason.FINANCIAL_CONFLICT));

        assertThatThrownBy(() -> service.changePlan(user, PlanCode.SILVER)).isInstanceOf(BadRequestAlertException.class);
        Mockito.verifyNoInteractions(client);
    }

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
    void aPlanChangeForOneUserNeverQueriesAnotherUsersSubscriptions() {
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

    // --- Effectuation of a pending downgrade ------------------------------------------------------

    @Test
    void beforeEffectiveDateThePendingDowngradeIsNeverApplied() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(28L, gold, SubscriptionStatus.ACTIVE, PERIOD_END, "pre-17", "79.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(28L)).thenReturn(Optional.of(subscription));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan()).isNotNull();
        Mockito.verifyNoInteractions(financialCoverage);
    }

    @Test
    void effectiveDateReachedButRenewalNotYetConfirmedLeavesTheDowngradePending() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(29L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-18", "79.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(29L)).thenReturn(Optional.of(subscription));
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
        scheduleDowngrade(subscription, bronze, "15.90");
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(30L)).thenReturn(Optional.of(subscription));
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
    void renewalCompetencyAnchoredMinutesBeforeTheEffectiveDateStillPromotesTheDowngrade() {
        // Competency derived from startDate (date_created) + sequence; effectiveAt from next_payment_date.
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(32L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-21", "79.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        Instant effectiveAt = NOW.minusSeconds(3600);
        subscription.setPlanChangeEffectiveAt(effectiveAt);
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(32L)).thenReturn(Optional.of(subscription));
        when(financialCoverage.evaluate(eq(subscription), eq(NOW))).thenReturn(new FinancialCoverageEvaluation(true, CommercialState.ACTIVE,
            101L, effectiveAt.minusSeconds(25 * 60), effectiveAt.plus(Duration.ofDays(30)), null, Reason.PAID));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BRONZE);
        assertThat(subscription.getPendingPlan()).isNull();
    }

    @Test
    void theCompetencyPaidBeforeTheDowngradeNeverPromotesItDespiteTheTolerance() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(33L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-22", "79.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        Instant effectiveAt = NOW.minusSeconds(3600);
        subscription.setPlanChangeEffectiveAt(effectiveAt);
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(33L)).thenReturn(Optional.of(subscription));
        // Still the previous (GOLD) competency: started ~30 days before the effective date.
        when(financialCoverage.evaluate(eq(subscription), eq(NOW))).thenReturn(new FinancialCoverageEvaluation(true, CommercialState.ACTIVE,
            102L, effectiveAt.minus(Duration.ofDays(30)), effectiveAt.plus(Duration.ofDays(2)), null, Reason.PAID));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan().getCode()).isEqualTo(PlanCode.BRONZE);
    }

    @Test
    void chargebackOrRefundEvidenceAtEffectuationTimeNeverPromotesThePendingPlan() {
        Plan gold = plan(1L, PlanCode.GOLD, 16, 30, "79.90");
        Plan bronze = plan(2L, PlanCode.BRONZE, 3, 5, "15.90");
        Subscription subscription = subscription(31L, gold, SubscriptionStatus.ACTIVE, NOW, "pre-20", "79.90");
        scheduleDowngrade(subscription, bronze, "15.90");
        subscription.setPlanChangeEffectiveAt(NOW.minusSeconds(1));
        when(subscriptionRepository.findByUserIdAndSource(1L, SubscriptionSource.PAYMENT_PROVIDER)).thenReturn(List.of(subscription));
        when(subscriptionRepository.findById(31L)).thenReturn(Optional.of(subscription));
        when(financialCoverage.evaluate(eq(subscription), eq(NOW)))
            .thenReturn(new FinancialCoverageEvaluation(false, CommercialState.UNRESOLVED, null, null, null, null, Reason.REVERSED_OR_CANCELED));

        service.effectuateDueChangesForUser(1L);

        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.GOLD);
        assertThat(subscription.getPendingPlan()).isNotNull();
    }

    /** Minimal controllable clock so checkout-window expiry can be exercised deterministically. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
