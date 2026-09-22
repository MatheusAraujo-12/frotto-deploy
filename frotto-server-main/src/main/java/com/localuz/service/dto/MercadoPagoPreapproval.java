package com.localuz.service.dto;
public class MercadoPagoPreapproval {
    private final String id; private final String status; private final String externalReference; private final String initPoint;
    private final java.time.Instant dateCreated; private final java.time.Instant nextPaymentDate; private final java.time.Instant lastModified;
    private final Integer frequency;
    private final String frequencyType;
    /** auto_recurring.transaction_amount/currency_id - 5G.12: authoritative confirmation of a plan-change PUT (see MercadoPagoHttpClient#updatePreapprovalAmount). Null on any response shape that never populated this DTO field (older constructors below). */
    private final java.math.BigDecimal transactionAmount;
    private final String currencyId;
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint){this(id,status,ref,initPoint,null,null,null);}
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint,java.time.Instant created,java.time.Instant next,java.time.Instant modified){this(id,status,ref,initPoint,created,next,modified,null,null);}
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint,java.time.Instant created,java.time.Instant next,java.time.Instant modified,Integer frequency,String frequencyType){this(id,status,ref,initPoint,created,next,modified,frequency,frequencyType,null,null);}
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint,java.time.Instant created,java.time.Instant next,java.time.Instant modified,Integer frequency,String frequencyType,java.math.BigDecimal transactionAmount,String currencyId){this.id=id;this.status=status;this.externalReference=ref;this.initPoint=initPoint;this.dateCreated=created;this.nextPaymentDate=next;this.lastModified=modified;this.frequency=frequency;this.frequencyType=frequencyType;this.transactionAmount=transactionAmount;this.currencyId=currencyId;}
    public String getId(){return id;} public String getStatus(){return status;} public String getExternalReference(){return externalReference;} public String getInitPoint(){return initPoint;}
    public java.time.Instant getDateCreated(){return dateCreated;} public java.time.Instant getNextPaymentDate(){return nextPaymentDate;} public java.time.Instant getLastModified(){return lastModified;}
    public Integer getFrequency(){return frequency;}
    public String getFrequencyType(){return frequencyType;}
    public java.math.BigDecimal getTransactionAmount(){return transactionAmount;}
    public String getCurrencyId(){return currencyId;}
}
