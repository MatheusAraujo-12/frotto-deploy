package com.localuz.service.dto;

import java.time.Instant;

/** Safe, derived cancellation state; never persisted as a separate source of truth. */
public enum SubscriptionCancellationState {
    NONE,
    PENDING_CONFIRMATION,
    CONFIRMED;

    public static SubscriptionCancellationState from(Boolean cancelAtPeriodEnd, Instant canceledAt) {
        if (!Boolean.TRUE.equals(cancelAtPeriodEnd)) {
            return NONE;
        }
        return canceledAt == null ? PENDING_CONFIRMATION : CONFIRMED;
    }
}
