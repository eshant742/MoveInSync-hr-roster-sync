package com.moveinsync.roster.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary response for a batch event processing request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEventResponseDto {

    private int totalReceived;
    private int successCount;
    private int failedCount;
    private int duplicateCount;
    private int staleCount;
    private List<EventResultDto> results;
}

