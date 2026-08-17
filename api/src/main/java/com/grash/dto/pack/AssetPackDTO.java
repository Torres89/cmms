package com.grash.dto.pack;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A vertical pack: everything known about a class of machine, as data.
 * <p>
 * This is the primary mechanism for per-customer customisation. The failure
 * mode that kills a one-person service business is fork proliferation — eight
 * customers with eight customised branches means all your time goes to merge
 * reconciliation instead of selling. So a customer who wants different PM
 * templates gets a pack file, not a code branch, and "can this be a pack
 * instead?" is a required question on every customisation request.
 */
@Data
@NoArgsConstructor
public class AssetPackDTO {

    /** e.g. "CNC_MACHINING_CENTER_VMC" — matches Asset.equipmentClass. */
    private String key;

    private String version;

    /** Localised display names, e.g. {"en": "...", "es": "..."}. */
    private Map<String, String> label = new LinkedHashMap<>();

    private String description;

    /** The equipment breakdown structure, as a tree of positions. */
    private List<EbsNode> ebs = new ArrayList<>();

    private List<SpecKey> specKeys = new ArrayList<>();

    private List<MeterTemplate> meters = new ArrayList<>();

    private List<PmTemplate> pmTemplates = new ArrayList<>();

    private List<FailureModeTemplate> failureModes = new ArrayList<>();

    private List<Consumable> consumables = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class EbsNode {
        private String positionCode;
        /** SUBUNIT, COMPONENT or PART. */
        private String level;
        private String name;
        private String nameEs;
        private String functionalDescription;
        private boolean critical;
        /** NON_TRACKED, SERIALIZED or LIFE_LIMITED. */
        private String trackingClass;
        private Double hourLimit;
        private Double cycleLimit;
        private Integer calendarLimitMonths;
        private Integer criticality;
        private List<EbsNode> children = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class SpecKey {
        private String group;
        private String key;
        private String labelEn;
        private String labelEs;
        private String unit;
        /** TEXT, NUM, BOOL or DATE. */
        private String type;
        private boolean required;
        private Integer order;
    }

    @Data
    @NoArgsConstructor
    public static class MeterTemplate {
        private String name;
        private String nameEs;
        private String unit;
        private Integer updateFrequency;
        private String sourceType;
        /** Adapter-specific identifier, e.g. an MTConnect DataItem id. */
        private String dataItem;
        /**
         * The meter this machine's component life is measured against. One per
         * counter kind — spindle hours rather than power-on hours, cycle count
         * rather than tool changes.
         */
        private Boolean usageBasis;
    }

    @Data
    @NoArgsConstructor
    public static class PmTemplate {
        private String title;
        private String titleEs;
        private String description;
        private String triggerMode;
        private String priority;
        private List<IntervalTemplate> intervals = new ArrayList<>();
        private List<String> tasks = new ArrayList<>();
        private List<String> tasksEs = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class IntervalTemplate {
        /** CALENDAR, METER or EVENT. */
        private String basis;
        /** Meter name, for METER intervals — resolved against the instantiated meters. */
        private String meter;
        private Double value;
        private String unit;
        private Double warnAtPercent;
    }

    @Data
    @NoArgsConstructor
    public static class FailureModeTemplate {
        private String code;
        private String subunit;
        private String nameEn;
        private String nameEs;
        private String description;
        private String typicalMechanism;
        private String typicalCauses;
        private String detectionMethods;
        private Integer severityDefault;
    }

    @Data
    @NoArgsConstructor
    public static class Consumable {
        private String name;
        private String nameEs;
        private String positionCode;
        private String unit;
        private Double qtyPerAssembly;
        private Double replaceIntervalHours;
        private Integer replaceIntervalMonths;
    }
}
