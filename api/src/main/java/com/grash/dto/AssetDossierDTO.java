package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Everything true about one machine right now.
 * <p>
 * Retrieval answers "what does the manual say". It is bad at "what is true
 * about this machine right now", which is what this is for: generated from the
 * database, never retrieved, a few hundred tokens, always accurate. It is what
 * an MCP client reads, what the mobile chat injects, and what makes a general
 * model behave like a specialist.
 */
@Data
@NoArgsConstructor
public class AssetDossierDTO {

    private Long id;
    private String name;
    private String customId;
    private String model;
    private String manufacturer;
    private String serialNumber;
    private String equipmentClass;
    private String level;
    private String status;
    private String locationPath;
    private String area;
    private Date inServiceDate;
    private Date warrantyExpirationDate;
    private Integer criticality;
    private Double downtimeCostPerHour;
    private Double replacementCost;
    private String description;
    private String functionalDescription;
    private String imageUrl;

    private List<MeterReadingSummary> meters = new ArrayList<>();
    private List<SpecSummary> keySpecs = new ArrayList<>();
    private SpecCompletenessDTO specCompleteness;
    private List<ComponentSummary> components = new ArrayList<>();
    private List<StructureNode> structure = new ArrayList<>();
    private List<PmSummary> upcomingMaintenance = new ArrayList<>();
    private List<WorkOrderSummary> openWorkOrders = new ArrayList<>();
    private List<FailureSummary> recentFailures = new ArrayList<>();
    private List<DocumentSummary> documents = new ArrayList<>();

    /** Rendered card for AI clients — the same facts as a compact text block. */
    private String text;

    @Data
    @NoArgsConstructor
    public static class MeterReadingSummary {
        private Long meterId;
        private String name;
        private String unit;
        private Double lastValue;
        private Date lastReadingAt;
        private int updateFrequency;
        private boolean overdue;
    }

    @Data
    @NoArgsConstructor
    public static class SpecSummary {
        private Long id;
        private String specGroup;
        private String specKey;
        private String label;
        private String value;
        private String unit;
        private String source;
        private boolean verified;
        private String sourceDocumentTitle;
        private Integer sourcePage;
        private Double confidence;
    }

    @Data
    @NoArgsConstructor
    public static class ComponentSummary {
        private Long id;
        private String serialNumber;
        private String name;
        /**
         * The position asset this component currently occupies.
         * <p>
         * The structure tree needs this to place a component. Matching on
         * {@code positionName} instead makes two positions called "Bearing"
         * indistinguishable, and silently moves a component when a position is
         * renamed.
         */
        private Long positionId;
        private String positionCode;
        private String positionName;
        private String status;
        private Double totalHours;
        private Double hoursSinceOverhaul;
        private Double hourLimit;
        private Double totalCycles;
        private Double cycleLimit;
        private Integer calendarLimitMonths;
        /** 0..1, or null when the component is not life-limited. */
        private Double remainingLifeFraction;
        private Date installedAt;
    }

    @Data
    @NoArgsConstructor
    public static class StructureNode {
        private Long id;
        private String name;
        private String positionCode;
        private String level;
        private String trackingClass;
        private Integer criticality;
        private List<StructureNode> children = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class PmSummary {
        private Long id;
        private String title;
        private String triggerMode;
        private Double percent;
        private boolean due;
        private boolean warning;
        private String drivingCounter;
        private Double remaining;
        private String remainingUnit;
        private Date nextDueDate;
    }

    @Data
    @NoArgsConstructor
    public static class WorkOrderSummary {
        private Long id;
        private String title;
        private String status;
        private String priority;
        private Date dueDate;
        private String assignedTo;
    }

    @Data
    @NoArgsConstructor
    public static class FailureSummary {
        private Long id;
        private String code;
        private String name;
        private Date occurredAt;
        private Integer downtimeMinutes;
        private Integer severity;
        private String cause;
        private String correctiveAction;
    }

    @Data
    @NoArgsConstructor
    public static class DocumentSummary {
        private Long id;
        private String title;
        private String docType;
        private String revision;
        private Integer pageCount;
        private String ingestStatus;
        private Integer chunkCount;
    }
}
