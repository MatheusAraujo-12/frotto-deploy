package com.localuz.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.localuz.repository.*;
import com.localuz.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class RecurringBillingReconciliationBootstrapTest {
    @Test void springResolvesRealConstructorsAndDisabledSchedulerDoesNoIo() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var subscriptions = mock(SubscriptionRepository.class);
            var client = mock(MercadoPagoClient.class);
            context.registerBean(SubscriptionRepository.class, () -> subscriptions);
            context.registerBean(MercadoPagoClient.class, () -> client);
            context.registerBean(BillingInvoiceRepository.class, () -> mock(BillingInvoiceRepository.class));
            context.registerBean(PaymentAttemptRepository.class, () -> mock(PaymentAttemptRepository.class));
            context.register(MercadoPagoProperties.class, MercadoPagoBillingStatusMapper.class,
                MercadoPagoFinancialIngestion.class, RecurringBillingReconciliationProperties.class,
                RecurringBillingReservationService.class, RecurringBillingReconciliationService.class,
                RecurringBillingReconciliationScheduler.class);
            context.refresh();
            assertThat(context.getBean(RecurringBillingReconciliationProperties.class).isEnabled()).isFalse();
            context.getBean(RecurringBillingReconciliationScheduler.class).reconcileSubscriptions();
            verifyNoInteractions(subscriptions, client);
        }
    }
}
