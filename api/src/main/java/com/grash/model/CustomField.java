package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.CustomFieldEntity;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * A field we didn't anticipate, attached to whatever needs it.
 * <p>
 * This used to be hard-bound to {@code Vendor}, which made it useless as an
 * escape hatch. It is now polymorphic, and it is load-bearing: the failure mode
 * that kills a one-person service business is fork proliferation, so every
 * customisation request has to land in a pack, a custom field or a setting
 * before anyone considers a code branch.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_custom_field_entity", columnList = "entity_type, entity_id")
})
public class CustomField extends CompanyAudit {

    @NotNull
    private String name;

    @NotNull
    @Column(length = 4000)
    private String value;

    @Enumerated(EnumType.STRING)
    @NotNull
    private CustomFieldEntity entityType = CustomFieldEntity.VENDOR;

    @NotNull
    private Long entityId;

    /** Optional grouping for display, e.g. "Compliance", "Contract". */
    private String fieldGroup;

    private Integer displayOrder;

    public CustomField(String name, String value, CustomFieldEntity entityType, Long entityId) {
        this.name = name;
        this.value = value;
        this.entityType = entityType;
        this.entityId = entityId;
    }
}
