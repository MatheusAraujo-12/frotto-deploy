package com.localuz.service.dto;

/** Official authorized-payment snapshot fetched from Mercado Pago. */
public class MercadoPagoAuthorizedPayment {
    private final String id; private final String status; private final String preapprovalId; private final String paymentStatus;
    public MercadoPagoAuthorizedPayment(String id, String status, String preapprovalId) { this(id,status,preapprovalId,null); }
    public MercadoPagoAuthorizedPayment(String id, String status, String preapprovalId, String paymentStatus) { this.id=id; this.status=status; this.preapprovalId=preapprovalId; this.paymentStatus=paymentStatus; }
    public String getId(){return id;} public String getStatus(){return status;} public String getPreapprovalId(){return preapprovalId;} public String getPaymentStatus(){return paymentStatus;}
}
