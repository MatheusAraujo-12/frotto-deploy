package com.localuz.config;

import static org.mockito.Mockito.*;
import com.localuz.service.*;
import com.localuz.service.RecurringBillingReservationService.Candidate;
import com.localuz.service.dto.RecurringReconciliationResult;
import com.localuz.service.dto.RecurringReconciliationResult.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringBillingReconciliationSchedulerTest {
    private final RecurringBillingReconciliationProperties config=new RecurringBillingReconciliationProperties();
    private final MercadoPagoProperties provider=new MercadoPagoProperties();
    private final RecurringBillingReservationService reservations=mock(RecurringBillingReservationService.class);
    private final RecurringBillingReconciliationService service=mock(RecurringBillingReconciliationService.class);
    private final RecurringBillingReconciliationScheduler scheduler=new RecurringBillingReconciliationScheduler(config,provider,reservations,service);
    private void enable() { config.setEnabled(true); provider.setEnabled(true); provider.setAccessToken("fake-test-only"); }

    @Test void disabledByDefaultDoesNothing() {
        scheduler.reconcileSubscriptions(); verifyNoInteractions(reservations,service);
    }
    @Test void disabledProviderDoesNothing() {
        config.setEnabled(true); scheduler.reconcileSubscriptions(); verifyNoInteractions(reservations,service);
    }
    @Test void batchSizeIsEnforcedEvenForUnexpectedLargerCandidateList() {
        enable(); config.setBatchSize(1);
        when(reservations.candidates(any(),eq(1))).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenReturn(new RecurringReconciliationResult(1L,Outcome.COMPLETE,0,0,0,0));
        scheduler.reconcileSubscriptions(); verify(service,times(1)).reconcile(any(),any());
    }
    @Test void rateLimitStopsRemainingCandidates() {
        enable();
        when(reservations.candidates(any(),anyInt())).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenAnswer(call->{
            call.<RecurringReconciliationBudget>getArgument(1).stopForRateLimit();
            return new RecurringReconciliationResult(1L,Outcome.RATE_LIMITED,0,0,0,1);
        });
        scheduler.reconcileSubscriptions(); verify(service,times(1)).reconcile(any(),any());
    }
    @Test void oneOperationalFailureDoesNotAbortOthers() {
        enable();
        when(reservations.candidates(any(),anyInt())).thenReturn(List.of(new Candidate(1L,"a"),new Candidate(2L,"b")));
        when(service.reconcile(any(),any())).thenThrow(new IllegalStateException("fake"));
        scheduler.reconcileSubscriptions(); verify(service,times(2)).reconcile(any(),any());
    }
}
