package com.localuz.service;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.PaymentAttempt;
import com.localuz.domain.Subscription;
import com.localuz.domain.enumeration.BillingInvoiceStatus;
import com.localuz.domain.enumeration.PaymentAttemptStatus;
import com.localuz.domain.enumeration.SubscriptionSource;
import com.localuz.repository.BillingInvoiceRepository;
import com.localuz.repository.PaymentAttemptRepository;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;
import com.localuz.service.dto.MercadoPagoPayment;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative financial observations only; never writes Subscription or grants access. */
@Service
public class MercadoPagoFinancialIngestion {
    private static final String PROVIDER = "MERCADO_PAGO";
    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoFinancialIngestion.class);
    private final MercadoPagoClient client;
    private final SubscriptionRepository subscriptions;
    private final BillingInvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;
    private final MercadoPagoBillingStatusMapper mapper;

    public MercadoPagoFinancialIngestion(MercadoPagoClient client, SubscriptionRepository subscriptions,
        BillingInvoiceRepository invoices, PaymentAttemptRepository attempts, MercadoPagoBillingStatusMapper mapper) {
        this.client = client;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.attempts = attempts;
        this.mapper = mapper;
    }

    public record FinancialSnapshot(MercadoPagoAuthorizedPayment charge, MercadoPagoPayment payment, MercadoPagoPreapproval preapproval) {}

    /**
     * A bare Payment paired with the preapproval it was correlated to via
     * point_of_interaction.transaction_data.subscription_id (see fetchPaymentSnapshot). Used
     * instead of FinancialSnapshot when there is no AuthorizedPayment charge to anchor on - the
     * Frotto checkout flow (POST /preapproval with status=pending + back_url/init_point, no
     * card_token_id) never produces one (see the 5G.9 root-cause investigation).
     */
    public record PaymentSnapshot(MercadoPagoPayment payment, MercadoPagoPreapproval preapproval) {}

    @Transactional
    public boolean ingest(String type, String resourceId) {
        if ("payment".equals(type)) {
            MercadoPagoPayment payment = client.getPayment(resourceId);
            if (payment == null || !resourceId.equals(payment.getId())) return ignored("payment_id_mismatch");
            PaymentSnapshot snapshot = fetchPaymentSnapshot(payment, () -> {});
            return snapshot != null && persistFromPayment(snapshot, null);
        }
        FinancialSnapshot snapshot = fetch(type, resourceId, null, () -> {});
        return snapshot != null && persist(snapshot, null);
    }

    /** Reconciliation calls this bean through its transactional proxy AFTER all HTTP has completed. */
    @Transactional
    public boolean ingestSnapshot(FinancialSnapshot snapshot, Long expectedSubscriptionId) {
        return persist(snapshot, expectedSubscriptionId);
    }

    /** Reconciliation calls this bean through its transactional proxy AFTER all HTTP has completed. */
    @Transactional
    public boolean ingestPaymentSnapshot(PaymentSnapshot snapshot, Long expectedSubscriptionId) {
        return persistFromPayment(snapshot, expectedSubscriptionId);
    }

    /** Charge-anchored path: only ever reachable for the "subscription_authorized_payment" event
     * type today. Nothing in the Frotto integration currently produces that resource (see the
     * 5G.9 investigation) - kept as-is in case a future flow (e.g. card_token_id) starts using it;
     * never used as a silent fallback for the "payment" event type, which is handled entirely by
     * fetchPaymentSnapshot/persistFromPayment instead (see ingest(...) above). */
    public FinancialSnapshot fetch(String type, String resourceId, String expectedPreapprovalId, Runnable beforeHttp) {
        if (!"subscription_authorized_payment".equals(type)) return ignoredSnapshot("unsupported_type");
        beforeHttp.run(); MercadoPagoAuthorizedPayment charge = client.getAuthorizedPayment(resourceId);
        if (charge == null || !resourceId.equals(charge.getId())) return ignoredSnapshot("charge_id_mismatch");
        if (expectedPreapprovalId != null && !expectedPreapprovalId.equals(charge.getPreapprovalId())) {
            return ignoredSnapshot("reconciliation_owner_mismatch");
        }
        MercadoPagoPayment payment = null;
        if (charge.getPaymentId() != null) { beforeHttp.run(); payment = client.getPayment(charge.getPaymentId()); }
        if (charge.getId() == null || charge.getPreapprovalId() == null
            || (payment != null && !Objects.equals(charge.getPaymentId(), payment.getId()))) {
            return ignoredSnapshot("correlation_mismatch");
        }
        validateRefundSnapshot(payment);
        beforeHttp.run(); MercadoPagoPreapproval preapproval = client.getPreapproval(charge.getPreapprovalId());
        if (preapproval == null || !charge.getPreapprovalId().equals(preapproval.getId())
            || conflictingReference(charge.getExternalReference(), preapproval.getExternalReference())
            || (payment != null && (conflictingReference(payment.getExternalReference(), preapproval.getExternalReference())
                || conflictingReference(payment.getExternalReference(), charge.getExternalReference())))) {
            return ignoredSnapshot("preapproval_mismatch");
        }
        return new FinancialSnapshot(charge, payment, preapproval);
    }

    /**
     * Correlates a bare Payment to its Subscription via
     * point_of_interaction.transaction_data.subscription_id - a documented Payment field present on
     * both the initial and every renewal charge (unlike external_reference, only confirmed for the
     * initial checkout payment). Mirrors fetch()'s authoritative-GET-then-cross-check shape: the
     * subscription_id is re-confirmed against a fresh GET /preapproval, never trusted on its own.
     */
    public PaymentSnapshot fetchPaymentSnapshot(MercadoPagoPayment payment, Runnable beforeHttp) {
        String subscriptionId = payment.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) return ignoredPaymentSnapshot("payment_missing_subscription_id");
        beforeHttp.run();
        MercadoPagoPreapproval preapproval = client.getPreapproval(subscriptionId);
        if (preapproval == null || !subscriptionId.equals(preapproval.getId())
            || conflictingReference(payment.getExternalReference(), preapproval.getExternalReference())) {
            return ignoredPaymentSnapshot("preapproval_mismatch");
        }
        return new PaymentSnapshot(payment, preapproval);
    }

    private PaymentSnapshot ignoredPaymentSnapshot(String reason) {
        ignored(reason);
        return null;
    }

    private FinancialSnapshot ignoredSnapshot(String reason) {
        ignored(reason);
        return null;
    }

    private boolean persist(FinancialSnapshot snapshot, Long expectedSubscriptionId) {
        MercadoPagoAuthorizedPayment charge = snapshot.charge();
        MercadoPagoPayment payment = snapshot.payment();
        MercadoPagoPreapproval preapproval = snapshot.preapproval();
        validateRefundSnapshot(payment);
        // Lock the existing parent before ANY financial read/write. The lock also serializes first inserts.
        Subscription subscription = subscriptions.findForFinancialIngestion(PROVIDER, preapproval.getId()).orElse(null);
        if (subscription == null || subscription.getSource() != SubscriptionSource.PAYMENT_PROVIDER
            || !PROVIDER.equals(subscription.getExternalProvider())
            || !preapproval.getId().equals(subscription.getExternalSubscriptionId())) return ignored("no_matching_subscription");

        if (expectedSubscriptionId != null && !expectedSubscriptionId.equals(subscription.getId())) {
            return ignored("reconciliation_subscription_mismatch");
        }
        String chargeCurrency = currency(charge.getCurrencyId());
        if (!validAmount(charge.getTransactionAmount()) || chargeCurrency == null) return ignored("incomplete_charge_money");
        BillingInvoice invoice = invoices.findByProviderAndExternalAuthorizedPaymentId(PROVIDER, charge.getId()).orElse(null);
        if (invoice != null && !Objects.equals(invoice.getSubscription().getId(), subscription.getId())) {
            return ignored("invoice_owner_mismatch");
        }
        boolean freshInvoice = invoice == null;
        if (freshInvoice) {
            if (mapper.unsettledInvoice(charge.getStatus()).isEmpty()) return ignored("unknown_charge_status");
            invoice = new BillingInvoice();
            invoice.setSubscription(subscription);
            invoice.setProvider(PROVIDER);
            invoice.setExternalAuthorizedPaymentId(charge.getId());
            invoice.setAmount(charge.getTransactionAmount().setScale(2));
            invoice.setCurrency(chargeCurrency);
            invoice.setStatus(mapper.unsettledInvoice(charge.getStatus()).orElseThrow());
        }
        boolean chargeMoneyMatches = moneyMatches(invoice, charge.getTransactionAmount(), chargeCurrency);
        if (!chargeMoneyMatches) LOG.warn("Mercado Pago financial inconsistency reason=charge_money_mismatch invoiceId={}", invoice.getId());
        MercadoPagoInvoiceTemporalEnricher.Result temporal = MercadoPagoInvoiceTemporalEnricher.enrich(invoice, charge, preapproval);
        if (temporal == MercadoPagoInvoiceTemporalEnricher.Result.CONFLICT) {
            LOG.warn("Mercado Pago temporal conflict invoiceId={} reason=competency_mismatch", invoice.getId());
        }
        if (freshInvoice || newer(charge.getLastModified(), invoice.getProviderUpdatedAt())) {
            invoice.setProviderStatus(code(charge.getStatus(), 64));
            invoice.setProviderUpdatedAt(charge.getLastModified());
            if (invoice.getProviderCreatedAt() == null) invoice.setProviderCreatedAt(charge.getDateCreated());
            if (!settled(invoice.getStatus())) mapper.unsettledInvoice(charge.getStatus()).ifPresent(invoice::setStatus);
        }
        invoice.setLastReconciledAt(Instant.now());
        invoices.saveAndFlush(invoice);
        upsertAttempt(invoice, charge, payment, chargeMoneyMatches);
        invoices.saveAndFlush(invoice);
        return true;
    }

    /**
     * Payment-anchored path (see PaymentSnapshot). Mirrors persist()'s invariants - authoritative
     * subscription lock before any read/write, money/status validation, idempotent invoice/attempt
     * upsert, fail-closed on any ambiguity - but keys the invoice on the competency period derived
     * from subscription_sequence.number (MercadoPagoPaymentInvoiceTemporalEnricher) instead of an
     * external_authorized_payment_id, since no AuthorizedPayment charge exists for this flow.
     */
    private boolean persistFromPayment(PaymentSnapshot snapshot, Long expectedSubscriptionId) {
        MercadoPagoPayment payment = snapshot.payment();
        MercadoPagoPreapproval preapproval = snapshot.preapproval();
        validateRefundSnapshot(payment);
        // Lock the existing parent before ANY financial read/write. The lock also serializes first inserts.
        Subscription subscription = subscriptions.findForFinancialIngestion(PROVIDER, preapproval.getId()).orElse(null);
        if (subscription == null || subscription.getSource() != SubscriptionSource.PAYMENT_PROVIDER
            || !PROVIDER.equals(subscription.getExternalProvider())
            || !preapproval.getId().equals(subscription.getExternalSubscriptionId())) return ignored("no_matching_subscription");

        if (expectedSubscriptionId != null && !expectedSubscriptionId.equals(subscription.getId())) {
            return ignored("reconciliation_subscription_mismatch");
        }
        String paymentCurrency = currency(payment.getCurrencyId());
        Optional<PaymentAttemptStatus> mappedStatus = mapper.payment(payment.getStatus());
        if (!validAmount(payment.getTransactionAmount()) || paymentCurrency == null || mappedStatus.isEmpty()) {
            return ignored("incomplete_or_unknown_payment");
        }
        PaymentAttempt existingAttempt = attempts.findByProviderAndExternalPaymentId(PROVIDER, payment.getId()).orElse(null);
        BillingInvoice invoice = existingAttempt != null ? existingAttempt.getBillingInvoice()
            : findOrCreateInvoiceForPayment(subscription, payment, preapproval, paymentCurrency, mappedStatus.get());
        if (!Objects.equals(invoice.getSubscription().getId(), subscription.getId())) return ignored("invoice_owner_mismatch");
        boolean freshInvoice = invoice.getId() == null;
        boolean chargeMoneyMatches = freshInvoice || moneyMatches(invoice, payment.getTransactionAmount(), paymentCurrency);
        if (!chargeMoneyMatches) LOG.warn("Mercado Pago financial inconsistency reason=charge_money_mismatch invoiceId={}", invoice.getId());
        MercadoPagoPaymentInvoiceTemporalEnricher.Result temporal = MercadoPagoPaymentInvoiceTemporalEnricher.enrich(
            invoice, payment, subscription, preapproval.getFrequency(), preapproval.getFrequencyType());
        if (temporal == MercadoPagoPaymentInvoiceTemporalEnricher.Result.CONFLICT) {
            LOG.warn("Mercado Pago temporal conflict invoiceId={} reason=competency_mismatch", invoice.getId());
        }
        if (freshInvoice || newer(payment.getDateLastUpdated(), invoice.getProviderUpdatedAt())) {
            invoice.setProviderUpdatedAt(payment.getDateLastUpdated());
            if (invoice.getProviderCreatedAt() == null) invoice.setProviderCreatedAt(payment.getDateCreated());
        }
        invoice.setLastReconciledAt(Instant.now());
        invoices.saveAndFlush(invoice);
        upsertAttemptFromPayment(invoice, existingAttempt, payment, mappedStatus.get(), paymentCurrency, chargeMoneyMatches);
        invoices.saveAndFlush(invoice);
        return true;
    }

    /**
     * Looks up the invoice for the competency period this payment belongs to (so a retry payment
     * for the same cycle attaches to the SAME invoice instead of fabricating a duplicate), falling
     * back to a fresh, unperiodized invoice when the period cannot be derived yet -
     * SubscriptionFinancialCoverageService already treats a null period as never covered, so this
     * never manufactures coverage out of an unresolved competency.
     */
    private BillingInvoice findOrCreateInvoiceForPayment(Subscription subscription, MercadoPagoPayment payment,
        MercadoPagoPreapproval preapproval, String currency, PaymentAttemptStatus status) {
        Integer sequence = payment.getSubscriptionSequenceNumber();
        if (sequence != null && sequence >= 1 && preapproval.getFrequency() != null) {
            Instant[] period = MercadoPagoPaymentInvoiceTemporalEnricher.computePeriod(
                subscription.getStartDate(), sequence, preapproval.getFrequency(), preapproval.getFrequencyType());
            if (period != null) {
                BillingInvoice existing = invoices
                    .findBySubscriptionIdAndPeriodStartAndPeriodEnd(subscription.getId(), period[0], period[1]).orElse(null);
                if (existing != null) return existing;
            }
        }
        BillingInvoice invoice = new BillingInvoice();
        invoice.setSubscription(subscription);
        invoice.setProvider(PROVIDER);
        invoice.setAmount(payment.getTransactionAmount().setScale(2));
        invoice.setCurrency(currency);
        invoice.setStatus(status == PaymentAttemptStatus.PROCESSING ? BillingInvoiceStatus.PROCESSING : BillingInvoiceStatus.PENDING);
        return invoice;
    }

    /** Payment-anchored twin of upsertAttempt: no "provisional" placeholder is needed since a bare
     * Payment (unlike an AuthorizedPayment charge) is never observed before its own evidence exists. */
    private void upsertAttemptFromPayment(BillingInvoice invoice, PaymentAttempt existing, MercadoPagoPayment payment,
        PaymentAttemptStatus status, String paymentCurrency, boolean chargeMoneyMatches) {
        boolean firstPayment = existing == null;
        if (!firstPayment && !newer(payment.getDateLastUpdated(), existing.getProviderUpdatedAt())) return;
        if (!firstPayment && BillingPaymentEvidence.reversed(existing) && status == PaymentAttemptStatus.APPROVED
            && payment.getRefundedAmount() == null) {
            // An incomplete approval cannot erase an already confirmed reversal.
            ignored("incomplete_approval_after_reversal");
            return;
        }
        PaymentAttempt attempt = existing == null ? newAttempt(invoice) : existing;
        attempt.setExternalPaymentId(payment.getId());
        // A terminal financial observation must not regress to a pending state, even on a newer snapshot.
        if (!firstPayment && terminal(attempt.getStatus()) && !terminal(status)) return;
        attempt.setStatus(status);
        attempt.setProviderStatus(code(payment.getStatus(), 64));
        if (code(payment.getStatusDetail(), 128) != null) attempt.setStatusDetail(code(payment.getStatusDetail(), 128));
        attempt.setAmount(payment.getTransactionAmount().setScale(2));
        attempt.setCurrency(paymentCurrency);
        if (attempt.getProviderCreatedAt() == null) attempt.setProviderCreatedAt(payment.getDateCreated());
        if (attempt.getAttemptedAt() == null) attempt.setAttemptedAt(payment.getDateCreated());
        attempt.setProviderUpdatedAt(payment.getDateLastUpdated());
        if (attempt.getApprovedAt() == null && payment.getDateApproved() != null) attempt.setApprovedAt(payment.getDateApproved());
        if (validAmount(payment.getRefundedAmount()) && payment.getRefundedAmount().compareTo(payment.getTransactionAmount()) <= 0) {
            attempt.setRefundedAmount(payment.getRefundedAmount().setScale(2));
        }
        attempt.setObservedAt(Instant.now());
        attempts.saveAndFlush(attempt);
        if (!chargeMoneyMatches || !moneyMatches(invoice, attempt.getAmount(), attempt.getCurrency())) {
            LOG.warn("Mercado Pago financial inconsistency reason=payment_money_mismatch invoiceId={} attemptId={}", invoice.getId(), attempt.getId());
            return;
        }
        var evidence = attempts.findByBillingInvoiceIdOrderByIdAsc(invoice.getId());
        boolean approved = evidence.stream().anyMatch(a -> BillingPaymentEvidence.approved(invoice, a));
        if (approved) {
            invoice.setStatus(BillingInvoiceStatus.PAID);
            if (invoice.getPaidAt() == null && attempt.getStatus() == PaymentAttemptStatus.APPROVED) invoice.setPaidAt(attempt.getApprovedAt());
        } else if (evidence.stream().anyMatch(a -> a.getStatus() == PaymentAttemptStatus.CHARGEDBACK)) {
            invoice.setStatus(BillingInvoiceStatus.CHARGEDBACK);
        } else if (evidence.stream().anyMatch(BillingPaymentEvidence::reversed)) {
            invoice.setStatus(BillingInvoiceStatus.REFUNDED);
        }
    }

    private void upsertAttempt(BillingInvoice invoice, MercadoPagoAuthorizedPayment charge, MercadoPagoPayment payment,
        boolean chargeMoneyMatches) {
        // One provisional observation per charge, later enriched by the first actual payment.
        String provisionalId = "authorized_payment:" + charge.getId();
        PaymentAttempt provisional = attempts.findByProviderAndExternalAttemptId(PROVIDER, provisionalId).orElse(null);
        PaymentAttempt attempt = payment == null ? provisional
            : attempts.findByProviderAndExternalPaymentId(PROVIDER, payment.getId()).orElse(null);
        if (attempt == null && provisional != null && (provisional.getExternalPaymentId() == null
            || (payment != null && payment.getId().equals(provisional.getExternalPaymentId())))) attempt = provisional;
        if (attempt != null && !Objects.equals(attempt.getBillingInvoice().getId(), invoice.getId())) {
            throw new MercadoPagoException("Mercado Pago financial identity conflict", false);
        }
        if (payment == null) {
            if (attempt != null) return; // Never replace payment evidence with an authorized-payment snapshot.
            attempt = newAttempt(invoice);
            attempt.setExternalAttemptId(provisionalId);
            attempt.setStatus(PaymentAttemptStatus.PENDING);
            attempt.setAmount(invoice.getAmount());
            attempt.setCurrency(invoice.getCurrency());
            // No payment timestamp/status is fabricated from the charge's clock or embedded payment status.
            attempts.saveAndFlush(attempt);
            return;
        }
        String paymentCurrency = currency(payment.getCurrencyId());
        Optional<PaymentAttemptStatus> status = mapper.payment(payment.getStatus());
        if (!validAmount(payment.getTransactionAmount()) || paymentCurrency == null || status.isEmpty()) {
            ignored("incomplete_or_unknown_payment");
            return;
        }
        boolean firstPayment = attempt == null || attempt.getExternalPaymentId() == null;
        if (!firstPayment && !newer(payment.getDateLastUpdated(), attempt.getProviderUpdatedAt())) return;
        if (!firstPayment && BillingPaymentEvidence.reversed(attempt) && status.get() == PaymentAttemptStatus.APPROVED
            && payment.getRefundedAmount() == null) {
            // An incomplete approval cannot erase an already confirmed reversal.
            ignored("incomplete_approval_after_reversal");
            return;
        }
        if (attempt == null) {
            attempt = newAttempt(invoice);
            if (provisional == null) attempt.setExternalAttemptId(provisionalId);
        }
        attempt.setExternalPaymentId(payment.getId());
        // A terminal financial observation must not regress to a pending state, even on a newer snapshot.
        if (!firstPayment && terminal(attempt.getStatus()) && !terminal(status.get())) return;
        attempt.setStatus(status.get());
        attempt.setProviderStatus(code(payment.getStatus(), 64));
        if (code(payment.getStatusDetail(), 128) != null) attempt.setStatusDetail(code(payment.getStatusDetail(), 128));
        attempt.setAmount(payment.getTransactionAmount().setScale(2));
        attempt.setCurrency(paymentCurrency);
        if (attempt.getProviderCreatedAt() == null) attempt.setProviderCreatedAt(payment.getDateCreated());
        if (attempt.getAttemptedAt() == null) attempt.setAttemptedAt(payment.getDateCreated());
        attempt.setProviderUpdatedAt(payment.getDateLastUpdated());
        if (attempt.getApprovedAt() == null && payment.getDateApproved() != null) attempt.setApprovedAt(payment.getDateApproved());
        if (validAmount(payment.getRefundedAmount()) && payment.getRefundedAmount().compareTo(payment.getTransactionAmount()) <= 0) {
            attempt.setRefundedAmount(payment.getRefundedAmount().setScale(2));
        }
        attempt.setObservedAt(Instant.now());
        attempts.saveAndFlush(attempt);
        if (!chargeMoneyMatches || !moneyMatches(invoice, attempt.getAmount(), attempt.getCurrency())) {
            LOG.warn("Mercado Pago financial inconsistency reason=payment_money_mismatch invoiceId={} attemptId={}", invoice.getId(), attempt.getId());
            return;
        }
        // Compute from the current evidence for THIS invoice, so a failed retry cannot erase a paid one.
        var evidence = attempts.findByBillingInvoiceIdOrderByIdAsc(invoice.getId());
        boolean approved = evidence.stream()
            .anyMatch(a -> BillingPaymentEvidence.approved(invoice, a));
        if (approved) {
            invoice.setStatus(BillingInvoiceStatus.PAID);
            if (invoice.getPaidAt() == null && attempt.getStatus() == PaymentAttemptStatus.APPROVED) invoice.setPaidAt(attempt.getApprovedAt());
        } else if (evidence.stream().anyMatch(a -> a.getStatus() == PaymentAttemptStatus.CHARGEDBACK)) {
            invoice.setStatus(BillingInvoiceStatus.CHARGEDBACK);
        } else if (evidence.stream().anyMatch(BillingPaymentEvidence::reversed)) {
            // REFUNDED also represents insufficient net coverage after a partial refund; amounts remain on each attempt.
            invoice.setStatus(BillingInvoiceStatus.REFUNDED);
        }
    }

    private void validateRefundSnapshot(MercadoPagoPayment payment) {
        if (payment == null) return;
        BigDecimal refund = payment.getRefundedAmount();
        boolean partial = "partially_refunded".equalsIgnoreCase(payment.getStatusDetail());
        if ((refund != null && (!validAmount(refund) || !validAmount(payment.getTransactionAmount())
                || refund.compareTo(payment.getTransactionAmount()) > 0))
            || (partial && (refund == null || refund.signum() <= 0))) {
            throw new MercadoPagoException("Invalid authoritative refund observation", false);
        }
    }

    private PaymentAttempt newAttempt(BillingInvoice invoice) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setBillingInvoice(invoice);
        attempt.setProvider(PROVIDER);
        return attempt;
    }

    private boolean newer(Instant incoming, Instant stored) {
        return incoming != null && (stored == null || incoming.isAfter(stored));
    }

    private boolean conflictingReference(String value, String expected) {
        return value != null && expected != null && !value.equals(expected);
    }

    private boolean settled(BillingInvoiceStatus status) {
        return status == BillingInvoiceStatus.PAID || status == BillingInvoiceStatus.REFUNDED || status == BillingInvoiceStatus.CHARGEDBACK;
    }

    private boolean terminal(PaymentAttemptStatus status) {
        return status == PaymentAttemptStatus.APPROVED || status == PaymentAttemptStatus.REFUNDED || status == PaymentAttemptStatus.CHARGEDBACK;
    }

    private boolean moneyMatches(BillingInvoice invoice, BigDecimal amount, String currency) {
        return BillingPaymentEvidence.moneyMatches(invoice, amount, currency);
    }

    private boolean validAmount(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.stripTrailingZeros().scale() <= 2
            && value.precision() - value.scale() <= 19;
    }

    private String currency(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    private String code(String value, int length) {
        return value != null && value.matches("[A-Za-z0-9_-]{1," + length + "}") ? value : null;
    }

    private boolean ignored(String reason) {
        LOG.warn("Mercado Pago financial observation ignored reason={}", reason);
        return false;
    }
}
