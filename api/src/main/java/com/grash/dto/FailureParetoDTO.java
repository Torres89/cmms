package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the "what actually costs this machine its uptime" table.
 */
@Data
@NoArgsConstructor
public class FailureParetoDTO {

    private String code;

    private String name;

    private long count;

    private long downtimeMinutes;

    private double repairCost;

    /** Null when there have been fewer than two occurrences to measure between. */
    private Double mtbfDays;

    private Double mttrMinutes;
}
