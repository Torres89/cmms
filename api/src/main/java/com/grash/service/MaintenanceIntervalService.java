package com.grash.service;

import com.grash.dto.IntervalStatusDTO;
import com.grash.model.MaintenanceInterval;
import com.grash.model.Meter;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.Reading;
import com.grash.model.enums.IntervalBasis;
import com.grash.model.enums.TriggerMode;
import com.grash.repository.MaintenanceIntervalRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-counter maintenance intervals — "every 500 hours or 3 months,
 * whichever comes first".
 * <p>
 * Progress is computed rather than stored, so it is always right the moment a
 * reading lands. The warning threshold on each interval is what produces the
 * "due in ~30 h" line on the dossier instead of a binary overdue flag that
 * arrives too late to plan around.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceIntervalService {

    private static final double MILLIS_PER_DAY = 1000d * 60 * 60 * 24;
    private static final double DAYS_PER_MONTH = 30.4375;

    private final MaintenanceIntervalRepository maintenanceIntervalRepository;
    private final ReadingRepository readingRepository;

    public List<MaintenanceInterval> findByPreventiveMaintenance(Long pmId) {
        return maintenanceIntervalRepository.findByPreventiveMaintenance_Id(pmId);
    }

    public Optional<MaintenanceInterval> findById(Long id) {
        return maintenanceIntervalRepository.findById(id);
    }

    public MaintenanceInterval save(MaintenanceInterval interval) {
        return maintenanceIntervalRepository.save(interval);
    }

    public void delete(Long id) {
        maintenanceIntervalRepository.deleteById(id);
    }

    public List<MaintenanceInterval> findByCompany(Long companyId) {
        return maintenanceIntervalRepository.findByCompany_Id(companyId);
    }

    /**
     * Record that the maintenance was done, resetting every counter on it.
     */
    @Transactional
    public void markCompleted(Long preventiveMaintenanceId, Date completedAt) {
        Date when = completedAt == null ? new Date() : completedAt;
        for (MaintenanceInterval interval : findByPreventiveMaintenance(preventiveMaintenanceId)) {
            interval.setLastCompletedAt(when);
            if (interval.getBasis() == IntervalBasis.METER && interval.getMeter() != null) {
                latestReading(interval.getMeter().getId())
                        .ifPresent(interval::setLastCompletedValue);
            }
            maintenanceIntervalRepository.save(interval);
        }
    }

    /**
     * How far through each of a PM's counters we are, and what that means for
     * the PM as a whole under its trigger mode.
     */
    public IntervalStatusDTO status(PreventiveMaintenance pm) {
        List<MaintenanceInterval> intervals = findByPreventiveMaintenance(pm.getId());
        IntervalStatusDTO status = new IntervalStatusDTO();
        status.setPreventiveMaintenanceId(pm.getId());
        status.setTitle(pm.getName() != null ? pm.getName() : pm.getTitle());
        status.setTriggerMode(pm.getTriggerMode() == null
                ? TriggerMode.WHICHEVER_FIRST.name() : pm.getTriggerMode().name());

        if (intervals.isEmpty()) {
            // No multi-counter intervals: this PM still runs off its calendar Schedule.
            status.setCounters(Collections.emptyList());
            return status;
        }

        List<IntervalStatusDTO.CounterStatus> counters = intervals.stream()
                .map(this::counterStatus)
                .collect(Collectors.toList());
        status.setCounters(counters);

        List<IntervalStatusDTO.CounterStatus> withProgress = counters.stream()
                .filter(c -> c.getPercent() != null)
                .collect(Collectors.toList());
        if (withProgress.isEmpty()) {
            return status;
        }

        TriggerMode mode = pm.getTriggerMode() == null ? TriggerMode.WHICHEVER_FIRST : pm.getTriggerMode();
        double overall = mode == TriggerMode.WHICHEVER_FIRST
                // First counter to reach 100 % fires the PM, so the PM is as far
                // along as its furthest-along counter.
                ? withProgress.stream().mapToDouble(IntervalStatusDTO.CounterStatus::getPercent).max().orElse(0)
                : withProgress.stream().mapToDouble(IntervalStatusDTO.CounterStatus::getPercent).min().orElse(0);
        status.setPercent(overall);
        status.setDue(overall >= 100);

        IntervalStatusDTO.CounterStatus driver = withProgress.stream()
                .max(Comparator.comparingDouble(IntervalStatusDTO.CounterStatus::getPercent))
                .orElse(null);
        if (driver != null) {
            status.setDrivingCounter(driver.getLabel());
            status.setRemaining(driver.getRemaining());
            status.setRemainingUnit(driver.getUnit());
            status.setWarning(overall >= nz(driver.getWarnAtPercent(), 90.0));
        }
        return status;
    }

    private IntervalStatusDTO.CounterStatus counterStatus(MaintenanceInterval interval) {
        IntervalStatusDTO.CounterStatus counter = new IntervalStatusDTO.CounterStatus();
        counter.setIntervalId(interval.getId());
        counter.setBasis(interval.getBasis() == null ? null : interval.getBasis().name());
        counter.setIntervalValue(interval.getIntervalValue());
        counter.setUnit(interval.getUnit());
        counter.setWarnAtPercent(interval.getWarnAtPercent());

        if (interval.getIntervalValue() == null || interval.getIntervalValue() <= 0) {
            return counter;
        }

        if (interval.getBasis() == IntervalBasis.METER && interval.getMeter() != null) {
            Meter meter = interval.getMeter();
            counter.setLabel(meter.getName());
            if (counter.getUnit() == null) counter.setUnit(meter.getUnit());
            Optional<Double> current = latestReading(meter.getId());
            if (current.isEmpty()) {
                return counter;
            }
            double baseline = nz(interval.getLastCompletedValue(), 0d);
            double elapsed = Math.max(0, current.get() - baseline);
            counter.setElapsed(elapsed);
            counter.setPercent(100.0 * elapsed / interval.getIntervalValue());
            counter.setRemaining(Math.max(0, interval.getIntervalValue() - elapsed));
            return counter;
        }

        if (interval.getBasis() == IntervalBasis.CALENDAR) {
            counter.setLabel(describeCalendar(interval));
            Date since = interval.getLastCompletedAt();
            if (since == null) {
                return counter;
            }
            double daysElapsed = (System.currentTimeMillis() - since.getTime()) / MILLIS_PER_DAY;
            double intervalDays = toDays(interval.getIntervalValue(), interval.getUnit());
            counter.setElapsed(daysElapsed);
            counter.setUnit("days");
            counter.setPercent(100.0 * daysElapsed / intervalDays);
            counter.setRemaining(Math.max(0, intervalDays - daysElapsed));
            return counter;
        }

        // EVENT intervals are driven by something happening, so there is no
        // meaningful progress to report between occurrences.
        counter.setLabel(interval.getDescription());
        return counter;
    }

    private String describeCalendar(MaintenanceInterval interval) {
        if (interval.getDescription() != null) return interval.getDescription();
        String unit = interval.getUnit() == null ? "days" : interval.getUnit();
        return "Every " + trim(interval.getIntervalValue()) + " " + unit;
    }

    private String trim(Double value) {
        if (value == null) return "?";
        return value == Math.rint(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private double toDays(double value, String unit) {
        if (unit == null) return value;
        switch (unit.toLowerCase(Locale.ROOT)) {
            case "month":
            case "months":
            case "meses":
                return value * DAYS_PER_MONTH;
            case "week":
            case "weeks":
                return value * 7;
            case "year":
            case "years":
                return value * 365;
            default:
                return value;
        }
    }

    private Optional<Double> latestReading(Long meterId) {
        Collection<Reading> readings = readingRepository.findByMeter_Id(meterId);
        return readings.stream().max(Comparator.comparing(Reading::getCreatedAt)).map(Reading::getValue);
    }

    private static double nz(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
