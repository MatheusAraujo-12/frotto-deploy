package com.localuz.service;

import com.localuz.domain.Plan;
import com.localuz.domain.Subscription;
import com.localuz.domain.User;
import com.localuz.domain.enumeration.PlanCode;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.PlanRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import java.time.Instant;
import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only effective subscription selection. Provider coverage comes from financial
 * competencies; administrative grants retain their existing eligibility rules.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final List<SubscriptionStatus> CURRENT_STATUSES = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    /**
     * Deterministic tie-break when a user has more than one valid current subscription (e.g. a
     * PAYMENT_PROVIDER subscription plus a temporary ADMIN_GRANT on top of it). Lower number
     * wins. ADMIN_GRANT outranks PAYMENT_PROVIDER so a manual override always takes effect
     * immediately without touching the underlying paid subscription; PAYMENT_PROVIDER outranks
     * GRANDFATHERED so a real subscription always supersedes a legacy backfill once one exists.
     * SubscriptionAdminService relies on this ordering too: it only ever closes a conflicting
     * ADMIN_GRANT, never a PAYMENT_PROVIDER or GRANDFATHERED row, precisely because this
     * comparator - not "which row is currently on top" - is what decides the effective plan.
     */
    private static final Map<SubscriptionSource, Integer> SOURCE_PRIORITY = new EnumMap<>(SubscriptionSource.class);

    static {
        SOURCE_PRIORITY.put(SubscriptionSource.ADMIN_GRANT, 0);
        SOURCE_PRIORITY.put(SubscriptionSource.PAYMENT_PROVIDER, 1);
        SOURCE_PRIORITY.put(SubscriptionSource.GRANDFATHERED, 2);
    }

    private static final Comparator<Subscription> BY_EFFECTIVE_PRIORITY = Comparator
        .comparingInt((Subscription s) -> SOURCE_PRIORITY.get(s.getSource()))
        .thenComparing(Subscription::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SubscriptionFinancialCoverageService financialCoverage;
    private final Clock clock;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        SubscriptionFinancialCoverageService financialCoverage) {
        this(subscriptionRepository, planRepository, financialCoverage, Clock.systemUTC());
    }

    SubscriptionService(SubscriptionRepository subscriptionRepository, PlanRepository planRepository,
        SubscriptionFinancialCoverageService financialCoverage, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.financialCoverage = financialCoverage;
        this.clock = clock;
    }

    /**
     * A single evaluation instant for all candidates. Include canceled/paused provider rows:
     * they may still own a valid paid competency. Their persisted status/period is not paid proof.
     * No mutation, HTTP request or ingestion lock is needed to expire access on a read.
     */
    public Optional<Subscription> getCurrentSubscription(User user) {
        Instant now = clock.instant();
        return subscriptionRepository
            .findByUserIdOrderByStartDateDesc(user.getId())
            .stream()
            .sorted(BY_EFFECTIVE_PRIORITY)
            .filter(subscription -> subscription.getSource() == SubscriptionSource.PAYMENT_PROVIDER
                ? isProviderSubscriptionEntitled(subscription, now)
                : CURRENT_STATUSES.contains(subscription.getStatus()) && isCurrentlyValid(subscription, now))
            .findFirst();
    }

    /**
     * 5G.10 regression fix: a PAYMENT_PROVIDER Subscription authoritatively confirmed ACTIVE by
     * Mercado Pago (webhook signature + GET /preapproval, or the equivalent reconciliation path -
     * see MercadoPagoWebhookProcessor#reconcile; never a browser redirect/query string, which never
     * writes Subscription) must grant the contracted plan immediately. BillingInvoice/PaymentAttempt
     * exist for renewals, arrears, retries, refunds and history - they are not a precondition for
     * this initial activation. Product regression this fixes: before, a brand-new ACTIVE
     * subscription with zero invoices (checkout just authorized, nothing ingested yet) was reported
     * as FREE by SubscriptionFinancialCoverageService's fail-closed "no evidence" verdict.
     *
     * The bypass is deliberately narrow: it only applies when financialCoverage has NO invoice
     * evidence at all (Reason.NO_INVOICE) - once even one invoice exists, its outcome (paid,
     * reversed, expired, financial conflict...) is authoritative again through
     * SubscriptionFinancialCoverageService, completely unchanged. A refund/chargeback recorded
     * against the current competency still revokes coverage exactly as before; this method never
     * weakens that.
     *
     * CANCELED/PAUSED are handled the same way for the identical reason: with no invoice evidence,
     * fall back to the Subscription's own currentPeriodEnd (set from Mercado Pago's own
     * next_payment_date when the preapproval was last authorized - never fabricated here) to decide
     * whether an already-contracted period is still running. A cancelled contract keeps the plan
     * only until that date, then reverts; a paused one is never treated as reactivated (status stays
     * PAUSED), it only keeps whatever period was already paid for. Missing currentPeriodEnd stays
     * fail-closed, per docs/billing-recurring-contract-5g1.md's "datas ausentes nunca significam
     * validade infinita".
     */
    private boolean isProviderSubscriptionEntitled(Subscription subscription, Instant now) {
        FinancialCoverageEvaluation evaluation = financialCoverage.evaluate(subscription, now);
        if (evaluation.covered()) {
            return true;
        }
        if (evaluation.reason() != FinancialCoverageEvaluation.Reason.NO_INVOICE) {
            return false;
        }
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return true;
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCELED || subscription.getStatus() == SubscriptionStatus.PAUSED) {
            Instant periodEnd = subscription.getCurrentPeriodEnd();
            return periodEnd != null && periodEnd.isAfter(now);
        }
        return false;
    }

    private static boolean isCurrentlyValid(Subscription subscription, Instant now) {
        Instant expiresAt = subscription.getGrantExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return false;
        }
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        return periodEnd == null || !periodEnd.isBefore(now);
    }

    public Plan getEffectivePlan(User user) {
        return getCurrentSubscription(user).map(Subscription::getPlan).orElseGet(this::getFreePlan);
    }

    public Plan getFreePlan() {
        return planRepository
            .findByCode(PlanCode.FREE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "FREE plan is not configured"));
    }
}
