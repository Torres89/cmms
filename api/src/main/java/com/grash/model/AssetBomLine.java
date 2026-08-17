package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * A part that belongs on this machine, and how many.
 * <p>
 * Without this, "what filters does this machine take and how many" is something
 * a model has to guess at. With it, it is a lookup — and a one-click restock
 * kit becomes possible.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_asset_bom_line_asset", columnList = "asset_id"),
        @Index(name = "idx_asset_bom_line_part", columnList = "part_id")
})
public class AssetBomLine extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Part part;

    /** Which position on the machine, e.g. "COOL", "LUBE", "SPN-CART". */
    private String positionCode;

    private Double qtyPerAssembly = 1.0;

    /** Consumables drive restock kits and the "order what is due" button. */
    private boolean consumable;

    private Double replaceIntervalHours;

    private Integer replaceIntervalMonths;

    @ManyToOne(fetch = FetchType.LAZY)
    private Document sourceDocument;

    private Integer sourcePage;

    private String notes;
}
