package com.moveinsync.roster.domain.enums;

/**
 * Roles available in the HR Roster Sync application.
 *
 * <ul>
 *   <li>{@link #EMPLOYEE} — Standard employee with read-only access to their own data.</li>
 *   <li>{@link #ADMIN} — Administrator with full access to manage employee data and reconciliation.</li>
 *   <li>{@link #OPERATOR} — Operations user who can manage transport-related fields and approve safety brake overrides.</li>
 * </ul>
 */
public enum UserRole {
    EMPLOYEE,
    ADMIN,
    OPERATOR
}
