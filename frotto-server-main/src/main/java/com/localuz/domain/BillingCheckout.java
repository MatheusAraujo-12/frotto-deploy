package com.localuz.domain;

import com.localuz.domain.enumeration.BillingCheckoutStatus;
import com.localuz.domain.enumeration.BillingCycle;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "billing_checkout")
public class BillingCheckout implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "jhi_user_id") private User user;
    @ManyToOne(optional = false) @JoinColumn(name = "plan_id") private Plan plan;
    @Column(name = "vehicle_count", nullable = false) private Integer vehicleCount;
    @Column(name = "quoted_price", precision = 21, scale = 2, nullable = false) private BigDecimal quotedPrice;
    @Enumerated(EnumType.STRING) @Column(name = "billing_cycle", length = 16, nullable = false) private BillingCycle billingCycle;
    @Column(name = "external_reference", length = 36, nullable = false, unique = true) private String externalReference;
    @Column(name = "provider", length = 32, nullable = false) private String provider;
    @Column(name = "provider_subscription_id", length = 128) private String providerSubscriptionId;
    @Column(name = "provider_status", length = 32) private String providerStatus;
    @Column(name = "idempotency_key", length = 36, nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "init_point", length = 1024) private String initPoint;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 32, nullable = false) private BillingCheckoutStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public User getUser(){return user;} public void setUser(User v){user=v;}
    public Plan getPlan(){return plan;} public void setPlan(Plan v){plan=v;}
    public Integer getVehicleCount(){return vehicleCount;} public void setVehicleCount(Integer v){vehicleCount=v;}
    public BigDecimal getQuotedPrice(){return quotedPrice;} public void setQuotedPrice(BigDecimal v){quotedPrice=v;}
    public BillingCycle getBillingCycle(){return billingCycle;} public void setBillingCycle(BillingCycle v){billingCycle=v;}
    public String getExternalReference(){return externalReference;} public void setExternalReference(String v){externalReference=v;}
    public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public String getProviderSubscriptionId(){return providerSubscriptionId;} public void setProviderSubscriptionId(String v){providerSubscriptionId=v;}
    public String getProviderStatus(){return providerStatus;} public void setProviderStatus(String v){providerStatus=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getInitPoint(){return initPoint;} public void setInitPoint(String v){initPoint=v;}
    public BillingCheckoutStatus getStatus(){return status;} public void setStatus(BillingCheckoutStatus v){status=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
}
