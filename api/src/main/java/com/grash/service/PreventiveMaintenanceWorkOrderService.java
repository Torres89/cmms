package com.grash.service;

import com.grash.model.PreventiveMaintenance;
import com.grash.model.Schedule;
import com.grash.model.Task;
import com.grash.model.WorkOrder;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;

/**
 * Turns a preventive maintenance into a work order.
 * <p>
 * One implementation for both ways a PM comes due — its calendar schedule
 * firing, or one of its counters crossing its interval — so a work order looks
 * the same whichever of them produced it: same assignments, same tasks, same
 * due-date delay.
 */
@Service
@RequiredArgsConstructor
public class PreventiveMaintenanceWorkOrderService {

    private final WorkOrderService workOrderService;
    private final TaskService taskService;

    @Transactional
    public WorkOrder generate(PreventiveMaintenance preventiveMaintenance) {
        WorkOrder workOrder = workOrderService.getWorkOrderFromWorkOrderBase(preventiveMaintenance);
        Collection<Task> tasks = taskService.findByPreventiveMaintenance(preventiveMaintenance.getId());
        workOrder.setParentPreventiveMaintenance(preventiveMaintenance);

        Schedule schedule = preventiveMaintenance.getSchedule();
        if (schedule != null && schedule.getDueDateDelay() != null) {
            workOrder.setDueDate(Helper.incrementDays(new Date(), schedule.getDueDateDelay()));
        }

        WorkOrder savedWorkOrder = workOrderService.create(workOrder, preventiveMaintenance.getCompany());

        tasks.forEach(task -> {
            Task copiedTask = new Task(task.getTaskBase(), savedWorkOrder, null, task.getValue());
            copiedTask.setCompany(preventiveMaintenance.getCompany());
            taskService.create(copiedTask);
        });
        return savedWorkOrder;
    }
}
