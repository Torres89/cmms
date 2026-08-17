package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.CrossRefType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * "This part also works", or "this part replaced that one".
 * <p>
 * The thing you badly want at 2am when the OEM part is six weeks out.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_part_cross_reference_part", columnList = "part_id")
})
public class PartCrossReference extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Part alternate;

    @Enumerated(EnumType.STRING)
    private CrossRefType type = CrossRefType.EQUIVALENT;

    @Column(length = 2000)
    private String justification;
}
