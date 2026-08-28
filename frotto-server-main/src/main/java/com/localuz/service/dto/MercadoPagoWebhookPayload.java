package com.localuz.service.dto;

public class MercadoPagoWebhookPayload {
    private String type;
    private Data data;
    public String getType(){return type;} public void setType(String value){type=value;}
    public Data getData(){return data;} public void setData(Data value){data=value;}
    public static class Data {
        private String id;
        public String getId(){return id;} public void setId(String value){id=value;}
    }
}
