package com.localuz.domain.enumeration;

/**
 * What putting a driver who already has an open contract in another car means. Always chosen by the caller, never
 * inferred.
 */
public enum DriverAssignmentType {
    /** Temporary reserve car: the primary contract is suspended and restored when the reserve is returned. */
    RESERVE,
    /** Definitive transfer: every open contract of the driver is concluded; the new one is the primary. */
    PERMANENT,
}
