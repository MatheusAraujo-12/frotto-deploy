package com.localuz.service;

import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;

/** Provider boundary. Implement HTTP calls only in the activation stage. */
public interface MercadoPagoClient {
    com.localuz.service.dto.MercadoPagoAuthorizedPaymentPage searchAuthorizedPayments(String preapprovalId, int offset, int limit);
    MercadoPagoPreapproval createPreapproval(MercadoPagoPreapprovalRequest request, String idempotencyKey);
    MercadoPagoPreapproval getPreapproval(String providerSubscriptionId);
    MercadoPagoPreapproval cancelPreapproval(String providerSubscriptionId, String idempotencyKey);
    /** 5G.12: updates ONLY auto_recurring.transaction_amount/currency_id on an existing preapproval - never plan/status/frequency. The caller is responsible for authoritatively re-confirming via getPreapproval afterwards; this method never does so itself. */
    MercadoPagoPreapproval updatePreapprovalAmount(String providerSubscriptionId, java.math.BigDecimal transactionAmount, String currencyId, String idempotencyKey);
    MercadoPagoAuthorizedPayment getAuthorizedPayment(String authorizedPaymentId);
    com.localuz.service.dto.MercadoPagoPayment getPayment(String paymentId);
    java.util.Optional<MercadoPagoAuthorizedPayment> findAuthorizedPaymentByPaymentId(String paymentId);
}
