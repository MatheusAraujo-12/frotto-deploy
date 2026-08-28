package com.localuz.service;

import com.localuz.service.dto.MercadoPagoPreapproval;
import com.localuz.service.dto.MercadoPagoPreapprovalRequest;
import com.localuz.service.dto.MercadoPagoAuthorizedPayment;

/** Provider boundary. Implement HTTP calls only in the activation stage. */
public interface MercadoPagoClient {
    MercadoPagoPreapproval createPreapproval(MercadoPagoPreapprovalRequest request, String idempotencyKey);
    MercadoPagoPreapproval getPreapproval(String providerSubscriptionId);
    MercadoPagoPreapproval cancelPreapproval(String providerSubscriptionId, String idempotencyKey);
    MercadoPagoAuthorizedPayment getAuthorizedPayment(String authorizedPaymentId);
}
