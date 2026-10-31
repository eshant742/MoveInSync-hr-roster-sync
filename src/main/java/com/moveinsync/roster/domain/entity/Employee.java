package com.moveinsync.roster.domain.entity;

import com.moveinsync.roster.dto.EmployeeEventDto;
import com.moveinsync.roster.dto.TransportFieldsUpdateDto;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA entity representing an employee in the HR Roster Sync system.
 *
 * <p>Maps to the {@code employees} table. Each employee is uniquely identified
 * by the combination of {@link #employeeId} and {@link #tenantId}.</p>
 *
 * <p>Fields are divided into two ownership groups:</p>
 * <ul>
 *   <li><strong>HR-owned</strong>: {@code firstName}, {@code lastName}, {@code email},
 *       {@code phone}, {@code managerId}, {@code officeCode}, {@code department},
 *       {@code shiftCode} — updated only via HR events and reconciliation.</li>
 *   <li><strong>Transport-admin-owned</strong>: {@code pickupPoint}, {@code specialAssistanceNeeds}
 *       — managed by the transport administration team and never overwritten by HR data.</li>
 * </ul>
 *
 * <p>Uses JPA {@link Version} for optimistic locking, which is independent of the
 * HR-provided event version number ({@link #hrVersion}).</p>
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_tenant",
                columnNames = {"employee_id", "tenant_id"}
        ),
        indexes = {
                @Index(name = "idx_employee_tenant", columnList = "employee_id, tenant_id"),
                @Index(name = "idx_tenant", columnList = "tenant_id"),
                @Index(name = "idx_office_code", columnList = "office_code"),
                @Index(name = "idx_active", columnList = "active")
        }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 64)
    private String employeeId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Column(name = "email", nullable = false, length = 256)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "manager_id", length = 64)
    private String managerId;

    @Column(name = "office_code", length = 32)
    private String officeCode;

    @Column(name = "department", length = 128)
    private String department;

    @Column(name = "shift_code", length = 32)
    private String shiftCode;

    /** Transport-admin-owned field: designated pickup point for the employee. */
    @Column(name = "pickup_point", length = 256)
    private String pickupPoint;

    /** Transport-admin-owned field: special assistance requirements. */
    @Column(name = "special_assistance_needs", length = 512)
    private String specialAssistanceNeeds;

    /**
     * HR event version — the latest version number received from the HR system.
     * Used to detect stale and duplicate events (not related to JPA optimistic locking).
     */
    @Column(name = "version", nullable = false)
    private long hrVersion;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA optimistic locking version — independent of the HR event version.
     */
    @Version
    @Column(name = "jpa_version")
    private Long jpaVersion;

    /** Default constructor required by JPA. */
    public Employee() {
    }

    /**
     * Determines whether the incoming HR event version is newer than the currently
     * stored version.
     *
     * @param incomingVersion the version number from the incoming HR event
     * @return {@code true} if the incoming version is strictly greater than the current version
     */
    public boolean isNewerThan(long incomingVersion) {
        return incomingVersion > this.hrVersion;
    }

    /**
     * Applies HR-owned fields from the given employee event.
     *
     * <p>This method updates <strong>only</strong> HR-owned fields and the HR version.
     * Transport-admin-owned fields ({@code pickupPoint}, {@code specialAssistanceNeeds})
     * are never modified by this method.</p>
     *
     * @param event the incoming employee event containing HR field values
     */
    public void applyHrFields(EmployeeEventDto event) {
        if (event.getFirstName() != null) {
            this.firstName = event.getFirstName();
        }
        if (event.getLastName() != null) {
            this.lastName = event.getLastName();
        }
        if (event.getEmail() != null) {
            this.email = event.getEmail();
        }
        if (event.getPhone() != null) {
            this.phone = event.getPhone();
        }
        if (event.getManagerId() != null) {
            this.managerId = event.getManagerId();
        }
        if (event.getOfficeCode() != null) {
            this.officeCode = event.getOfficeCode();
        }
        if (event.getDepartment() != null) {
            this.department = event.getDepartment();
        }
        if (event.getShiftCode() != null) {
            this.shiftCode = event.getShiftCode();
        }
        this.hrVersion = event.getVersion();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Applies transport-admin-owned fields from the given update.
     *
     * <p>This method updates <strong>only</strong> transport-admin-owned fields.
     * HR-owned fields are never modified by this method.</p>
     *
     * @param update the transport fields update containing pickup point and/or special assistance data
     */
    public void applyTransportFields(TransportFieldsUpdateDto update) {
        if (update.getPickupPoint() != null) {
            this.pickupPoint = update.getPickupPoint();
        }
        if (update.getSpecialAssistanceNeeds() != null) {
            this.specialAssistanceNeeds = update.getSpecialAssistanceNeeds();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Equality is based on the natural key: {@code employeeId} + {@code tenantId}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee employee = (Employee) o;
        return Objects.equals(employeeId, employee.employeeId)
                && Objects.equals(tenantId, employee.tenantId);
    }

    /**
     * Hash code is based on the natural key: {@code employeeId} + {@code tenantId}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(employeeId, tenantId);
    }

    @Override
    public String toString() {
        // Intentionally excludes PII fields (name, email, phone)
        return "Employee{employeeId='" + employeeId + "', tenantId='" + tenantId
                + "', hrVersion=" + hrVersion + ", active=" + active + '}';
    }

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getOfficeCode() {
        return officeCode;
    }

    public void setOfficeCode(String officeCode) {
        this.officeCode = officeCode;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getShiftCode() {
        return shiftCode;
    }

    public void setShiftCode(String shiftCode) {
        this.shiftCode = shiftCode;
    }

    public String getPickupPoint() {
        return pickupPoint;
    }

    public void setPickupPoint(String pickupPoint) {
        this.pickupPoint = pickupPoint;
    }

    public String getSpecialAssistanceNeeds() {
        return specialAssistanceNeeds;
    }

    public void setSpecialAssistanceNeeds(String specialAssistanceNeeds) {
        this.specialAssistanceNeeds = specialAssistanceNeeds;
    }

    public long getHrVersion() {
        return hrVersion;
    }

    public void setHrVersion(long hrVersion) {
        this.hrVersion = hrVersion;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getJpaVersion() {
        return jpaVersion;
    }

    public void setJpaVersion(Long jpaVersion) {
        this.jpaVersion = jpaVersion;
    }
}
