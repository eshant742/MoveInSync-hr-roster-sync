package com.moveinsync.roster.domain.enums;

/**
 * Indicates which system owns a particular field on the employee record.
 *
 * <p>HR-owned fields are updated exclusively through HR events and reconciliation.
 * Transport-admin-owned fields are managed by the transport administration team
 * and must <strong>never</strong> be overwritten by HR data.</p>
 */
public enum FieldOwner {
    /** Fields owned by the HR system (name, email, manager, office, etc.). */
    HR,

    /** Fields owned by the transport administration team (pickup point, special assistance, etc.). */
    TRANSPORT_ADMIN
}
