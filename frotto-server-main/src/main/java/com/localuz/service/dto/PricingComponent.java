package com.localuz.service.dto;

import java.math.BigDecimal;

/**
 * One progressive-pricing tier that contributed to a PricingResult (e.g. "vehicles 101-200
 * at R$2,00 each"). Purely informational/for-display; not persisted.
 */
public class PricingComponent {

    private final int fromVehicleCount;
    private final Integer toVehicleCount;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal subtotal;

    public PricingComponent(int fromVehicleCount, Integer toVehicleCount, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
        this.fromVehicleCount = fromVehicleCount;
        this.toVehicleCount = toVehicleCount;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public int getFromVehicleCount() {
        return fromVehicleCount;
    }

    public Integer getToVehicleCount() {
        return toVehicleCount;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
