package com.grash.model;

import com.grash.model.abstracts.DateAudit;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * What a fault code actually means.
 * <p>
 * Telematics payloads and CNC controls generally report the code and not its
 * meaning — Caterpillar expects lookup through service tooling or the dealer,
 * and a Fanuc control gives you "SV0410" and nothing else. So this table is
 * seeded from public data (J1939, published alarm lists) and enriched from the
 * customer's own manuals during ingestion.
 * <p>
 * {@code companyId} is nullable on purpose: a null row is shared reference data
 * that every tenant can read, and a company-scoped row is that customer's own
 * enrichment, which wins on lookup. This is the one place a shared corpus is
 * appropriate, because it is public manufacturer data rather than anyone's
 * documents.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_fault_code_lookup", columnList = "code, equipment_class")
})
public class FaultCodeDictionary extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null means shared reference data, readable by every tenant. */
    private Long companyId;

    private String equipmentClass;

    private String manufacturer;

    /** "SV0410", "121", "SPN 100 FMI 1". */
    @NotNull
    private String code;

    @Column(columnDefinition = "text")
    private String descriptionEn;

    @Column(columnDefinition = "text")
    private String descriptionEs;

    private String severity;

    @Column(columnDefinition = "text")
    private String likelyCauses;

    @Column(columnDefinition = "text")
    private String recommendedAction;

    /** SEEDED, DOC_EXTRACTION, MANUAL_ENTRY. */
    private String source;

    private Long documentId;

    private Integer page;
}
