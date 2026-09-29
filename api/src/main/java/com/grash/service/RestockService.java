package com.grash.service;

import com.grash.dto.RestockKitDTO;
import com.grash.model.Asset;
import com.grash.model.AssetBomLine;
import com.grash.model.Meter;
import com.grash.model.Part;
import com.grash.model.PartSupplier;
import com.grash.model.Reading;
import com.grash.repository.AssetRepository;
import com.grash.repository.PartConsumptionRepository;
import com.grash.repository.PartQuantityRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Restock kits and reorder points.
 * <p>
 * The point is a single button that says "these six consumables are due on this
 * machine in the next month, here is what they cost and how long they take" —
 * which is the difference between a shop that has the filter and a shop that
 * waits nine days for it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestockService {

    private static final double MILLIS_PER_DAY = 1000d * 60 * 60 * 24;
    private static final double DAYS_PER_MONTH = 30.4375;

    private final AssetBomService assetBomService;
    private final PartSourcingService partSourcingService;
    private final MeterService meterService;
    private final ReadingRepository readingRepository;
    private final PartConsumptionRepository partConsumptionRepository;
    private final PartQuantityRepository partQuantityRepository;
    private final AssetRepository assetRepository;

    /** Replaceable in tests; "today" is otherwise the wall clock. */
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Consumables coming due on a machine within the horizon.
     *
     * @param horizonDays how far ahead to look
     */
    public RestockKitDTO kitFor(Asset asset, int horizonDays) {
        RestockKitDTO kit = new RestockKitDTO();
        kit.setAssetId(asset.getId());
        kit.setAssetName(asset.getName());
        kit.setHorizonDays(horizonDays);

        List<Reading> hoursReadings = hoursReadings(asset);
        double hoursPerDay = hoursPerDay(hoursReadings);
        kit.setHoursPerDay(hoursPerDay);
        List<Long> subtree = subtreeIds(asset);

        for (AssetBomLine line : assetBomService.findConsumables(asset.getId())) {
            Part part = line.getPart();
            if (part == null) {
                continue;
            }
            RestockKitDTO.KitLine kitLine = new RestockKitDTO.KitLine();
            kitLine.setPartId(part.getId());
            kitLine.setName(part.getName());
            kitLine.setMpn(part.getMpn());
            kitLine.setPositionCode(line.getPositionCode());
            kitLine.setQuantity(line.getQtyPerAssembly() == null ? 1.0 : line.getQtyPerAssembly());
            kitLine.setOnHand(part.getQuantity());
            kitLine.setUnit(part.getUnit());

            Date lastReplaced = lastReplacement(line, asset, subtree);
            kitLine.setLastReplacedAt(lastReplaced);
            Integer daysUntilDue = daysUntilDue(line, lastReplaced, hoursReadings, hoursPerDay);
            kitLine.setDaysUntilDue(daysUntilDue);

            Optional<PartSupplier> preferred = partSourcingService.findPreferredSupplier(part.getId());
            if (preferred.isEmpty()) {
                preferred = partSourcingService.findSuppliers(part.getId()).stream().findFirst();
            }
            preferred.ifPresent(supplier -> {
                kitLine.setSupplierName(supplier.getVendor() == null ? null : supplier.getVendor().getName());
                kitLine.setUnitPrice(supplier.getUnitPrice());
                kitLine.setCurrency(supplier.getCurrency());
                kitLine.setLeadTimeDays(supplier.getLeadTimeDays());
                kitLine.setProductUrl(supplier.getProductUrl());
            });

            // Lead time is the whole reason this exists: a part due in 20 days
            // with a 30-day lead time is already late. Overdue (<= 0) is always
            // urgent.
            int leadTime = kitLine.getLeadTimeDays() != null ? kitLine.getLeadTimeDays()
                    : (part.getLeadTimeDaysTypical() == null ? 0 : part.getLeadTimeDaysTypical().intValue());
            kitLine.setUrgent(daysUntilDue != null && daysUntilDue <= leadTime);
            kitLine.setShortfall(Math.max(0, kitLine.getQuantity() - part.getQuantity()));

            boolean withinHorizon = daysUntilDue == null || daysUntilDue <= horizonDays;
            if (withinHorizon && kitLine.getShortfall() > 0) {
                kit.getLines().add(kitLine);
            }
        }

        kit.getLines().sort(Comparator.comparing(
                line -> line.getDaysUntilDue() == null ? Integer.MAX_VALUE : line.getDaysUntilDue()));
        kit.setEstimatedTotal(kit.getLines().stream()
                .filter(line -> line.getUnitPrice() != null)
                .mapToDouble(line -> line.getUnitPrice() * line.getShortfall())
                .sum());
        if (kit.getLines().isEmpty()) {
            kit.setNote("Nothing is due within " + horizonDays + " days that isn't already on the shelf.");
        }
        return kit;
    }

    /**
     * When a consumable was last replaced on this machine.
     * <p>
     * The last completed work order against the machine or anything under it
     * that used the part. A line that has never been replaced here counts from
     * when it was documented — or from the machine's in-service date when that
     * is older, because a filter nobody has recorded changing since the machine
     * went in is not "fresh as of the day someone typed it in".
     */
    Date lastReplacement(AssetBomLine line, Asset asset, Collection<Long> subtree) {
        Part part = line.getPart();
        if (part != null && part.getId() != null && !subtree.isEmpty()) {
            try {
                Date used = partQuantityRepository.findLastCompletedUse(part.getId(), subtree);
                if (used != null) {
                    return used;
                }
            } catch (Exception e) {
                log.debug("Could not look up the last use of part {} on asset {}: {}",
                        part.getId(), asset.getId(), e.getMessage());
            }
        }
        Date fallback = line.getCreatedAt();
        Date inService = asset.getInServiceDate();
        if (inService != null && (fallback == null || inService.before(fallback))) {
            fallback = inService;
        }
        return fallback;
    }

    /**
     * Days until a consumable is due, counted from its last replacement;
     * zero or negative when it is already due. With both an hour and a month
     * interval, whichever comes first.
     * <p>
     * Hours are measured on the machine's hours meter since the replacement,
     * then projected forward at the measured rate. Without a rate an hour
     * interval can still say "overdue", but cannot put a date on "not yet".
     */
    Integer daysUntilDue(AssetBomLine line, Date lastReplaced, List<Reading> hoursReadings, double hoursPerDay) {
        Integer byHours = null;
        if (line.getReplaceIntervalHours() != null) {
            Double used = hoursSince(lastReplaced, hoursReadings);
            double remaining = line.getReplaceIntervalHours() - (used == null ? 0 : used);
            if (hoursPerDay > 0) {
                byHours = (int) Math.floor(remaining / hoursPerDay);
            } else if (remaining <= 0) {
                byHours = 0;
            }
        }
        Integer byMonths = null;
        if (line.getReplaceIntervalMonths() != null) {
            if (lastReplaced == null) {
                byMonths = (int) Math.round(line.getReplaceIntervalMonths() * DAYS_PER_MONTH);
            } else {
                Calendar due = Calendar.getInstance();
                due.setTime(lastReplaced);
                due.add(Calendar.MONTH, line.getReplaceIntervalMonths());
                byMonths = (int) Math.floor((due.getTimeInMillis() - clock.millis()) / MILLIS_PER_DAY);
            }
        }
        if (byHours == null) return byMonths;
        if (byMonths == null) return byHours;
        return Math.min(byHours, byMonths);
    }

    /**
     * Machine hours run since a date: the latest reading now, less the reading
     * at (or, failing that, first after) the date. Null when it can't be known.
     */
    private Double hoursSince(Date since, List<Reading> readings) {
        if (since == null || readings.isEmpty()) {
            return null;
        }
        Reading base = null;
        for (Reading reading : readings) {
            if (reading.getCreatedAt() != null && !reading.getCreatedAt().after(since)) {
                base = reading;
            }
        }
        if (base == null) {
            base = readings.get(0);
        }
        Reading latest = readings.get(readings.size() - 1);
        return Math.max(0, latest.getValue() - base.getValue());
    }

    /**
     * The machine's hours meter, oldest reading first. With several hour
     * meters (spindle, power-on, idle) it is the one flagged as the usage
     * basis, or failing that the oldest — the same rule components are aged by.
     */
    private List<Reading> hoursReadings(Asset asset) {
        List<Meter> hourMeters = meterService.findByAsset(asset.getId()).stream()
                .filter(meter -> meter.getUnit() != null
                        && meter.getUnit().toLowerCase(Locale.ROOT).startsWith("h"))
                .sorted(Comparator.comparing(Meter::getId))
                .collect(Collectors.toList());
        if (hourMeters.isEmpty()) {
            return Collections.emptyList();
        }
        Meter meter = hourMeters.stream().filter(Meter::isUsageBasis).findFirst().orElse(hourMeters.get(0));
        return readingRepository.findByMeter_Id(meter.getId()).stream()
                .filter(reading -> reading.getCreatedAt() != null)
                .sorted(Comparator.comparing(Reading::getCreatedAt))
                .collect(Collectors.toList());
    }

    /**
     * Average machine hours per day, from the meter history.
     * <p>
     * Returns 0 when there is nothing to measure from, which makes hour-based
     * intervals fall back to their calendar equivalent rather than producing an
     * imaginary date.
     */
    private double hoursPerDay(List<Reading> readings) {
        if (readings.size() < 2) {
            return 0;
        }
        Reading first = readings.get(0);
        Reading last = readings.get(readings.size() - 1);
        double days = (last.getCreatedAt().getTime() - first.getCreatedAt().getTime()) / MILLIS_PER_DAY;
        if (days >= 1 && last.getValue() > first.getValue()) {
            return (last.getValue() - first.getValue()) / days;
        }
        return 0;
    }

    /** The asset and everything beneath it. */
    private List<Long> subtreeIds(Asset asset) {
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(asset.getId());
        while (!queue.isEmpty() && ids.size() < 1000) {
            Long id = queue.poll();
            if (id == null || ids.contains(id)) continue;
            ids.add(id);
            for (Asset child : assetRepository.findByParentAsset_Id(id, Sort.unsorted())) {
                queue.add(child.getId());
            }
        }
        return ids;
    }

    /**
     * Suggest a reorder point from twelve months of consumption and the part's
     * lead time.
     */
    public Double suggestReorderPoint(Part part) {
        Calendar yearAgo = Calendar.getInstance();
        yearAgo.add(Calendar.YEAR, -1);
        double annualUsage = partConsumptionRepository.findByPart_Id(part.getId()).stream()
                .filter(consumption -> consumption.getCreatedAt() != null
                        && consumption.getCreatedAt().after(yearAgo.getTime()))
                .mapToDouble(consumption -> consumption.getQuantity())
                .sum();
        Integer leadTime = partSourcingService.findPreferredSupplier(part.getId())
                .map(PartSupplier::getLeadTimeDays)
                .orElseGet(() -> part.getLeadTimeDaysTypical() == null
                        ? null : part.getLeadTimeDaysTypical().intValue());
        return partSourcingService.suggestReorderPoint(annualUsage, leadTime, part.getCriticality());
    }
}
