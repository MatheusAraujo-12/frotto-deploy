package com.localuz.service;

import com.localuz.domain.Car;
import com.localuz.domain.enumeration.CommissionType;
import java.math.BigDecimal;

public final class CommissionCalculator {

    public static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    public static final BigDecimal ZERO = new BigDecimal(0);

    private CommissionCalculator() {}

    public static BigDecimal calcCommission(BigDecimal profit, Car car) {
        if (profit == null || profit.compareTo(ZERO) <= 0 || car == null) {
            return ZERO;
        }

        CommissionType commissionType = resolveCommissionType(car);
        if (commissionType == CommissionType.FIXED) {
            if (car.getCommissionFixed() == null || car.getCommissionFixed().compareTo(ZERO) < 0) {
                return ZERO;
            }
            return car.getCommissionFixed();
        }

        Float commissionPercent = car.getCommissionPercent();
        if (commissionPercent == null || commissionPercent <= 0) {
            return ZERO;
        }

        return profit.multiply(BigDecimal.valueOf(commissionPercent)).divide(ONE_HUNDRED);
    }

    public static CommissionType resolveCommissionType(Car car) {
        if (car == null) {
            return CommissionType.PERCENT_PROFIT;
        }
        if (car.getCommissionType() == null) {
            if (car.getCommissionFixed() != null && car.getCommissionPercent() == null) {
                return CommissionType.FIXED;
            }
            return CommissionType.PERCENT_PROFIT;
        }
        return car.getCommissionType();
    }
}
