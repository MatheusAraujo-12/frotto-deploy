package com.localuz.service;

import com.localuz.domain.Subscription;
import com.localuz.service.dto.MercadoPagoPreapproval;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** External calls occur between committed steps; never creates a charge or a preapproval. */
@Service
public class DynamicFleetBillingService {
    private final DynamicFleetBillingSteps steps;
    private final MercadoPagoClient client;

    public DynamicFleetBillingService(DynamicFleetBillingSteps steps, MercadoPagoClient client) {
        this.steps = steps; this.client = client;
    }

    public void reconcile(Subscription candidate, Instant now) {
        if (!DynamicFleetBillingSteps.eligible(candidate)) return;
        MercadoPagoPreapproval remote;
        try {
            remote = client.getPreapproval(candidate.getExternalSubscriptionId());
        } catch (RuntimeException failure) {
            if (DynamicFleetBillingSteps.locked(candidate)) steps.unconfirmed(candidate.getId(), candidate.getNextRenewalToken(), now);
            LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal subscriptionId={} outcome=PROVIDER_UNAVAILABLE", candidate.getId());
            rethrowRateLimit(failure);
            return;
        }
        if (remote == null) return;
        Subscription s = steps.lockSnapshot(candidate.getUser().getId(), candidate.getId(), remote, now);
        if (s == null || s.getNextRenewalSyncedAt() != null) return;
        try {
            if (!sameRenewal(s, remote) || !s.getNextRenewalAt().isAfter(now)) {
                steps.unconfirmed(s.getId(), s.getNextRenewalToken(), now);
                return;
            }
            if (!matches(s, remote)) {
                try {
                    client.updatePreapprovalAmount(s.getExternalSubscriptionId(), s.getNextRenewalPrice(), "BRL", "fleet-" + s.getNextRenewalToken());
                } catch (MercadoPagoException failure) {
                    rethrowRateLimit(failure);
                    if (!failure.isAmbiguous()) throw failure;
                }
                remote = client.getPreapproval(s.getExternalSubscriptionId());
            }
            if (!matches(s, remote) || !sameRenewal(s, remote)) throw new IllegalStateException("Renewal not confirmed");
            steps.confirmed(s.getId(), s.getNextRenewalToken(), now);
        } catch (RuntimeException failure) {
            steps.unconfirmed(s.getId(), s.getNextRenewalToken(), now);
            LoggerFactory.getLogger(getClass()).warn("Dynamic fleet renewal subscriptionId={} outcome=UNCONFIRMED", s.getId());
            rethrowRateLimit(failure);
        }
    }

    private static void rethrowRateLimit(RuntimeException failure) {
        if (failure instanceof MercadoPagoException providerFailure && providerFailure.getCategory() == MercadoPagoException.Category.HTTP_429) {
            throw providerFailure;
        }
    }

    static boolean sameRenewal(Subscription s, MercadoPagoPreapproval p) {
        return p != null && Objects.equals(s.getExternalSubscriptionId(), p.getId()) && "authorized".equals(p.getStatus())
            && Objects.equals(s.getNextRenewalAt(), p.getNextPaymentDate());
    }

    static boolean matches(Subscription s, MercadoPagoPreapproval p) {
        return p != null && p.getTransactionAmount() != null && "BRL".equals(p.getCurrencyId())
            && s.getNextRenewalPrice().compareTo(p.getTransactionAmount()) == 0;
    }
}
