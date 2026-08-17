package com.grash.service;

import com.grash.dto.RestockKitDTO;
import com.grash.model.Asset;
import com.grash.model.AssetBomLine;
import com.grash.model.Meter;
import com.grash.model.Part;
import com.grash.model.PartSupplier;
import com.grash.model.Reading;
import com.grash.repository.PartConsumptionRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
public class RestockService {

    private static final double MILLIS_PER_DAY = 1000d * 60 * 60 * 24;

    private final AssetBomService assetBomService;
    private final PartSourcingService partSourcingService;
    private final MeterService meterService;
    private final ReadingRepository readingRepository;
    private final PartConsumptionRepository partConsumptionRepository;

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

        double hoursPerDay = hoursPerDay(asset);
        kit.setHoursPerDay(hoursPerDay);

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

            Integer daysUntilDue = daysUntilDue(line, hoursPerDay);
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
            // with a 30-day lead time is already late.
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
     * Average machine hours per day, from the meter history.
     * <p>
     * Returns 0 when there is nothing to measure from, which makes hour-based
     * intervals fall back to their calendar equivalent rather than producing an
     * imaginary date.
     */
    private double hoursPerDay(Asset asset) {
        for (Meter meter : meterService.findByAsset(asset.getId())) {
            String unit = meter.getUnit() == null ? "" : meter.getUnit().toLowerCase(Locale.ROOT);
            if (!unit.startsWith("h")) {
                continue;
            }
            List<Reading> readings = new ArrayList<>(readingRepository.findByMeter_Id(meter.getId()));
            if (readings.size() < 2) {
                continue;
            }
            readings.sort(Comparator.comparing(Reading::getCreatedAt));
            Reading first = readings.get(0);
            Reading last = readings.get(readings.size() - 1);
            double days = (last.getCreatedAt().getTime() - first.getCreatedAt().getTime()) / MILLIS_PER_DAY;
            if (days >= 1 && last.getValue() > first.getValue()) {
                return (last.getValue() - first.getValue()) / days;
            }
        }
        return 0;
    }

    private Integer daysUntilDue(AssetBomLine line, double hoursPerDay) {
        if (line.getReplaceIntervalHours() != null && hoursPerDay > 0) {
            return (int) Math.round(line.getReplaceIntervalHours() / hoursPerDay);
        }
        if (line.getReplaceIntervalMonths() != null) {
            return (int) Math.round(line.getReplaceIntervalMonths() * 30.4375);
        }
        return null;
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
