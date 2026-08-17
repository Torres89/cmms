package com.grash.service;

import com.grash.dto.MaintenancePlanProposalDTO;
import com.grash.dto.KnowledgeSearchResultDTO;
import com.grash.model.Asset;
import com.grash.model.AssetBomLine;
import com.grash.model.Meter;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.Reading;
import com.grash.repository.PreventiveMaintenanceRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Proposes preventive maintenance from the manual's interval charts and the
 * machine's actual usage.
 * <p>
 * The output is explicitly a <em>proposal</em>. Nothing is created; a human
 * approves it. A system that silently invents a maintenance schedule and then
 * nags about it is worse than no schedule, because people stop trusting the
 * nagging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenancePlanProposalService {

    /** "every 500 hours", "each 250 h", "cada 500 horas". */
    private static final Pattern HOUR_INTERVAL = Pattern.compile(
            "(?i)\\b(?:every|each|cada)\\s+([0-9][0-9.,]*)\\s*(?:h|hr|hrs|hours?|horas?)\\b");
    /** "every 3 months", "annually", "cada 6 meses". */
    private static final Pattern CALENDAR_INTERVAL = Pattern.compile(
            "(?i)\\b(?:every|each|cada)\\s+([0-9][0-9.,]*)\\s*(day|days|week|weeks|month|months|year|years|"
                    + "d[ií]as?|semanas?|meses|a[nñ]os?)\\b");

    private final KnowledgeService knowledgeService;
    private final AssetBomService assetBomService;
    private final PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    private final MeterService meterService;
    private final ReadingRepository readingRepository;

    public MaintenancePlanProposalDTO propose(Asset asset, Long companyId) {
        MaintenancePlanProposalDTO proposal = new MaintenancePlanProposalDTO();
        proposal.setAssetId(asset.getId());
        proposal.setAssetName(asset.getName());

        // What already exists, so we propose additions rather than duplicates.
        Set<String> existingTitles = preventiveMaintenanceRepository.findByAsset_Id(asset.getId()).stream()
                .map(PreventiveMaintenance::getTitle)
                .filter(Objects::nonNull)
                .map(title -> title.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());
        proposal.setExistingPlanCount(existingTitles.size());

        // Usage tells us whether an interval is months away or next week.
        proposal.setUsage(usageSummary(asset));

        List<KnowledgeSearchResultDTO> chartExcerpts = knowledgeService.search(
                companyId,
                "maintenance schedule interval chart every hours lubrication service inspection",
                asset.getId(), asset.getEquipmentClass(), null, 12);

        Map<String, MaintenancePlanProposalDTO.ProposedPlan> byKey = new LinkedHashMap<>();
        for (KnowledgeSearchResultDTO excerpt : chartExcerpts) {
            if (excerpt.getContent() == null) continue;
            collectHourIntervals(excerpt, byKey);
            collectCalendarIntervals(excerpt, byKey);
        }

        // Consumables with a stated replacement interval are a maintenance plan
        // whether anyone wrote one or not.
        for (AssetBomLine line : assetBomService.findConsumables(asset.getId())) {
            String name = line.getPart() == null ? "Consumable" : line.getPart().getName();
            if (line.getReplaceIntervalHours() != null) {
                MaintenancePlanProposalDTO.ProposedPlan plan = byKey.computeIfAbsent(
                        "h:" + line.getReplaceIntervalHours().intValue(),
                        key -> newPlan("Every " + line.getReplaceIntervalHours().intValue() + " hours",
                                "METER", line.getReplaceIntervalHours(), "h"));
                plan.getTasks().add("Replace " + name
                        + (line.getPositionCode() == null ? "" : " (" + line.getPositionCode() + ")"));
                plan.getSources().add("Bill of materials");
            } else if (line.getReplaceIntervalMonths() != null) {
                MaintenancePlanProposalDTO.ProposedPlan plan = byKey.computeIfAbsent(
                        "m:" + line.getReplaceIntervalMonths(),
                        key -> newPlan("Every " + line.getReplaceIntervalMonths() + " months",
                                "CALENDAR", line.getReplaceIntervalMonths().doubleValue(), "months"));
                plan.getTasks().add("Replace " + name
                        + (line.getPositionCode() == null ? "" : " (" + line.getPositionCode() + ")"));
                plan.getSources().add("Bill of materials");
            }
        }

        List<MaintenancePlanProposalDTO.ProposedPlan> plans = byKey.values().stream()
                .filter(plan -> !plan.getTasks().isEmpty())
                .filter(plan -> !existingTitles.contains(plan.getTitle().toLowerCase(Locale.ROOT).trim()))
                .peek(plan -> plan.setEstimatedFirstDue(estimateFirstDue(plan, proposal.getUsage())))
                .collect(Collectors.toList());
        proposal.setPlans(plans);

        if (plans.isEmpty()) {
            proposal.setNote(chartExcerpts.isEmpty()
                    ? "No manual content is indexed for this machine, so there is nothing to propose from. "
                    + "Upload the maintenance manual first."
                    : "The indexed documents don't contain a recognisable interval chart. "
                    + "The intervals may need to be entered by hand.");
        } else {
            proposal.setNote("These are proposals drawn from the indexed manuals and the bill of materials. "
                    + "Review each one before creating it — nothing has been created.");
        }
        return proposal;
    }

    private MaintenancePlanProposalDTO.ProposedPlan newPlan(String title, String basis,
                                                            Double value, String unit) {
        MaintenancePlanProposalDTO.ProposedPlan plan = new MaintenancePlanProposalDTO.ProposedPlan();
        plan.setTitle(title);
        plan.setBasis(basis);
        plan.setIntervalValue(value);
        plan.setUnit(unit);
        plan.setTriggerMode("WHICHEVER_FIRST");
        return plan;
    }

    private void collectHourIntervals(KnowledgeSearchResultDTO excerpt,
                                      Map<String, MaintenancePlanProposalDTO.ProposedPlan> byKey) {
        Matcher matcher = HOUR_INTERVAL.matcher(excerpt.getContent());
        while (matcher.find()) {
            Double hours = parseNumber(matcher.group(1));
            if (hours == null || hours <= 0) continue;
            MaintenancePlanProposalDTO.ProposedPlan plan = byKey.computeIfAbsent("h:" + hours.intValue(),
                    key -> newPlan("Every " + hours.intValue() + " hours", "METER", hours, "h"));
            plan.getTasks().add(sentenceAround(excerpt.getContent(), matcher.start()));
            plan.getSources().add(excerpt.getCitation());
            plan.getReferences().add(excerpt);
        }
    }

    private void collectCalendarIntervals(KnowledgeSearchResultDTO excerpt,
                                          Map<String, MaintenancePlanProposalDTO.ProposedPlan> byKey) {
        Matcher matcher = CALENDAR_INTERVAL.matcher(excerpt.getContent());
        while (matcher.find()) {
            Double value = parseNumber(matcher.group(1));
            String unit = normaliseUnit(matcher.group(2));
            if (value == null || value <= 0 || unit == null) continue;
            MaintenancePlanProposalDTO.ProposedPlan plan = byKey.computeIfAbsent(
                    unit.charAt(0) + ":" + value.intValue(),
                    key -> newPlan("Every " + value.intValue() + " " + unit, "CALENDAR", value, unit));
            plan.getTasks().add(sentenceAround(excerpt.getContent(), matcher.start()));
            plan.getSources().add(excerpt.getCitation());
            plan.getReferences().add(excerpt);
        }
    }

    private String normaliseUnit(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("day") || lower.startsWith("día") || lower.startsWith("dia")) return "days";
        if (lower.startsWith("week") || lower.startsWith("semana")) return "weeks";
        if (lower.startsWith("month") || lower.startsWith("mes")) return "months";
        if (lower.startsWith("year") || lower.startsWith("año") || lower.startsWith("ano")) return "years";
        return null;
    }

    private Double parseNumber(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", "").replace(".", "").isEmpty()
                    ? raw : raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The sentence the interval appeared in — enough context for a human to
     * judge the proposal without opening the manual.
     */
    private String sentenceAround(String content, int index) {
        int start = content.lastIndexOf('\n', index);
        int stop = content.indexOf('\n', index);
        if (start < 0) start = Math.max(0, index - 160);
        if (stop < 0) stop = Math.min(content.length(), index + 160);
        return content.substring(start, stop).trim();
    }

    private MaintenancePlanProposalDTO.Usage usageSummary(Asset asset) {
        MaintenancePlanProposalDTO.Usage usage = new MaintenancePlanProposalDTO.Usage();
        for (Meter meter : meterService.findByAsset(asset.getId())) {
            List<Reading> readings = new ArrayList<>(readingRepository.findByMeter_Id(meter.getId()));
            if (readings.size() < 2) continue;
            readings.sort(Comparator.comparing(Reading::getCreatedAt));
            Reading first = readings.get(0);
            Reading last = readings.get(readings.size() - 1);
            double days = (last.getCreatedAt().getTime() - first.getCreatedAt().getTime())
                    / (1000d * 60 * 60 * 24);
            if (days < 1) continue;
            double perDay = (last.getValue() - first.getValue()) / days;
            if (perDay <= 0) continue;
            MaintenancePlanProposalDTO.MeterUsage meterUsage = new MaintenancePlanProposalDTO.MeterUsage();
            meterUsage.setMeterId(meter.getId());
            meterUsage.setName(meter.getName());
            meterUsage.setUnit(meter.getUnit());
            meterUsage.setCurrentValue(last.getValue());
            meterUsage.setPerDay(Math.round(perDay * 100) / 100.0);
            meterUsage.setPerWeek(Math.round(perDay * 7 * 10) / 10.0);
            usage.getMeters().add(meterUsage);
        }
        return usage;
    }

    /**
     * Turn "every 500 hours" into "about 9 weeks away at the current rate" —
     * the form a planner can act on.
     */
    private String estimateFirstDue(MaintenancePlanProposalDTO.ProposedPlan plan,
                                    MaintenancePlanProposalDTO.Usage usage) {
        if (!"METER".equals(plan.getBasis()) || plan.getIntervalValue() == null) {
            return null;
        }
        Optional<MaintenancePlanProposalDTO.MeterUsage> hourMeter = usage.getMeters().stream()
                .filter(meter -> meter.getUnit() != null
                        && meter.getUnit().toLowerCase(Locale.ROOT).startsWith("h"))
                .max(Comparator.comparingDouble(MaintenancePlanProposalDTO.MeterUsage::getPerDay));
        if (hourMeter.isEmpty() || hourMeter.get().getPerDay() <= 0) {
            return null;
        }
        double days = plan.getIntervalValue() / hourMeter.get().getPerDay();
        if (days < 14) {
            return "about " + Math.round(days) + " days at the current rate";
        }
        return "about " + Math.round(days / 7) + " weeks at the current rate";
    }
}
