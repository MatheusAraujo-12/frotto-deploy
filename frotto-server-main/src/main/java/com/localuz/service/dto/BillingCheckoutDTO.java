package com.localuz.service.dto;
import com.localuz.domain.BillingCheckout;
import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.BillingCycle;
import com.localuz.domain.enumeration.PlanCode;
import java.math.BigDecimal;
public class BillingCheckoutDTO {
    private final Long checkoutId; private final PlanCode planCode; private final BigDecimal quotedPrice;
    private final BillingCycle billingCycle; private final BillingCheckoutStatus status; private final String checkoutUrl;
    private BillingCheckoutDTO(BillingCheckout c) { checkoutId=c.getId(); planCode=c.getPlan().getCode(); quotedPrice=c.getQuotedPrice(); billingCycle=c.getBillingCycle(); status=c.getStatus(); checkoutUrl=c.getInitPoint(); }
    public static BillingCheckoutDTO from(BillingCheckout c){return new BillingCheckoutDTO(c);}
    public Long getCheckoutId(){return checkoutId;} public PlanCode getPlanCode(){return planCode;} public BigDecimal getQuotedPrice(){return quotedPrice;}
    public BillingCycle getBillingCycle(){return billingCycle;} public BillingCheckoutStatus getStatus(){return status;} public String getCheckoutUrl(){return checkoutUrl;}
}
