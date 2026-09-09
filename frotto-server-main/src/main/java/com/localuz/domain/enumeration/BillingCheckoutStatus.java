package com.localuz.domain.enumeration;
public enum BillingCheckoutStatus {
    CREATED, PROVIDER_PENDING, PROVIDER_UNKNOWN, AUTHORIZED, FAILED, EXPIRED, CANCELED;

    /**
     * Single source of truth for "this checkout still represents an attempt the user can resume".
     * Callers must still confirm an init_point is present: an in-progress status alone (e.g. a
     * CREATED checkout that failed before the provider ever returned one) is not resumable.
     */
    public boolean isResumable() {
        return this == CREATED || this == PROVIDER_PENDING || this == PROVIDER_UNKNOWN;
    }
}
