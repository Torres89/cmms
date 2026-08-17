package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.IntervalBasis;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * One counter a preventive maintenance is measured against.
 * <p>
 * A PM can carry several: "every 500 spindle hours or 3 months, whichever comes
 * first" is two intervals and a trigger mode. That is how manufacturer charts
 * are actually written, and modelling it any other way means the PM either
 * fires far too often on a lightly used machine or far too late on a busy one.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_maintenance_interval_pm", columnList = "preventive_maintenance_id")
})
public class MaintenanceInterval extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PreventiveMaintenance preventiveMaintenance;

    @Enumerated(EnumType.STRING)
    @NotNull
    private IntervalBasis basis;

    /** Required when basis is METER. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Meter meter;

    @NotNull
    private Double intervalValue;

    /** "h", "cycles", "days", "months". */
    private String unit;

    /** Raise the "due soon" flag at this percentage of the interval. */
    private Double warnAtPercent = 90.0;

    /** Meter reading at the last completion, for METER intervals. */
    private Double lastCompletedValue;

    /** Timestamp of the last completion, for CALENDAR intervals. */
    private Date lastCompletedAt;

    private String description;
}
