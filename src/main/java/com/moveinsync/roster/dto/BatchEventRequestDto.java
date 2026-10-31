package com.moveinsync.roster.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload containing a batch of employee events for bulk ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEventRequestDto {

    @NotEmpty(message = "Events list cannot be empty")
    @Valid
    private List<EmployeeEventDto> events;
}

