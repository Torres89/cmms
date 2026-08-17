package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.SpecSource;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * One typed fact about a machine.
 * <p>
 * Machines vary far too much for fixed columns and free text is unusable, so
 * this is a typed EAV row: {@code valueText} + {@code valueNum} + {@code unit},
 * against a curated key catalogue per equipment class.
 * <p>
 * Every value carries its provenance. A spec a vision model read off a
 * nameplate is not the same fact as one a technician typed, and
 * {@code source} / {@code confidence} / {@code verifiedBy} are what let the UI
 * show "from Maintenance Manual p. 12 — verify" instead of quietly presenting
 * a guess as fact.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_asset_spec_asset", columnList = "asset_id"),
        @Index(name = "idx_asset_spec_key", columnList = "spec_key")
})
public class AssetSpec extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Asset asset;

    /** "Spindle", "Travels", "Coolant", "Lubrication". */
    @NotNull
    private String specGroup;

    /** "max_spindle_rpm", "way_lube_spec". */
    @NotNull
    private String specKey;

    private String label;

    /** e.g. "Mobil Vactra No. 2" */
    @Column(length = 2000)
    private String valueText;

    /** e.g. 12000 */
    private Double valueNum;

    /** "rpm", "L", "bar", "mm" */
    private String unit;

    @Enumerated(EnumType.STRING)
    private SpecSource source = SpecSource.MANUAL_ENTRY;

    @ManyToOne(fetch = FetchType.LAZY)
    private Document sourceDocument;

    private Integer sourcePage;

    /** 0..1 for extracted values; null when a human typed it. */
    private Double confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    private OwnUser verifiedBy;

    private Date verifiedAt;

    @Transient
    public boolean isVerified() {
        return verifiedBy != null;
    }

    /**
     * Anything a machine produced is a proposal until someone confirms it.
     */
    @Transient
    public boolean isNeedsVerification() {
        return verifiedBy == null && source != SpecSource.MANUAL_ENTRY;
    }
}
