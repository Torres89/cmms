package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Proposed preventive maintenance for a human to approve. Nothing here has been
 * created.
 */
@Data
@NoArgsConstructor
public class MaintenancePlanProposalDTO {

    private Long assetId;

    private String assetName;

    private int existingPlanCount;

    private Usage usage = new Usage();

    private List<ProposedPlan> plans = new ArrayList<>();

    /** Said plainly when there is nothing to propose from. */
    private String note;

    @Data
    @NoArgsConstructor
    public static class ProposedPlan {
        private String title;
        /** CALENDAR or METER. */
        private String basis;
        private Double intervalValue;
        private String unit;
        private String triggerMode;
        /** e.g. "about 9 weeks at the current rate". */
        private String estimatedFirstDue;
        private Set<String> tasks = new LinkedHashSet<>();
        private Set<String> sources = new LinkedHashSet<>();
        private List<KnowledgeSearchResultDTO> references = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Usage {
        private List<MeterUsage> meters = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class MeterUsage {
        private Long meterId;
        private String name;
        private String unit;
        private Double currentValue;
        private double perDay;
        private double perWeek;
    }
}
