package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.SpecValueType;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * The expected spec keys for an equipment class.
 * <p>
 * This is what turns commissioning into a visible progress bar — "27 of 34
 * specs captured" — which is exactly what you want on screen when you are
 * sitting with a customer, billing for the day.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_spec_key_catalog_class", columnList = "equipment_class")
})
public class SpecKeyCatalog extends CompanyAudit {

    @NotNull
    private String equipmentClass;

    @NotNull
    private String specGroup;

    @NotNull
    private String specKey;

    private String labelEn;

    private String labelEs;

    private String unit;

    @Enumerated(EnumType.STRING)
    private SpecValueType valueType = SpecValueType.TEXT;

    /** Counted in the completeness meter. */
    private boolean required;

    private Integer displayOrder;

    /** Seeded from a vertical pack rather than typed by the customer. */
    private boolean systemSeeded;
}
