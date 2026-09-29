package com.grash.job;

import com.grash.model.WorkOrder;
import com.grash.service.IntervalMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily pass over every interval-driven PM.
 * <p>
 * Meter counters are evaluated the moment a reading lands; this catches the
 * calendar ones ("or 3 months, whichever first"), which come due with nobody
 * entering anything. Each PM runs in its own transaction, so one failing never
 * stops the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class IntervalMaintenanceJob implements Job {

    private final IntervalMaintenanceService intervalMaintenanceService;

    @Override
    public void execute(JobExecutionContext context) {
        List<WorkOrder> generated = intervalMaintenanceService.evaluateAll();
        log.info("Interval maintenance pass generated {} work order(s)", generated.size());
    }
}
