package com.localuz.service;

import static com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState.*;
import static com.localuz.service.dto.FinancialCoverageEvaluation.Reason.*;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.PaymentAttempt;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingInvoiceStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.domain.enumeration.SubscriptionStatus;
import com.localuz.repository.BillingInvoiceRepository;
import com.localuz.repository.PaymentAttemptRepository;
import com.localuz.service.dto.FinancialCoverageEvaluation;
import com.localuz.service.dto.FinancialCoverageEvaluation.CommercialState;
import com.localuz.service.dto.FinancialCoverageEvaluation.Reason;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumes 5G.3 financial evidence and 5G.4A periods. Never writes, derives periods or calls the provider. */
@Service
@Transactional(readOnly = true)
public class SubscriptionFinancialCoverageService {
    private final BillingInvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final Clock clock;

    @Autowired
    public SubscriptionFinancialCoverageService(BillingInvoiceRepository invoices, PaymentAttemptRepository attempts) {
        this(invoices, attempts, Clock.systemUTC());
    }

    public SubscriptionFinancialCoverageService(BillingInvoiceRepository invoices, PaymentAttemptRepository attempts, Clock clock) {
        this.invoices = invoices;
        this.attempts = attempts;
        this.clock = clock;
    }

    public FinancialCoverageEvaluation evaluate(Subscription subscription) {
        return evaluate(subscription, clock.instant());
    }

    public FinancialCoverageEvaluation evaluate(Subscription subscription, Instant now) {
        Objects.requireNonNull(now, "Evaluation instant is required");
        if (subscription.getSource() != SubscriptionSource.PAYMENT_PROVIDER) {
            return result(false, UNRESOLVED, null, NOT_PAYMENT_PROVIDER);
        }
        if (subscription.getId() == null) return result(false, AWAITING_PAYMENT, null, NO_INVOICE);
        List<BillingInvoice> history = invoices.findBySubscriptionIdOrderByPeriodStartAsc(subscription.getId());
        // An unanchored invoice cannot safely be classified as old/current/future. Never guess from audit timestamps.
        if (history.stream().anyMatch(i -> i.getPeriodStart() == null)) {
            return result(false, UNRESOLVED, null, INCOMPLETE_PERIOD);
        }
        List<BillingInvoice> started = history.stream().filter(i -> !i.getPeriodStart().isAfter(now))
            .sorted(Comparator.comparing(BillingInvoice::getPeriodStart).reversed()).toList();
        if (started.isEmpty()) {
            return result(false, stoppedState(subscription, AWAITING_PAYMENT), null, history.isEmpty() ? NO_INVOICE : FUTURE_PERIOD);
        }
        BillingInvoice current = started.get(0);
        if (started.size() > 1 && current.getPeriodStart().equals(started.get(1).getPeriodStart())) {
            return result(false, UNRESOLVED, current, AMBIGUOUS_PERIOD);
        }
        if (!completePeriod(current)) return result(false, UNRESOLVED, current, INCOMPLETE_PERIOD);
        List<PaymentAttempt> evidence = attempts.findByBillingInvoiceSubscriptionId(subscription.getId());
        if (paid(current, evidence) && now.isBefore(current.getPeriodEnd())) {
            if (!paidPeriodAllowed(subscription, current)) {
                return result(false, stoppedState(subscription, CANCELED), current, RENEWAL_STOPPED);
            }
            return result(true, subscription.getStatus() == SubscriptionStatus.CANCELED ? CANCELED : ACTIVE, current, PAID);
        }
        boolean previouslyPaid = started.stream().skip(1).anyMatch(i -> completePeriod(i) && paid(i, evidence));
        CommercialState state = stoppedState(subscription, previouslyPaid ? PAST_DUE : AWAITING_PAYMENT);
        if (!now.isBefore(current.getPeriodEnd())) {
            return result(false, stoppedState(subscription, paid(current, evidence) ? EXPIRED : state), current, PERIOD_EXPIRED);
        }
        if (current.getStatus() == BillingInvoiceStatus.REFUNDED || current.getStatus() == BillingInvoiceStatus.CHARGEDBACK
            || current.getStatus() == BillingInvoiceStatus.CANCELED
            || evidence.stream().anyMatch(a -> belongsTo(current, a) && BillingPaymentEvidence.reversed(a))) {
            return result(false, state, current, REVERSED_OR_CANCELED);
        }
        if (evidence.stream().anyMatch(a -> belongsTo(current, a)
            && !BillingPaymentEvidence.moneyMatches(current, a.getAmount(), a.getCurrency()))) {
            return result(false, UNRESOLVED, current, FINANCIAL_CONFLICT);
        }
        if (current.getDueAt() == null || current.getGracePeriodEnd() == null) {
            return result(false, UNRESOLVED, current, INCOMPLETE_PERIOD);
        }
        // Reuse the model's existing temporal validation; never assign or repair a deadline here.
        if (!current.isPeriodValid()) {
            return result(false, UNRESOLVED, current, INCOMPLETE_PERIOD);
        }
        if (renewalStopped(subscription)) return result(false, state, current, RENEWAL_STOPPED);
        if (!previouslyPaid) return result(false, AWAITING_PAYMENT, current, NO_APPROVED_PAYMENT);
        BillingInvoice previous = started.get(1);
        boolean uniquePrevious = started.size() < 3 || !previous.getPeriodStart().equals(started.get(2).getPeriodStart());
        if (!uniquePrevious || !completePeriod(previous) || !paidBefore(previous, evidence, current.getPeriodStart())
            || !previous.getPeriodEnd().equals(current.getPeriodStart())) {
            return result(false, PAST_DUE, current, NO_CONTIGUOUS_PAID_PREDECESSOR);
        }
        // A PAID invoice without valid evidence must not obtain substitute grace.
        if (current.getStatus() != BillingInvoiceStatus.PENDING && current.getStatus() != BillingInvoiceStatus.PROCESSING
            && current.getStatus() != BillingInvoiceStatus.PAST_DUE) {
            return result(false, UNRESOLVED, current, NO_APPROVED_PAYMENT);
        }
        return now.isBefore(current.getGracePeriodEnd())
            ? result(true, PAST_DUE, current, GRACE)
            : result(false, PAST_DUE, current, GRACE_EXPIRED);
    }

    private static boolean completePeriod(BillingInvoice invoice) {
        return invoice.getPeriodStart() != null && invoice.getPeriodEnd() != null
            && invoice.getPeriodStart().isBefore(invoice.getPeriodEnd());
    }

    private static boolean paid(BillingInvoice invoice, List<PaymentAttempt> evidence) {
        return invoice.getStatus() == BillingInvoiceStatus.PAID && evidence.stream().anyMatch(a -> approved(invoice, a));
    }

    private static boolean paidBefore(BillingInvoice invoice, List<PaymentAttempt> evidence, Instant boundary) {
        // Late settlement of an already expired invoice cannot retroactively manufacture previous paid coverage.
        return invoice.getStatus() == BillingInvoiceStatus.PAID && evidence.stream().anyMatch(a -> approved(invoice, a)
            && a.getApprovedAt() != null && a.getApprovedAt().isBefore(boundary));
    }

    private static boolean approved(BillingInvoice invoice, PaymentAttempt attempt) {
        return belongsTo(invoice, attempt) && Objects.equals(invoice.getProvider(), attempt.getProvider())
            && attempt.getExternalPaymentId() != null && BillingPaymentEvidence.approved(invoice, attempt);
    }

    private static boolean belongsTo(BillingInvoice invoice, PaymentAttempt attempt) {
        return attempt.getBillingInvoice() != null && invoice.getId() != null
            && invoice.getId().equals(attempt.getBillingInvoice().getId());
    }

    private static boolean renewalStopped(Subscription subscription) {
        return Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getCanceledAt() != null
            || subscription.getStatus() == SubscriptionStatus.CANCELED || subscription.getStatus() == SubscriptionStatus.PAUSED;
    }

    private static boolean paidPeriodAllowed(Subscription subscription, BillingInvoice invoice) {
        // A confirmation timestamp bounds which competencies predate cancellation; it never supplies a service period.
        if (subscription.getCanceledAt() != null) return invoice.getPeriodStart().isBefore(subscription.getCanceledAt());
        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || subscription.getStatus() == SubscriptionStatus.CANCELED) {
            // Pending confirmation / legacy cancellation: use the existing 5F boundary only as a restriction, never as paid proof.
            return subscription.getCurrentPeriodEnd() != null && !invoice.getPeriodEnd().isAfter(subscription.getCurrentPeriodEnd());
        }
        return true;
    }

    private static CommercialState stoppedState(Subscription subscription, CommercialState otherwise) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELED || subscription.getCanceledAt() != null) return CANCELED;
        if (subscription.getStatus() == SubscriptionStatus.PAUSED) return PAUSED;
        return otherwise;
    }

    private static FinancialCoverageEvaluation result(boolean covered, CommercialState state, BillingInvoice invoice, Reason reason) {
        return new FinancialCoverageEvaluation(covered, state, invoice == null ? null : invoice.getId(),
            covered ? invoice.getPeriodStart() : null,
            covered ? (reason == GRACE ? invoice.getGracePeriodEnd() : invoice.getPeriodEnd()) : null,
            invoice == null ? null : invoice.getGracePeriodEnd(), reason);
    }
}
