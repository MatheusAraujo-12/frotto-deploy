package com.localuz.service.dto;
import java.math.BigDecimal;
public class MercadoPagoPreapprovalRequest {
    private final String externalReference; private final String payerEmail; private final String reason;
    private final BigDecimal transactionAmount; private final String currencyId; private final String backUrl;
    public MercadoPagoPreapprovalRequest(String ref,String email,String reason,BigDecimal amount,String currency,String backUrl){this.externalReference=ref;this.payerEmail=email;this.reason=reason;this.transactionAmount=amount;this.currencyId=currency;this.backUrl=backUrl;}
    public String getExternalReference(){return externalReference;} public String getPayerEmail(){return payerEmail;}
    public String getReason(){return reason;} public BigDecimal getTransactionAmount(){return transactionAmount;}
    public String getCurrencyId(){return currencyId;} public String getBackUrl(){return backUrl;}
}
