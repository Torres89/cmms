package com.grash.service;

import com.grash.dto.IntervalStatusDTO;
import com.grash.model.MaintenanceInterval;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.WorkOrder;
import com.grash.repository.MaintenanceIntervalRepository;
import com.grash.repository.PreventiveMaintenanceRepository;
import com.grash.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates work orders for interval-driven preventive maintenance.
 * <p>
 * A PM with {@link MaintenanceInterval}s is due when its counters say so —
 * "500 hours or 3 months, whichever first" — not when a calendar trigger
 * fires. That is evaluated at the two moments it can change: when a reading
 * lands on a meter an interval counts against, and once a day for the calendar
 * counters that advance on their own.
 * <p>
 * One open work order per PM at a time. Readings keep arriving while a service
 * is waiting to be done, and each of them must not produce another copy of it;
 * the next cycle starts when that work order is completed and the counters are
 * reset from it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntervalMaintenanceService {

    private final MaintenanceIntervalRepository maintenanceIntervalRepository;
    private final PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MaintenanceIntervalService maintenanceIntervalService;
    private final ScheduleService scheduleService;
    private final PreventiveMaintenanceWorkOrderService preventiveMaintenanceWorkOrderService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Evaluate every PM counting against this meter, once the reading that
     * moved it is committed.
     * <p>
     * Deferred to after commit because a reading posted inside a transaction
     * (the telemetry endpoint) is not visible to anyone else until then, and
     * each PM is evaluated in a transaction of its own so a failure on one —
     * a work-order limit, say — never rolls back the reading.
     */
    public void onReading(Long meterId) {
        if (meterId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evaluateMeter(meterId);
                }
            });
        } else {
            evaluateMeter(meterId);
        }
    }

    /**
     * Evaluate the PMs with an interval on this meter.
     *
     * @return the work orders generated
     */
    public List<WorkOrder> evaluateMeter(Long meterId) {
        Set<Long> pmIds = maintenanceIntervalRepository.findByMeter_Id(meterId).stream()
                .map(MaintenanceInterval::getPreventiveMaintenance)
                .filter(Objects::nonNull)
                .map(PreventiveMaintenance::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return evaluateEach(pmIds);
    }

    /**
     * Evaluate every interval-driven PM, for the calendar counters that come
     * due with nobody entering anything.
     *
     * @return the work orders generated
     */
    public List<WorkOrder> evaluateAll() {
        return evaluateEach(maintenanceIntervalRepository.findDistinctPreventiveMaintenanceIds());
    }

    private List<WorkOrder> evaluateEach(Iterable<Long> pmIds) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<WorkOrder> generated = new ArrayList<>();
        for (Long pmId : pmIds) {
            if (pmId == null) continue;
            try {
                Optional<WorkOrder> workOrder = transaction.execute(status -> evaluate(pmId));
                if (workOrder != null) workOrder.ifPresent(generated::add);
            } catch (Exception e) {
                log.warn("Could not evaluate interval-driven PM {}: {}", pmId, e.getMessage());
            }
        }
        return generated;
    }

    /**
     * Generate this PM's work order if its counters say it is due and nobody
     * is already working on one.
     */
    Optional<WorkOrder> evaluate(Long pmId) {
        PreventiveMaintenance pm = preventiveMaintenanceRepository.findById(pmId).orElse(null);
        if (pm == null || maintenanceIntervalRepository.findByPreventiveMaintenance_Id(pmId).isEmpty()) {
            return Optional.empty();
        }
        // PMs created outside PreventiveMaintenanceService (a pack, an import)
        // still carry the default "every 1 day" schedule. Retire it so nothing
        // treats it as live.
        if (pm.getSchedule() != null && !pm.getSchedule().isDisabled()) {
            scheduleService.retireForIntervals(pm.getSchedule());
        }
        IntervalStatusDTO status = maintenanceIntervalService.status(pm);
        if (!status.isDue()) {
            return Optional.empty();
        }
        if (workOrderRepository.hasOpenFromPreventiveMaintenance(pmId)) {
            return Optional.empty();
        }
        WorkOrder workOrder = preventiveMaintenanceWorkOrderService.generate(pm);
        log.info("PM {} is due on {} ({}%); generated work order {}", pmId,
                status.getDrivingCounter(), Math.round(status.getPercent() == null ? 0 : status.getPercent()),
                workOrder.getId());
        return Optional.of(workOrder);
    }
}
