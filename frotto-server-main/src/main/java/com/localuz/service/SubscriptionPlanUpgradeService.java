package com.localuz.service;

import com.localuz.config.MercadoPagoProperties;
import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.SubscriptionPlanUpgrade;
import com.localuz.domain.enumeration.SubscriptionPlanUpgradeStatus;
import com.localuz.repository.SubscriptionPlanUpgradeRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.MercadoPagoPayment;
import com.localuz.service.dto.MercadoPagoPaymentPreference;
import com.localuz.service.dto.MercadoPagoPaymentPreferenceRequest;
import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.PlanChangeResultDTO;
import com.localuz.service.dto.PlanChangeStatus;
import com.localuz.service.dto.PlanChangeType;
import com.localuz.service.dto.PlanUpgradeStatusDTO;
import com.localuz.web.rest.errors.BillingPlanChangePeriodUnconfirmedException;
import com.localuz.web.rest.errors.BillingPlanChangeProviderRejectedException;
import com.localuz.web.rest.errors.BillingPlanUpgradeCheckoutUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 5G.12.1: prorated upgrade of an existing PAYMENT_PROVIDER subscription.
 *
 *   quote (authoritative GET /preapproval + PricingService price + paid cycle)
 *   -> open attempt (AWAITING_PAYMENT, user lock)                  [nothing granted]
 *   -> one-off Checkout Pro preference for the prorated amount      [user pays on Mercado Pago]
 *   -> authoritative GET /v1/payments/{id} approved, exact amount  (webhook, polling or scheduler)
 *      -> APPLYING
 *   -> PUT auto_recurring.transaction_amount on the SAME preapproval
 *   -> authoritative GET /preapproval confirms the new amount
 *   -> finalize: plan / contracted price / vehicle count switched, scheduled downgrade cleared
 *
 * Never @Transactional itself: every provider call happens between the short transactions of
 * SubscriptionPlanUpgradeSteps (same rule as SubscriptionPlanChangeService / cancellation). The
 * browser's return from Mercado Pago is never trusted - it only triggers a server-side
 * reconciliation (statusForUser). A preapproval PUT is never assumed to charge anything (confirmed
 * in staging that it does not), which is why the prorated difference is a separate payment.
 */
@Service
public class SubscriptionPlanUpgradeService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanUpgradeService.class);
    static final String CURRENCY = "BRL";

    private final SubscriptionPlanUpgradeSteps steps;
    private final SubscriptionPlanUpgradeRepository upgrades;
    private final MercadoPagoClient client;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final MercadoPagoProperties properties;
    private final Clock clock;

    @Autowired
    public SubscriptionPlanUpgradeService(SubscriptionPlanUpgradeSteps steps, SubscriptionPlanUpgradeRepository upgrades,
        MercadoPagoClient client, SubscriptionFinancialCoverageService financialCoverage, MercadoPagoProperties properties) {
        this(steps, upgrades, client, financialCoverage, properties, Clock.systemUTC());
    }

    SubscriptionPlanUpgradeService(SubscriptionPlanUpgradeSteps steps, SubscriptionPlanUpgradeRepository upgrades,
        MercadoPagoClient client, SubscriptionFinancialCoverageService financialCoverage, MercadoPagoProperties properties, Clock clock) {
        this.steps = steps;
        this.upgrades = upgrades;
        this.client = client;
        this.financialCoverage = financialCoverage;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Server-side quote. The recurrence must be the caller's live contract (authorized, same id, and
     * currently charging either the contracted price or the scheduled downgrade's price); the cycle
     * comes from PlanUpgradeProration#resolveCycle. Any doubt refuses the upgrade - never estimates.
     */
    public SubscriptionPlanUpgradeSteps.Quote quote(Subscription subscription, Plan targetPlan, BigDecimal targetPrice, int vehicleCount) {
        String externalId = subscription.getExternalSubscriptionId();
        MercadoPagoPreapproval preapproval;
        try {
            preapproval = client.getPreapproval(externalId);
        } catch (MercadoPagoException failure) {
            log.warn("Plan upgrade quote could not read the recurrence subscriptionId={} category={} httpStatus={}",
                subscription.getId(), failure.getCategory(), failure.getHttpStatus());
            throw new BillingPlanChangePeriodUnconfirmedException();
        }
        if (preapproval == null || !externalId.equals(preapproval.getId()) || !"authorized".equalsIgnoreCase(preapproval.getStatus())
            || !recurringAmountIsKnown(subscription, preapproval)) {
            log.warn("Plan upgrade quote refused subscriptionId={} reason=recurrence_state_unexpected", subscription.getId());
            throw new BillingPlanChangePeriodUnconfirmedException();
        }
        Instant now = clock.instant();
        FinancialCoverageEvaluation evaluation = financialCoverage.evaluate(subscription, now);
        PlanUpgradeProration.Cycle cycle = PlanUpgradeProration.resolveCycle(evaluation, preapproval, subscription.getStartDate(), now)
            .orElseThrow(() -> {
                log.warn("Plan upgrade quote refused subscriptionId={} reason=cycle_unconfirmed coverageReason={}",
                    subscription.getId(), evaluation.reason());
                return new BillingPlanChangePeriodUnconfirmedException();
            });
        BigDecimal charge = PlanUpgradeProration.charge(subscription.getContractedPrice(), targetPrice, cycle.start(), cycle.end(), now);
        return new SubscriptionPlanUpgradeSteps.Quote(subscription.getId(), subscription.getPlan().getId(), subscription.getContractedPrice(),
            targetPlan, targetPrice, vehicleCount, charge, CURRENCY, cycle.start(), cycle.end());
    }

    private static boolean recurringAmountIsKnown(Subscription subscription, MercadoPagoPreapproval preapproval) {
        BigDecimal amount = preapproval.getTransactionAmount();
        if (amount == null || !CURRENCY.equalsIgnoreCase(preapproval.getCurrencyId()) || subscription.getContractedPrice() == null) {
            return false;
        }
        return amount.compareTo(subscription.getContractedPrice()) == 0
            || (subscription.getPendingContractedPrice() != null && amount.compareTo(subscription.getPendingContractedPrice()) == 0);
    }

    /** Starts (or idempotently resumes) a prorated upgrade. Never grants the target plan by itself. */
    public PlanChangeResultDTO requestUpgrade(Long userId, Subscription subscription, Plan targetPlan, BigDecimal targetPrice, int vehicleCount) {
        SubscriptionPlanUpgradeSteps.Quote quote = quote(subscription, targetPlan, targetPrice, vehicleCount);
        SubscriptionPlanUpgrade upgrade = steps.openOrReuse(userId, quote).upgrade();
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.APPLYING) {
            upgrade = apply(upgrade.getId());
        } else if (upgrade.getCheckoutUrl() == null) {
            upgrade = createCheckout(upgrade);
        }
        return result(upgrade);
    }

    private SubscriptionPlanUpgrade createCheckout(SubscriptionPlanUpgrade upgrade) {
        MercadoPagoPaymentPreferenceRequest request = new MercadoPagoPaymentPreferenceRequest(upgrade.getExternalReference(),
            "Frotto - upgrade proporcional para o plano " + upgrade.getTargetPlan().getName(), upgrade.getChargeAmount(),
            upgrade.getCurrency(), properties.getBackUrl(), upgrade.getCheckoutExpiresAt());
        MercadoPagoPaymentPreference preference;
        try {
            preference = client.createPaymentPreference(request);
        } catch (MercadoPagoException failure) {
            log.warn("Plan upgrade checkout creation failed upgradeId={} ambiguous={} category={} httpStatus={} providerErrorCode={}",
                upgrade.getId(), failure.isAmbiguous(), failure.getCategory(), failure.getHttpStatus(), failure.getSafeProviderErrorCode());
            // Ambiguous: keep the attempt (never shown to the user, so nothing can be paid through
            // it) - the next request for the same target retries with the same external_reference.
            if (failure.isAmbiguous()) steps.markCheckoutUnconfirmed(upgrade.getId());
            else steps.markCheckoutFailed(upgrade.getId());
            throw new BillingPlanUpgradeCheckoutUnavailableException();
        }
        return steps.recordCheckout(upgrade.getId(), preference.getId(), preference.getCheckoutUrl());
    }

    /**
     * APPLYING -> APPLIED: PUT the target price on the SAME preapproval, then trust only an
     * authoritative GET. Any failure leaves the attempt APPLYING (retried by polling/scheduler with a
     * fresh idempotency key per attempt) until MAX_APPLY_ATTEMPTS escalates it - the plan is never
     * granted on an unconfirmed recurrence, and a paid attempt is never silently dropped.
     */
    SubscriptionPlanUpgrade apply(Long upgradeId) {
        Optional<SubscriptionPlanUpgrade> prepared = steps.prepareApply(upgradeId);
        if (prepared.isEmpty()) {
            return upgrades.findById(upgradeId).orElseThrow();
        }
        SubscriptionPlanUpgrade upgrade = prepared.get();
        String externalId = upgrade.getSubscription().getExternalSubscriptionId();
        String idempotencyKey = "plan-upgrade-" + upgrade.getId() + "-amount-" + upgrade.getApplyAttempts();
        try {
            client.updatePreapprovalAmount(externalId, upgrade.getTargetPrice(), upgrade.getCurrency(), idempotencyKey);
        } catch (MercadoPagoException failure) {
            log.warn("Plan upgrade recurrence update failed upgradeId={} ambiguous={} category={} httpStatus={}",
                upgrade.getId(), failure.isAmbiguous(), failure.getCategory(), failure.getHttpStatus());
            if (!failure.isAmbiguous()) {
                return steps.recordApplyFailure(upgradeId, "recurrence_update_rejected");
            }
        }
        MercadoPagoPreapproval current;
        try {
            current = client.getPreapproval(externalId);
        } catch (MercadoPagoException failure) {
            return steps.recordApplyFailure(upgradeId, "recurrence_confirmation_unavailable");
        }
        if (current != null && externalId.equals(current.getId()) && current.getTransactionAmount() != null
            && current.getTransactionAmount().compareTo(upgrade.getTargetPrice()) == 0
            && upgrade.getCurrency().equalsIgnoreCase(current.getCurrencyId())) {
            return steps.finalizeApplied(upgradeId);
        }
        return steps.recordApplyFailure(upgradeId, "recurrence_not_confirmed");
    }

    /**
     * Moves one attempt forward using only authoritative provider reads: payment discovery by
     * external_reference + individual GETs while AWAITING_PAYMENT, then apply while APPLYING.
     * Provider failures leave the state untouched (retried later).
     */
    public SubscriptionPlanUpgrade reconcile(Long upgradeId) {
        SubscriptionPlanUpgrade upgrade = upgrades.findById(upgradeId).orElseThrow();
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.AWAITING_PAYMENT) {
            try {
                List<SubscriptionPlanUpgradeSteps.ObservedPayment> observed = new ArrayList<>();
                for (String paymentId : client.searchPaymentIdsByExternalReference(upgrade.getExternalReference())) {
                    MercadoPagoPayment payment = client.getPayment(paymentId);
                    if (payment != null && paymentId.equals(payment.getId())) observed.add(observed(payment));
                }
                upgrade = steps.recordPayments(upgradeId, observed, true);
            } catch (MercadoPagoException failure) {
                log.warn("Plan upgrade payment reconciliation failed upgradeId={} category={} httpStatus={}",
                    upgradeId, failure.getCategory(), failure.getHttpStatus());
                return upgrade;
            }
        }
        if (upgrade.getStatus() == SubscriptionPlanUpgradeStatus.APPLYING) {
            upgrade = apply(upgradeId);
        }
        return upgrade;
    }

    /** Reconciles the caller's open attempt(s), then returns the latest attempt (if any). Only ever the caller's own rows. */
    public Optional<SubscriptionPlanUpgrade> reconcileForUser(Long userId) {
        for (SubscriptionPlanUpgrade open : upgrades.findBySubscriptionUserIdAndStatusIn(userId, SubscriptionPlanChangeSteps.OPEN_UPGRADE_STATUSES)) {
            reconcile(open.getId());
        }
        return upgrades.findFirstBySubscriptionUserIdOrderByIdDesc(userId);
    }

    public PlanUpgradeStatusDTO statusForUser(Long userId) {
        return reconcileForUser(userId).map(upgrade -> PlanUpgradeStatusDTO.from(upgrade, clock.instant())).orElseGet(PlanUpgradeStatusDTO::none);
    }

    /**
     * Webhook entry point for topic "payment". Returns true when the payment belongs to a prorated
     * upgrade (external_reference prefix), in which case it must NOT be fed to the recurring
     * financial ingestion. Only records the authoritative observation - applying (which PUTs the
     * recurrence) is left to polling/scheduler, outside the webhook's transaction.
     */
    public boolean handlePaymentNotification(String paymentId) {
        MercadoPagoPayment payment = client.getPayment(paymentId);
        if (payment == null || !paymentId.equals(payment.getId())) return false;
        String reference = payment.getExternalReference();
        if (reference == null || !reference.startsWith(SubscriptionPlanUpgradeSteps.REFERENCE_PREFIX)) return false;
        Optional<SubscriptionPlanUpgrade> upgrade = upgrades.findByExternalReference(reference);
        if (upgrade.isEmpty()) {
            log.warn("Plan upgrade payment ignored paymentId={} reason=unknown_reference", paymentId);
            return true;
        }
        steps.recordPayments(upgrade.get().getId(), List.of(observed(payment)), false);
        return true;
    }

    /** Scheduler sweep over open attempts, oldest update first. */
    public int reconcileOpenUpgrades(int batchSize) {
        int processed = 0;
        for (SubscriptionPlanUpgrade open : upgrades.findByStatusInOrderByUpdatedAtAsc(SubscriptionPlanChangeSteps.OPEN_UPGRADE_STATUSES,
            PageRequest.of(0, batchSize))) {
            try {
                reconcile(open.getId());
                processed++;
            } catch (RuntimeException failure) {
                log.warn("Plan upgrade reconciliation outcome=OPERATIONAL_FAILURE upgradeId={}", open.getId());
            }
        }
        return processed;
    }

    PlanChangeResultDTO result(SubscriptionPlanUpgrade upgrade) {
        PlanChangeStatus status = switch (upgrade.getStatus()) {
            case APPLIED -> PlanChangeStatus.UPGRADE_APPLIED;
            case APPLYING -> PlanChangeStatus.UPGRADE_PAYMENT_PENDING;
            case AWAITING_PAYMENT -> SubscriptionPlanUpgradeSteps.isPendingPaymentStatus(upgrade.getLastPaymentStatus())
                ? PlanChangeStatus.UPGRADE_PAYMENT_PENDING : PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED;
            default -> throw new BillingPlanChangeProviderRejectedException();
        };
        String checkoutUrl = status == PlanChangeStatus.UPGRADE_PAYMENT_REQUIRED ? upgrade.getCheckoutUrl() : null;
        return new PlanChangeResultDTO(upgrade.getFromPlan().getCode(), upgrade.getTargetPlan().getCode(), PlanChangeType.UPGRADE, status,
            upgrade.getAppliedAt(), upgrade.getTargetPrice(), upgrade.getChargeAmount(), upgrade.getTargetPrice(), checkoutUrl);
    }

    private static SubscriptionPlanUpgradeSteps.ObservedPayment observed(MercadoPagoPayment payment) {
        return new SubscriptionPlanUpgradeSteps.ObservedPayment(payment.getId(), payment.getStatus(), payment.getTransactionAmount(),
            payment.getCurrencyId(), payment.getExternalReference(), payment.getDateApproved());
    }
}
