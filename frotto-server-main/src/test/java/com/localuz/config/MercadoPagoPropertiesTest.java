package com.localuz.config;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class MercadoPagoPropertiesTest {
 @Test void defaultsToDisabledWithoutCredential(){MercadoPagoProperties p=new MercadoPagoProperties();assertThat(p.isEnabled()).isFalse();assertThat(p.hasAccessToken()).isFalse();assertThat(p.toString()).doesNotContain("access-token");}
}
