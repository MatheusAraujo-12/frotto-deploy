package com.localuz.domain.enumeration;

/** What a structured Checklist de Entrega/Devolução does to its contract (driver_car). */
public enum ChecklistType {
    /** The car is handed to the driver: the contract is created or confirmed. */
    ENTREGA,
    /** The car comes back: the contract is concluded (a reserve is returned with the existing rules). */
    DEVOLUCAO,
}
