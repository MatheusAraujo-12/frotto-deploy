package com.localuz.service;

import com.localuz.domain.BillingInvoice;
import com.localuz.domain.PaymentAttempt;
import com.localuz.domain.enumeration.PaymentAttemptStatus;
import java.math.BigDecimal;

/** Shared settlement predicate from 5G.3; correlation and normalization remain in ingestion. */
final class BillingPaymentEvidence {
    private BillingPaymentEvidence() {}

    static boolean moneyMatches(BillingInvoice invoice, BigDecimal amount, String currency) {
        return amount != null && invoice.getAmount() != null && invoice.getCurrency() != null
            && invoice.getAmount().compareTo(amount) == 0 && invoice.getCurrency().equals(currency);
    }

    static boolean approved(BillingInvoice invoice, PaymentAttempt attempt) {
        return attempt.getStatus() == PaymentAttemptStatus.APPROVED
            && moneyMatches(invoice, attempt.getAmount(), attempt.getCurrency());
    }
}
