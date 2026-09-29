package com.grash.job;

import com.grash.model.PreventiveMaintenance;
import com.grash.model.Schedule;
import com.grash.repository.ScheduleRepository;
import com.grash.service.PreventiveMaintenanceWorkOrderService;
import com.grash.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderCreationJob extends QuartzJobBean {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;
    private final PreventiveMaintenanceWorkOrderService preventiveMaintenanceWorkOrderService;

    @Override
    @Transactional
    public void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getMergedJobDataMap().getLong("scheduleId");

        // We fetch fresh data from DB to ensure validity
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || schedule.isDisabled()) {
            return;
        }
        scheduleService.checkIfWeeklyShouldRun(schedule);

        PreventiveMaintenance preventiveMaintenance = schedule.getPreventiveMaintenance();

        // A trigger that predates the PM getting intervals: its counters own it
        // now, so retire the trigger instead of generating a daily work order.
        if (scheduleService.isIntervalDriven(preventiveMaintenance)) {
            scheduleService.retireForIntervals(schedule);
            return;
        }

        preventiveMaintenanceWorkOrderService.generate(preventiveMaintenance);

//        log.info("Generated Work Order for Schedule ID: {}", scheduleId);
    }
}
