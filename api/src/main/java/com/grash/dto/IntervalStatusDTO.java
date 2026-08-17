package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a preventive maintenance stands against all of its counters.
 */
@Data
@NoArgsConstructor
public class IntervalStatusDTO {

    private Long preventiveMaintenanceId;

    private String title;

    private String triggerMode;

    /** 0..100+ across all counters, resolved by the trigger mode. */
    private Double percent;

    private boolean due;

    /** Past the warn threshold but not yet due. */
    private boolean warning;

    /** Which counter is closest to firing — the one worth showing. */
    private String drivingCounter;

    private Double remaining;

    private String remainingUnit;

    private List<CounterStatus> counters = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class CounterStatus {
        private Long intervalId;
        private String basis;
        private String label;
        private Double intervalValue;
        private String unit;
        private Double elapsed;
        private Double percent;
        private Double remaining;
        private Double warnAtPercent;
    }
}
