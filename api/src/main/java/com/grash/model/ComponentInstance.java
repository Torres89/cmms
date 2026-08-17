package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.ComponentStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * One physical, serialized component, tracked back to birth.
 * <p>
 * This is the aviation pattern transferred directly: a serialized life-limited
 * part carries a continuous record from manufacture onward, and configuration
 * management always knows which component is in which position. Exactly what a
 * spindle cartridge, ballscrew, rotary table or final drive needs.
 * <p>
 * Counters roll forward automatically from meter readings on whatever asset the
 * component is installed under, which is what makes "hours on the
 * <em>current</em> spindle" an answerable question.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_component_instance_position", columnList = "current_position_id"),
        @Index(name = "idx_component_instance_serial", columnList = "serial_number")
})
public class ComponentInstance extends CompanyAudit {

    @NotNull
    private String serialNumber;

    /** What kind of thing this is; links to stock, sourcing and cross-references. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Part partType;

    private String manufacturer;

    private String mpn;

    private Date manufactureDate;

    private Date acquiredAt;

    private Double acquisitionCost;

    @Enumerated(EnumType.STRING)
    private ComponentStatus status = ComponentStatus.IN_STOCK;

    /** The asset position it currently occupies, or null when not installed. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Asset currentPosition;

    // --- counters -------------------------------------------------------

    /** Time since new. */
    private Double totalHours = 0d;

    private Double totalCycles = 0d;

    private Double hoursSinceOverhaul = 0d;

    private Double cyclesSinceOverhaul = 0d;

    // --- limits (null = not life-limited on that axis) -------------------

    private Double hourLimit;

    private Double cycleLimit;

    private Integer calendarLimitMonths;

    @OneToOne(fetch = FetchType.LAZY)
    private File image;

    @Column(length = 2000)
    private String notes;

    private static final double MILLIS_PER_MONTH = 1000d * 60 * 60 * 24 * 30.4375;

    /**
     * Fraction of life remaining on whichever limit runs out first, or null
     * when the component is not life-limited.
     * <p>
     * Alerting mirrors the aviation convention: warn at 10 % remaining and
     * again at 5 %.
     */
    @Transient
    public Double getRemainingLifeFraction() {
        Double worst = null;
        if (hourLimit != null && hourLimit > 0) {
            worst = min(worst, 1 - elapsedHours() / hourLimit);
        }
        if (cycleLimit != null && cycleLimit > 0) {
            worst = min(worst, 1 - elapsedCycles() / cycleLimit);
        }
        if (calendarLimitMonths != null && calendarLimitMonths > 0 && acquiredAt != null) {
            double monthsElapsed = (System.currentTimeMillis() - acquiredAt.getTime()) / MILLIS_PER_MONTH;
            worst = min(worst, 1 - monthsElapsed / calendarLimitMonths);
        }
        return worst == null ? null : Math.max(0d, worst);
    }

    /**
     * An overhaul resets the clock, so hours since overhaul win when present.
     */
    @Transient
    public double elapsedHours() {
        double sinceOverhaul = nz(hoursSinceOverhaul);
        return sinceOverhaul > 0 ? sinceOverhaul : nz(totalHours);
    }

    @Transient
    public double elapsedCycles() {
        double sinceOverhaul = nz(cyclesSinceOverhaul);
        return sinceOverhaul > 0 ? sinceOverhaul : nz(totalCycles);
    }

    @Transient
    public boolean isLifeLimited() {
        return hourLimit != null || cycleLimit != null || calendarLimitMonths != null;
    }

    private static Double min(Double current, double candidate) {
        return current == null ? candidate : Math.min(current, candidate);
    }

    private static double nz(Double value) {
        return value == null ? 0d : value;
    }
}
