package com.localuz.service.dto;

import java.util.List;

/** Discovery identifiers only. Search results never constitute payment evidence. */
public record MercadoPagoAuthorizedPaymentPage(int offset, int limit, int total, List<String> ids) {
    public MercadoPagoAuthorizedPaymentPage { ids = List.copyOf(ids); }
}
