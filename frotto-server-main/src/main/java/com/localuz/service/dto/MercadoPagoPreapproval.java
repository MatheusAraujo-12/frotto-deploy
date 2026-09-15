package com.localuz.service.dto;
public class MercadoPagoPreapproval {
    private final String id; private final String status; private final String externalReference; private final String initPoint;
    private final java.time.Instant dateCreated; private final java.time.Instant nextPaymentDate; private final java.time.Instant lastModified;
    private final Integer frequency;
    private final String frequencyType;
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint){this(id,status,ref,initPoint,null,null,null);}
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint,java.time.Instant created,java.time.Instant next,java.time.Instant modified){this(id,status,ref,initPoint,created,next,modified,null,null);}
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint,java.time.Instant created,java.time.Instant next,java.time.Instant modified,Integer frequency,String frequencyType){this.id=id;this.status=status;this.externalReference=ref;this.initPoint=initPoint;this.dateCreated=created;this.nextPaymentDate=next;this.lastModified=modified;this.frequency=frequency;this.frequencyType=frequencyType;}
    public String getId(){return id;} public String getStatus(){return status;} public String getExternalReference(){return externalReference;} public String getInitPoint(){return initPoint;}
    public java.time.Instant getDateCreated(){return dateCreated;} public java.time.Instant getNextPaymentDate(){return nextPaymentDate;} public java.time.Instant getLastModified(){return lastModified;}
    public Integer getFrequency(){return frequency;}
    public String getFrequencyType(){return frequencyType;}
}
