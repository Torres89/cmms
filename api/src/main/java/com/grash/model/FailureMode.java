package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

/**
 * A way a class of equipment fails, in the ISO 14224 sense.
 * <p>
 * ISO 14224 insists on a distinction most CMMS deployments blur: the failure
 * <em>mode</em> is the observed effect, the <em>mechanism</em> is the physical
 * process, and the <em>cause</em> is the root condition. Keeping them apart is
 * what makes MTBF per failure mode and a Pareto of real causes possible,
 * instead of a pile of free text.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_failure_mode_class", columnList = "equipment_class"),
        @Index(name = "idx_failure_mode_code", columnList = "code")
})
public class FailureMode extends CompanyAudit {

    @NotNull
    private String equipmentClass;

    /** Which subunit it belongs to, e.g. "Spindle", "Lubrication". */
    private String subunit;

    /** e.g. "SPN-BRG-SEIZE" */
    @NotNull
    private String code;

    @NotNull
    private String nameEn;

    private String nameEs;

    @Column(length = 2000)
    private String description;

    /** The physical process: "fatigue spalling / lubrication starvation". */
    @Column(length = 2000)
    private String typicalMechanism;

    /** Root conditions: "lube failure; coolant ingress past seal; crash overload". */
    @Column(length = 2000)
    private String typicalCauses;

    /** How you would catch it early: "vibration RMS; temperature rise; audible". */
    @Column(length = 2000)
    private String detectionMethods;

    /** 1..5 */
    private Integer severityDefault;

    /** Seeded from a vertical pack rather than authored by the customer. */
    private boolean systemSeeded;
}
