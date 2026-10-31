package com.moveinsync.roster.service;

import com.moveinsync.roster.domain.enums.EventType;
import com.moveinsync.roster.dto.EmployeeEventDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates incoming employee events at the system boundary.
 *
 * <p>Enforces data integrity rules before events enter the core processing pipeline.
 * Per case study requirements, invalid records are rejected individually with
 * a detailed per-record error report — <strong>one bad record must never fail
 * the entire batch</strong>.</p>
 */
@Service
public class EventValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9]{10,15}$"
    );

    /**
     * Validates an incoming employee event and returns a list of error descriptions.
     * An empty return list indicates the event passed all validation checks.
     *
     * @param event the event to validate
     * @return list of error messages, empty if valid
     */
    public List<String> validate(EmployeeEventDto event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            errors.add("Event payload cannot be null");
            return errors;
        }

        // Required envelope fields
        if (event.getEmployeeId() == null || event.getEmployeeId().trim().isEmpty()) {
            errors.add("employeeId is required and cannot be blank");
        }

        if (event.getTenantId() == null || event.getTenantId().trim().isEmpty()) {
            errors.add("tenantId is required and cannot be blank");
        }

        if (event.getEventType() == null) {
            errors.add("eventType is required (CREATE, UPDATE, or DEACTIVATE)");
        }

        if (event.getVersion() == null || event.getVersion() <= 0) {
            errors.add("version must be a positive integer greater than 0");
        }

        // Field requirements by event type
        if (event.getEventType() == EventType.CREATE) {
            validateCreateFields(event, errors);
        } else if (event.getEventType() == EventType.UPDATE) {
            validateUpdateFields(event, errors);
        }

        return errors;
    }

    private void validateCreateFields(EmployeeEventDto event, List<String> errors) {
        if (event.getFirstName() == null || event.getFirstName().trim().isEmpty()) {
            errors.add("firstName is required for CREATE event");
        }
        if (event.getLastName() == null || event.getLastName().trim().isEmpty()) {
            errors.add("lastName is required for CREATE event");
        }
        if (event.getEmail() == null || !isValidEmail(event.getEmail())) {
            errors.add("A valid email address is required for CREATE event");
        }
        if (event.getOfficeCode() == null || event.getOfficeCode().trim().isEmpty()) {
            errors.add("officeCode is required for CREATE event");
        }
        if (event.getPhone() != null && !event.getPhone().trim().isEmpty() && !isValidPhone(event.getPhone())) {
            errors.add("phone must match 10-15 digit format: " + event.getPhone());
        }
    }

    private void validateUpdateFields(EmployeeEventDto event, List<String> errors) {
        if (event.getEmail() != null && !event.getEmail().trim().isEmpty() && !isValidEmail(event.getEmail())) {
            errors.add("Provided email address format is invalid");
        }
        if (event.getPhone() != null && !event.getPhone().trim().isEmpty() && !isValidPhone(event.getPhone())) {
            errors.add("Provided phone number format is invalid");
        }
    }

    public boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public boolean isValidPhone(String phone) {
        if (phone == null) return false;
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }
}

