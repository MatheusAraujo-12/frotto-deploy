package com.localuz.service;

import static org.mockito.Mockito.*;

import com.localuz.domain.*;
import com.localuz.domain.enumeration.*;
import com.localuz.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** In-memory persistence boundary; the production evaluator and financial predicate remain real. */
class FinancialCoverageFixture {
    static final Instant OCT = Instant.parse("2026-10-01T12:00:00Z");
    static final Instant NOV = Instant.parse("2026-11-01T12:00:00Z");
    static final Instant DEC = Instant.parse("2026-12-01T12:00:00Z");
    final BillingInvoiceRepository invoices = mock(BillingInvoiceRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final List<BillingInvoice> history = new ArrayList<>();
    final List<PaymentAttempt> payments = new ArrayList<>();
    final Subscription subscription = new Subscription();
    final Clock clock = Clock.fixed(NOV.plusSeconds(3600), ZoneOffset.UTC);
    final SubscriptionFinancialCoverageService service = new SubscriptionFinancialCoverageService(invoices, attempts, clock);

    FinancialCoverageFixture() {
        subscription.setId(1L);
        subscription.setSource(SubscriptionSource.PAYMENT_PROVIDER);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExternalProvider("MERCADO_PAGO");
        subscription.setStartDate(OCT);
        when(invoices.findBySubscriptionIdOrderByPeriodStartAsc(1L)).thenReturn(history);
        when(attempts.findByBillingInvoiceSubscriptionId(1L)).thenReturn(payments);
    }

    BillingInvoice invoice(Instant start, Instant end, boolean paid) {
        BillingInvoice invoice = new BillingInvoice();
        invoice.setId((long) history.size() + 1);
        invoice.setSubscription(subscription);
        invoice.setProvider("MERCADO_PAGO");
        invoice.setAmount(new BigDecimal("29.90"));
        invoice.setCurrency("BRL");
        invoice.setPeriodStart(start);
        invoice.setPeriodEnd(end);
        invoice.setDueAt(start);
        invoice.setGracePeriodEnd(start == null ? null : start.plus(Duration.ofHours(72)));
        invoice.setStatus(paid ? BillingInvoiceStatus.PAID : BillingInvoiceStatus.PENDING);
        history.add(invoice);
        if (paid) approve(invoice);
        return invoice;
    }

    PaymentAttempt approve(BillingInvoice invoice) {
        invoice.setStatus(BillingInvoiceStatus.PAID);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId((long) payments.size() + 1);
        attempt.setBillingInvoice(invoice);
        attempt.setProvider(invoice.getProvider());
        attempt.setExternalPaymentId("payment-" + attempt.getId());
        attempt.setStatus(PaymentAttemptStatus.APPROVED);
        attempt.setAmount(invoice.getAmount());
        attempt.setCurrency(invoice.getCurrency());
        attempt.setApprovedAt(invoice.getPeriodStart());
        payments.add(attempt);
        return attempt;
    }
}
