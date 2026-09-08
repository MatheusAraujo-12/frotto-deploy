package com.localuz.config;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class MercadoPagoPropertiesTest {
 @Test void defaultsToDisabledWithoutCredential(){MercadoPagoProperties p=new MercadoPagoProperties();assertThat(p.isEnabled()).isFalse();assertThat(p.hasAccessToken()).isFalse();assertThat(p.toString()).doesNotContain("access-token");}
 @Test void defaultsToTestModeDisabledWithoutTestPayerEmail(){MercadoPagoProperties p=new MercadoPagoProperties();assertThat(p.isTestMode()).isFalse();assertThat(p.hasTestPayerEmail()).isFalse();assertThat(p.getTestPayerEmail()).isNull();}
 @Test void hasTestPayerEmailIsFalseForBlankValue(){MercadoPagoProperties p=new MercadoPagoProperties();p.setTestPayerEmail("   ");assertThat(p.hasTestPayerEmail()).isFalse();}
 @Test void hasTestPayerEmailIsTrueWhenConfigured(){MercadoPagoProperties p=new MercadoPagoProperties();p.setTestMode(true);p.setTestPayerEmail("buyer-test@testuser.com");assertThat(p.isTestMode()).isTrue();assertThat(p.hasTestPayerEmail()).isTrue();assertThat(p.getTestPayerEmail()).isEqualTo("buyer-test@testuser.com");}
}
