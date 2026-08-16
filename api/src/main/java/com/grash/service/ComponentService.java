package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.ComponentEventType;
import com.grash.model.enums.ComponentStatus;
import com.grash.repository.ComponentEventRepository;
import com.grash.repository.ComponentInstanceRepository;
import com.grash.repository.MeterRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serialized components and their back-to-birth ledger.
 * <p>
 * Two things make this useful rather than decorative:
 * <ul>
 *   <li>installs and removals are recorded as events, never as edits, so the
 *       history of a position survives every component swap;</li>
 *   <li>counters roll forward from meter readings automatically, which is what
 *       turns "hours on the current spindle" from a guess into a number.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentService {

    /** Warn here first, then again at the second threshold. */
    public static final double FIRST_ALERT_FRACTION = 0.10;
    public static final double SECOND_ALERT_FRACTION = 0.05;

    private final ComponentInstanceRepository componentInstanceRepository;
    private final ComponentEventRepository componentEventRepository;
    private final ReadingRepository readingRepository;
    private final MeterRepository meterRepository;

    private AssetService assetService;
    private NotificationService notificationService;

    @Autowired
    public void setDeps(@Lazy AssetService assetService, @Lazy NotificationService notificationService) {
        this.assetService = assetService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public ComponentInstance create(ComponentInstance component) {
        ComponentInstance saved = componentInstanceRepository.save(component);
        record(saved, ComponentEventType.RECEIVED,
                saved.getAcquiredAt() == null ? new Date() : saved.getAcquiredAt(),
                null, null, "Component recorded in the system");
        return saved;
    }

    public ComponentInstance save(ComponentInstance component) {
        return componentInstanceRepository.save(component);
    }

    public Optional<ComponentInstance> findById(Long id) {
        return componentInstanceRepository.findById(id);
    }

    public List<ComponentInstance> findByCompany(Long companyId) {
        return componentInstanceRepository.findByCompany_Id(companyId);
    }

    public List<ComponentInstance> findByPosition(Long assetId) {
        return componentInstanceRepository.findByCurrentPosition_Id(assetId);
    }

    /**
     * Everything installed at or under an asset — the machine's current
     * configuration, not just what hangs off the top node.
     */
    public List<ComponentInstance> findInstalledInSubtree(Long assetId) {
        return componentInstanceRepository.findInstalledInSubtree(assetId);
    }

    public List<ComponentEvent> historyOf(Long componentId) {
        return componentEventRepository.findByComponent_IdOrderByOccurredAtDesc(componentId);
    }

    /**
     * The ledger for a position and everything beneath it.
     * <p>
     * Asking a machine for events recorded against the machine itself returns
     * almost nothing: components are fitted to its subunits, not to it. The
     * recursive query is what makes "every spindle cartridge this machine has
     * ever had" answerable from the machine's own page. Falls back to the
     * direct lookup where recursive CTEs are unavailable, as the other subtree
     * queries here do.
     */
    public List<ComponentEvent> historyOfPosition(Long assetId) {
        try {
            return componentEventRepository.findInAssetSubtree(assetId);
        } catch (Exception e) {
            log.debug("Falling back to direct component event lookup for asset {}: {}",
                    assetId, e.getMessage());
            return componentEventRepository.findByPosition_IdOrderByOccurredAtDesc(assetId);
        }
    }

    public void delete(Long id) {
        componentInstanceRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Ledger operations
    // ------------------------------------------------------------------

    /**
     * Put a component into a position, closing out whatever was there.
     */
    @Transactional
    public ComponentInstance install(Long componentId, Long positionAssetId, Date at, Double meterValue,
                                     WorkOrder workOrder, OwnUser performedBy, String reason) {
        ComponentInstance component = require(componentId);
        Asset position = assetService.findById(positionAssetId)
                .orElseThrow(() -> new CustomException("Position asset not found", HttpStatus.NOT_FOUND));
        if (!position.getCompany().getId().equals(component.getCompany().getId())) {
            throw new CustomException("Component and position belong to different companies",
                    HttpStatus.FORBIDDEN);
        }
        if (component.getStatus() == ComponentStatus.SCRAPPED) {
            throw new CustomException("A scrapped component cannot be installed", HttpStatus.NOT_ACCEPTABLE);
        }
        Date when = at == null ? new Date() : at;

        // A position holds one component at a time; whatever is in there comes out first.
        for (ComponentInstance occupant : componentInstanceRepository.findByCurrentPosition_Id(positionAssetId)) {
            if (!occupant.getId().equals(componentId)) {
                remove(occupant.getId(), when, meterValue, workOrder, performedBy,
                        "Displaced by " + component.getSerialNumber());
            }
        }

        component.setCurrentPosition(position);
        component.setStatus(ComponentStatus.IN_SERVICE);
        backfillUsageSinceInstall(component, position, meterValue);
        ComponentInstance saved = componentInstanceRepository.save(component);

        ComponentEvent event = record(saved, ComponentEventType.INSTALLED, when, position, workOrder, reason);
        event.setPositionMeterValue(meterValue);
        event.setPerformedBy(performedBy);
        componentEventRepository.save(event);
        return saved;
    }

    /**
     * Credit a component with the usage that has already happened since it went in.
     * <p>
     * Counters otherwise only advance from readings taken *after* the install,
     * which is right for a component fitted today and wrong for every machine
     * being commissioned into the system with history behind it. Telling the
     * commissioning engineer "this spindle went in at 8,940 hours" and then
     * showing it with zero hours on a machine reading 11,840 is not a rounding
     * problem, it is the wrong answer to the question the feature exists for.
     */
    private void backfillUsageSinceInstall(ComponentInstance component, Asset position, Double meterValue) {
        // A meter value of 0 is a real answer — it means "installed when the
        // machine was new" — so only a missing value skips the back-fill.
        if (meterValue == null || meterValue < 0) {
            return;
        }
        // Only credit a component that is not already carrying hours, so
        // re-running an install never double-counts.
        if (nz(component.getTotalHours()) > 0) {
            return;
        }
        Asset machine = position;
        int guard = 0;
        while (machine != null && guard++ < 10) {
            for (Meter meter : meterRepository.findByAsset_Id(machine.getId())) {
                if (classify(meter.getUnit(), meter.getName()) != CounterKind.HOURS) {
                    continue;
                }
                // The same rule the running roll-up uses: a machine with
                // spindle, power-on and idle counters has exactly one of them
                // that a part's life is spent against.
                if (!isUsageBasisFor(meter, CounterKind.HOURS)) {
                    continue;
                }
                Optional<Double> current = readingRepository.findByMeter_Id(meter.getId()).stream()
                        .map(Reading::getValue)
                        .max(Double::compare);
                if (current.isEmpty() || current.get() <= meterValue) {
                    continue;
                }
                double elapsed = current.get() - meterValue;
                component.setTotalHours(elapsed);
                component.setHoursSinceOverhaul(elapsed);
                log.debug("Credited component {} with {} h accrued since it was installed at {} h",
                        component.getSerialNumber(), elapsed, meterValue);
                return;
            }
            machine = machine.getParentAsset();
        }
    }

    /**
     * Take a component out. Counters freeze where they are — that is the point
     * of the record.
     */
    @Transactional
    public ComponentInstance remove(Long componentId, Date at, Double meterValue,
                                    WorkOrder workOrder, OwnUser performedBy, String reason) {
        ComponentInstance component = require(componentId);
        Asset position = component.getCurrentPosition();
        Date when = at == null ? new Date() : at;

        component.setCurrentPosition(null);
        component.setStatus(ComponentStatus.REMOVED);
        ComponentInstance saved = componentInstanceRepository.save(component);

        ComponentEvent event = record(saved, ComponentEventType.REMOVED, when, position, workOrder, reason);
        event.setPositionMeterValue(meterValue);
        event.setPerformedBy(performedBy);
        componentEventRepository.save(event);
        return saved;
    }

    /**
     * An overhaul resets the since-overhaul counters; time since new keeps
     * running, because it always does.
     */
    @Transactional
    public ComponentInstance overhaul(Long componentId, Date at, Vendor vendor, Double cost,
                                      OwnUser performedBy, String reason) {
        ComponentInstance component = require(componentId);
        Date when = at == null ? new Date() : at;

        ComponentEvent event = record(component, ComponentEventType.OVERHAULED, when,
                component.getCurrentPosition(), null, reason);
        event.setVendor(vendor);
        event.setCost(cost);
        event.setPerformedBy(performedBy);
        componentEventRepository.save(event);

        component.setHoursSinceOverhaul(0d);
        component.setCyclesSinceOverhaul(0d);
        if (component.getStatus() == ComponentStatus.IN_REPAIR) {
            component.setStatus(ComponentStatus.IN_STOCK);
        }
        return componentInstanceRepository.save(component);
    }

    @Transactional
    public ComponentInstance scrap(Long componentId, Date at, OwnUser performedBy, String reason) {
        ComponentInstance component = require(componentId);
        component.setCurrentPosition(null);
        component.setStatus(ComponentStatus.SCRAPPED);
        ComponentInstance saved = componentInstanceRepository.save(component);
        ComponentEvent event = record(saved, ComponentEventType.SCRAPPED,
                at == null ? new Date() : at, null, null, reason);
        event.setPerformedBy(performedBy);
        componentEventRepository.save(event);
        return saved;
    }

    /**
     * Free-form ledger entry — inspections, repairs, modifications.
     */
    @Transactional
    public ComponentEvent logEvent(ComponentEvent event) {
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(new Date());
        }
        ComponentInstance component = require(event.getComponent().getId());
        event.setComponent(component);
        event.setComponentHours(component.getTotalHours());
        event.setComponentCycles(component.getTotalCycles());
        return componentEventRepository.save(event);
    }

    private ComponentEvent record(ComponentInstance component, ComponentEventType type, Date at,
                                  Asset position, WorkOrder workOrder, String reason) {
        ComponentEvent event = new ComponentEvent();
        event.setComponent(component);
        event.setCompany(component.getCompany());
        event.setType(type);
        event.setOccurredAt(at);
        event.setPosition(position);
        event.setWorkOrder(workOrder);
        event.setReason(reason);
        event.setComponentHours(component.getTotalHours());
        event.setComponentCycles(component.getTotalCycles());
        return componentEventRepository.save(event);
    }

    private ComponentInstance require(Long id) {
        return componentInstanceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Component not found", HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------
    // Counter roll-up
    // ------------------------------------------------------------------

    /**
     * Roll a new meter reading into every component installed at or under the
     * meter's asset.
     * <p>
     * Called on every reading. Deliberately forgiving: a bad reading must never
     * take down the reading endpoint, and a meter whose unit we don't recognise
     * simply doesn't drive any counters.
     */
    @Transactional
    public void applyReading(Reading reading) {
        try {
            if (reading.getMeter() == null || reading.getMeter().getId() == null) {
                return;
            }
            // The meter on a freshly posted reading is whatever the request body
            // carried — usually just {"id": n}, with no asset attached. Load the
            // real one rather than trusting the association.
            Meter meter = meterRepository.findById(reading.getMeter().getId()).orElse(null);
            if (meter == null || meter.getAsset() == null) {
                return;
            }
            CounterKind kind = classify(meter.getUnit(), meter.getName());
            if (kind == CounterKind.UNKNOWN) {
                log.debug("Meter {} ('{}' {}) drives no counter", meter.getId(), meter.getName(),
                        meter.getUnit());
                return;
            }
            if (!isUsageBasisFor(meter, kind)) {
                log.debug("Meter {} ('{}') is not the {} usage basis for asset {}",
                        meter.getId(), meter.getName(), kind, meter.getAsset().getId());
                return;
            }
            double delta = deltaSincePreviousReading(reading, meter.getId());
            if (delta <= 0) {
                log.debug("Reading {} on meter {} is not an increase; nothing to roll",
                        reading.getId(), meter.getId());
                return;
            }
            List<ComponentInstance> installed =
                    componentInstanceRepository.findInstalledInSubtree(meter.getAsset().getId());
            log.debug("Rolling {} {} from meter {} into {} installed component(s)",
                    delta, kind, meter.getId(), installed.size());
            for (ComponentInstance component : installed) {
                if (kind == CounterKind.HOURS) {
                    component.setTotalHours(nz(component.getTotalHours()) + delta);
                    component.setHoursSinceOverhaul(nz(component.getHoursSinceOverhaul()) + delta);
                } else {
                    component.setTotalCycles(nz(component.getTotalCycles()) + delta);
                    component.setCyclesSinceOverhaul(nz(component.getCyclesSinceOverhaul()) + delta);
                }
            }
            componentInstanceRepository.saveAll(installed);
        } catch (Exception e) {
            log.warn("Could not roll reading {} into component counters: {}", reading.getId(), e.getMessage());
        }
    }

    /**
     * Meters are cumulative counters, so the usage to add is the increase since
     * the previous reading, not the reading itself.
     */
    private double deltaSincePreviousReading(Reading reading, Long meterId) {
        Collection<Reading> readings = readingRepository.findByMeter_Id(meterId);
        double previous = readings.stream()
                .filter(r -> !r.getId().equals(reading.getId()))
                .filter(r -> r.getValue() <= reading.getValue())
                .mapToDouble(Reading::getValue)
                .max()
                .orElse(0d);
        return reading.getValue() - previous;
    }

    private enum CounterKind {HOURS, CYCLES, UNKNOWN}

    /**
     * Whether this meter is the one a component's life is spent against.
     * <p>
     * A machine typically has several meters of the same kind — spindle hours,
     * power-on hours, idle hours — and crediting a component from every one of
     * them inflates its wear by whatever the others happen to read. Exactly one
     * meter per kind is the basis: the one flagged as such, or, on data that
     * predates the flag, the oldest meter of that kind on the machine.
     */
    private boolean isUsageBasisFor(Meter meter, CounterKind kind) {
        List<Meter> sameKind = meterRepository.findByAsset_Id(meter.getAsset().getId()).stream()
                .filter(m -> classify(m.getUnit(), m.getName()) == kind)
                .sorted(Comparator.comparing(Meter::getId))
                .collect(Collectors.toList());
        if (sameKind.size() <= 1) {
            return true;
        }
        List<Meter> flagged = sameKind.stream().filter(Meter::isUsageBasis).collect(Collectors.toList());
        if (!flagged.isEmpty()) {
            return flagged.get(0).getId().equals(meter.getId());
        }
        return sameKind.get(0).getId().equals(meter.getId());
    }

    private CounterKind classify(String unit, String name) {
        String haystack = ((unit == null ? "" : unit) + " " + (name == null ? "" : name)).toLowerCase(Locale.ROOT);
        if (haystack.matches(".*\\b(h|hr|hrs|hour|hours|horas|smr)\\b.*")) {
            return CounterKind.HOURS;
        }
        if (haystack.contains("cycle") || haystack.contains("ciclo") || haystack.contains("count")
                || haystack.contains("piece") || haystack.contains("pieza")) {
            return CounterKind.CYCLES;
        }
        return CounterKind.UNKNOWN;
    }

    private static double nz(Double value) {
        return value == null ? 0d : value;
    }

    // ------------------------------------------------------------------
    // Life-limit alerts
    // ------------------------------------------------------------------

    public static class LifeAlert {
        public final ComponentInstance component;
        public final double remainingFraction;
        public final String threshold;

        LifeAlert(ComponentInstance component, double remainingFraction, String threshold) {
            this.component = component;
            this.remainingFraction = remainingFraction;
            this.threshold = threshold;
        }

        public ComponentInstance getComponent() {
            return component;
        }

        public double getRemainingFraction() {
            return remainingFraction;
        }

        public String getThreshold() {
            return threshold;
        }
    }

    /**
     * Components at or past the 10 % and 5 % remaining-life marks, on whichever
     * counter runs out first.
     */
    public List<LifeAlert> findLifeAlerts(Long companyId) {
        return componentInstanceRepository.findLifeLimitedInService(companyId).stream()
                .map(component -> {
                    Double remaining = component.getRemainingLifeFraction();
                    if (remaining == null) return null;
                    if (remaining <= SECOND_ALERT_FRACTION) return new LifeAlert(component, remaining, "5%");
                    if (remaining <= FIRST_ALERT_FRACTION) return new LifeAlert(component, remaining, "10%");
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(LifeAlert::getRemainingFraction))
                .collect(Collectors.toList());
    }
}
