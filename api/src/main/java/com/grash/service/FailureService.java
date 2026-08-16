package com.grash.service;

import com.grash.dto.FailureParetoDTO;
import com.grash.model.Asset;
import com.grash.model.FailureEvent;
import com.grash.model.FailureMode;
import com.grash.repository.FailureEventRepository;
import com.grash.repository.FailureModeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The failure taxonomy and the events recorded against it.
 * <p>
 * The payoff is the moment where the system can say "this machine has had this
 * exact failure twice, both traced to the way-lube line, and here is what was
 * done" — which needs codes, not prose.
 */
@Service
@RequiredArgsConstructor
public class FailureService {

    private final FailureModeRepository failureModeRepository;
    private final FailureEventRepository failureEventRepository;

    // --- catalogue ------------------------------------------------------

    public List<FailureMode> findModesForClass(String equipmentClass, Long companyId) {
        return failureModeRepository.findByEquipmentClassAndCompany_Id(equipmentClass, companyId);
    }

    public List<FailureMode> findModesForSubunit(String equipmentClass, String subunit, Long companyId) {
        return failureModeRepository.findByEquipmentClassAndSubunitAndCompany_Id(
                equipmentClass, subunit, companyId);
    }

    public Optional<FailureMode> findModeByCode(String code, Long companyId) {
        return failureModeRepository.findByCodeAndCompany_Id(code, companyId);
    }

    public Optional<FailureMode> findModeById(Long id) {
        return failureModeRepository.findById(id);
    }

    public FailureMode saveMode(FailureMode mode) {
        return failureModeRepository.save(mode);
    }

    public void deleteMode(Long id) {
        failureModeRepository.deleteById(id);
    }

    public List<FailureMode> findModesByCompany(Long companyId) {
        return failureModeRepository.findByCompany_Id(companyId);
    }

    /**
     * Candidate failure modes for a machine, ranked by what has actually
     * happened to <em>this</em> machine before, then by catalogue severity.
     * <p>
     * That ranking is the whole difference between a dropdown a technician
     * scrolls past and one where the right answer is already near the top.
     */
    public List<FailureMode> rankedCandidates(Asset asset, String subunit, Long companyId) {
        if (asset.getEquipmentClass() == null) {
            return Collections.emptyList();
        }
        List<FailureMode> modes = subunit == null
                ? findModesForClass(asset.getEquipmentClass(), companyId)
                : findModesForSubunit(asset.getEquipmentClass(), subunit, companyId);

        Map<Long, Long> seenHere = failureEventRepository.countByFailureModeForAsset(asset.getId()).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a));

        return modes.stream()
                .sorted(Comparator
                        .comparingLong((FailureMode m) -> -seenHere.getOrDefault(m.getId(), 0L))
                        .thenComparing(m -> -(m.getSeverityDefault() == null ? 0 : m.getSeverityDefault()))
                        .thenComparing(FailureMode::getNameEn, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    // --- events ---------------------------------------------------------

    public FailureEvent record(FailureEvent event) {
        if (event.getOccurredAt() == null) {
            // A failure recorded without a date happened now. Leaving it null
            // would push the decision onto every reader.
            event.setOccurredAt(new Date());
        }
        return failureEventRepository.save(event);
    }

    /**
     * When a failure happened: the recorded date if there is one, otherwise the
     * date the row was written. Every read path goes through here so that rows
     * predating the occurredAt column keep behaving as they always did.
     */
    public static Date when(FailureEvent event) {
        return event.getOccurredAt() != null ? event.getOccurredAt() : event.getCreatedAt();
    }

    public Optional<FailureEvent> findEventById(Long id) {
        return failureEventRepository.findById(id);
    }

    /** Most recent first, by when the failure happened rather than when it was typed. */
    public List<FailureEvent> findEventsForAsset(Long assetId) {
        return failureEventRepository.findByAsset_IdOrderByCreatedAtDesc(assetId).stream()
                .sorted(Comparator.comparing(FailureService::when,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<FailureEvent> findEventsForAssetSince(Long assetId, Date since) {
        if (since == null) {
            return findEventsForAsset(assetId);
        }
        // Filtered in memory rather than by the repository: a back-filled event
        // can have an occurredAt older than its createdAt, so the database
        // predicate on createdAt would return rows the caller did not ask for.
        return findEventsForAsset(assetId).stream()
                .filter(event -> {
                    Date at = when(event);
                    return at != null && at.after(since);
                })
                .collect(Collectors.toList());
    }

    public List<FailureEvent> findEventsForComponent(Long componentId) {
        return failureEventRepository.findByComponent_IdOrderByCreatedAtDesc(componentId);
    }

    public List<FailureEvent> findEventsForWorkOrder(Long workOrderId) {
        return failureEventRepository.findByWorkOrder_Id(workOrderId);
    }

    public void deleteEvent(Long id) {
        failureEventRepository.deleteById(id);
    }

    // --- analytics ------------------------------------------------------

    /**
     * A Pareto of downtime causes for one machine, with MTBF per failure mode.
     */
    public List<FailureParetoDTO> pareto(Long assetId) {
        List<Object[]> rows = failureEventRepository.summariseByFailureMode(assetId);
        List<FailureEvent> events = findEventsForAsset(assetId);

        Map<String, List<Date>> occurrences = events.stream()
                .filter(e -> e.getFailureMode() != null && when(e) != null)
                .collect(Collectors.groupingBy(
                        e -> e.getFailureMode().getCode(),
                        Collectors.mapping(FailureService::when, Collectors.toList())));

        return rows.stream().map(row -> {
            FailureParetoDTO dto = new FailureParetoDTO();
            dto.setCode((String) row[0]);
            dto.setName((String) row[1]);
            dto.setCount(((Number) row[2]).longValue());
            dto.setDowntimeMinutes(((Number) row[3]).longValue());
            dto.setRepairCost(((Number) row[4]).doubleValue());
            dto.setMtbfDays(meanTimeBetween(occurrences.get(dto.getCode())));
            dto.setMttrMinutes(dto.getCount() == 0 ? null : (double) dto.getDowntimeMinutes() / dto.getCount());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Mean days between occurrences. Needs at least two of them — one failure
     * tells you nothing about an interval, and reporting a number anyway would
     * be worse than reporting none.
     */
    private Double meanTimeBetween(List<Date> dates) {
        if (dates == null || dates.size() < 2) {
            return null;
        }
        List<Date> sorted = dates.stream().sorted().collect(Collectors.toList());
        long spanMillis = sorted.get(sorted.size() - 1).getTime() - sorted.get(0).getTime();
        return spanMillis / (1000d * 60 * 60 * 24) / (sorted.size() - 1);
    }
}
