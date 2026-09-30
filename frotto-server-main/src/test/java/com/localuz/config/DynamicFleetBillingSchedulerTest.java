package com.localuz.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.localuz.domain.Subscription;
import com.localuz.repository.SubscriptionRepository;
import com.localuz.service.DynamicFleetBillingService;
import com.localuz.service.MercadoPagoException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DynamicFleetBillingSchedulerTest {
    @Test void boundedRunsContinueFromLastIdWithoutStarvingLaterAccounts() {
        var repository = mock(SubscriptionRepository.class);
        var service = mock(DynamicFleetBillingService.class);
        var provider = mock(MercadoPagoProperties.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.hasAccessToken()).thenReturn(true);
        var scheduler = new DynamicFleetBillingScheduler(repository, service, provider);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "maxPerRun", 1);
        Subscription first = new Subscription(); first.setId(7L);
        Subscription second = new Subscription(); second.setId(9L);
        when(repository.findFleetRenewalCandidates(eq(0L), any())).thenReturn(List.of(first));
        when(repository.findFleetRenewalCandidates(eq(7L), any())).thenReturn(List.of(second));
        scheduler.closeRenewals();
        verify(service).reconcile(eq(first), any());
        verify(service, never()).reconcile(eq(second), any());
        scheduler.closeRenewals();
        verify(service).reconcile(eq(second), any());
    }

    @Test void rateLimitStopsBatchAndRetriesFailedAccountNextRun() {
        var repository = mock(SubscriptionRepository.class);
        var service = mock(DynamicFleetBillingService.class);
        var provider = mock(MercadoPagoProperties.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.hasAccessToken()).thenReturn(true);
        var scheduler = new DynamicFleetBillingScheduler(repository, service, provider);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        Subscription first = new Subscription(); first.setId(7L);
        Subscription second = new Subscription(); second.setId(9L);
        when(repository.findFleetRenewalCandidates(eq(0L), any())).thenReturn(List.of(first, second));
        doThrow(new MercadoPagoException("rate limited", false, 429, null, null)).when(service).reconcile(eq(first), any());
        scheduler.closeRenewals(); scheduler.closeRenewals();
        verify(service, times(2)).reconcile(eq(first), any());
        verify(service, never()).reconcile(eq(second), any());
    }
}
