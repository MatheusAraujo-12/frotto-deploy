package com.localuz.domain.enumeration;

/**
 * What kind of debt a pendency structurally is, when the system knows it for sure. Rows recorded before the
 * origin existed stay without it (null): nothing is classified from names or descriptions.
 */
public enum PendencyOriginType {
    /** A traffic fine charged to the driver: the pendency is the fine (its infraction data lives in it). */
    FINE,
    /** The driver's share of a car maintenance: the maintenance keeps its full cost, only the share is owed. */
    SHARED_MAINTENANCE,
}
