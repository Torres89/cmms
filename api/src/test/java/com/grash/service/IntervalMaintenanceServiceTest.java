package com.grash.service;

import com.grash.dto.IntervalStatusDTO;
import com.grash.model.MaintenanceInterval;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.WorkOrder;
import com.grash.repository.MaintenanceIntervalRepository;
import com.grash.repository.PreventiveMaintenanceRepository;
import com.grash.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntervalMaintenanceServiceTest {

    private static final long PM_ID = 4L;

    @Mock
    private MaintenanceIntervalRepository maintenanceIntervalRepository;
    @Mock
    private PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private MaintenanceIntervalService maintenanceIntervalService;
    @Mock
    private ScheduleService scheduleService;
    @Mock
    private PreventiveMaintenanceWorkOrderService preventiveMaintenanceWorkOrderService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private IntervalMaintenanceService service;
    private PreventiveMaintenance pm;

    @BeforeEach
    void setUp() {
        service = new IntervalMaintenanceService(maintenanceIntervalRepository, preventiveMaintenanceRepository,
                workOrderRepository, maintenanceIntervalService, scheduleService,
                preventiveMaintenanceWorkOrderService, transactionManager);
        pm = new PreventiveMaintenance();
        pm.setId(PM_ID);
        when(preventiveMaintenanceRepository.findById(PM_ID)).thenReturn(Optional.of(pm));
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID))
                .thenReturn(List.of(new MaintenanceInterval()));
    }

    private void status(boolean due) {
        IntervalStatusDTO status = new IntervalStatusDTO();
        status.setDue(due);
        status.setPercent(due ? 104d : 40d);
        when(maintenanceIntervalService.status(pm)).thenReturn(status);
    }

    @Test
    void dueWithNoOpenWorkOrderGeneratesOne() {
        status(true);
        WorkOrder generated = new WorkOrder();
        when(workOrderRepository.hasOpenFromPreventiveMaintenance(PM_ID)).thenReturn(false);
        when(preventiveMaintenanceWorkOrderService.generate(pm)).thenReturn(generated);

        assertEquals(Optional.of(generated), service.evaluate(PM_ID));
    }

    @Test
    void dueButAlreadyOpenGeneratesNothing() {
        status(true);
        when(workOrderRepository.hasOpenFromPreventiveMaintenance(PM_ID)).thenReturn(true);

        assertTrue(service.evaluate(PM_ID).isEmpty());
        verify(preventiveMaintenanceWorkOrderService, never()).generate(any());
    }

    @Test
    void notDueGeneratesNothing() {
        status(false);

        assertTrue(service.evaluate(PM_ID).isEmpty());
        verify(preventiveMaintenanceWorkOrderService, never()).generate(any());
    }

    @Test
    void theDefaultDailyScheduleOfAPackPmIsRetired() {
        status(false);

        service.evaluate(PM_ID);

        verify(scheduleService).retireForIntervals(pm.getSchedule());
    }

    @Test
    void aPmWithoutIntervalsIsLeftToItsSchedule() {
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of());

        assertTrue(service.evaluate(PM_ID).isEmpty());
        verify(maintenanceIntervalService, never()).status(any());
        verify(scheduleService, never()).retireForIntervals(any());
    }
}
