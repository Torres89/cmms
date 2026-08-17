package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of {@code diagnose}: what is probably wrong, why we think so, what
 * to check in what order, and what parts it will probably take.
 * <p>
 * Note {@code safetySteps} is a separate field rather than prose mixed into the
 * checks. This system gets read by people who then put their hands inside a
 * machine, and when a model we don't control is doing the talking, a safety
 * step that lives in its own field can't be paraphrased away.
 */
@Data
@NoArgsConstructor
public class DiagnosisDTO {

    private Long assetId;

    private String assetName;

    private String symptom;

    /** The machine's current state, so the caller doesn't need a second round-trip. */
    private String dossier;

    private List<Candidate> candidates = new ArrayList<>();

    private List<KnowledgeSearchResultDTO> generalReferences = new ArrayList<>();

    private List<String> safetySteps = new ArrayList<>();

    /**
     * Said plainly when there is nothing indexed to reason from, so the answer
     * is "we don't have that documented" rather than a confident invention.
     */
    private String coverageNote;

    @Data
    @NoArgsConstructor
    public static class Candidate {
        private String code;
        private String name;
        private String subunit;
        private String typicalMechanism;
        private String typicalCauses;
        private String detectionMethods;
        private Integer severity;

        /** How many times this exact failure has happened on this machine. */
        private long timesSeenOnThisAsset;

        /** When it last happened here, if it has. */
        private java.util.Date lastSeenOnThisAsset;

        private String previousCorrectiveAction;

        /** Ranking rationale, stated rather than implied. */
        private String why;

        private List<KnowledgeSearchResultDTO> references = new ArrayList<>();

        private List<SuggestedPart> likelyParts = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class SuggestedPart {
        private Long partId;
        private String name;
        private String mpn;
        private String positionCode;
        private Double qtyPerAssembly;
        private double onHand;
        private Boolean inStock;
    }
}
