package com.localuz.service.dto;
public class MercadoPagoPreapproval {
    private final String id; private final String status; private final String externalReference; private final String initPoint;
    public MercadoPagoPreapproval(String id,String status,String ref,String initPoint){this.id=id;this.status=status;this.externalReference=ref;this.initPoint=initPoint;}
    public String getId(){return id;} public String getStatus(){return status;} public String getExternalReference(){return externalReference;} public String getInitPoint(){return initPoint;}
}
