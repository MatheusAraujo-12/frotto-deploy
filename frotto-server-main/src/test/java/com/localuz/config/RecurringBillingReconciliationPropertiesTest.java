package com.localuz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 5G.8: dedicated coverage for the config-matrix bounds added in 5G.7 - none existed before. */
class RecurringBillingReconciliationPropertiesTest {
    @Test void disabledAndSafeByDefault() {
        var config = new RecurringBillingReconciliationProperties();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getCancelledTerminalHorizonDays()).isEqualTo(90);
        assertThat(config.getRateLimitDefaultCooldownSeconds()).isEqualTo(900);
        assertThat(config.getRateLimitMaxCooldownSeconds()).isEqualTo(3600);
        config.validate(); // defaults must never fail validation
    }

    @Test void zeroOrNegativeCancelledTerminalHorizonDaysFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setCancelledTerminalHorizonDays(0);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
        config.setCancelledTerminalHorizonDays(-1);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void tooLargeCancelledTerminalHorizonDaysFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setCancelledTerminalHorizonDays(3651);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
        config.setCancelledTerminalHorizonDays(3650);
        config.validate(); // boundary is inclusive
    }

    @Test void zeroOrNegativeRateLimitMaxCooldownFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setRateLimitMaxCooldownSeconds(0);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rateLimitMaxCooldownAboveOneHourFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setRateLimitMaxCooldownSeconds(3601);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
        config.setRateLimitMaxCooldownSeconds(3600);
        config.validate(); // 1h ceiling is inclusive
    }

    @Test void rateLimitDefaultCooldownAboveItsOwnMaxFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setRateLimitMaxCooldownSeconds(300);
        config.setRateLimitDefaultCooldownSeconds(301);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
        config.setRateLimitDefaultCooldownSeconds(300);
        config.validate(); // default == max is allowed
    }

    @Test void zeroOrNegativeRateLimitDefaultCooldownFailsValidation() {
        var config = new RecurringBillingReconciliationProperties();
        config.setRateLimitDefaultCooldownSeconds(0);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void existingBoundedWorkLimitsStillFailClosedOnInvalidConfiguration() {
        var config = new RecurringBillingReconciliationProperties();
        config.setBatchSize(0);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
        config.setBatchSize(10);
        config.setMaxHttpCalls(0);
        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
