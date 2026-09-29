package com.grash.service;

import com.grash.dto.IntervalStatusDTO;
import com.grash.model.MaintenanceInterval;
import com.grash.model.Meter;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.Reading;
import com.grash.model.enums.IntervalBasis;
import com.grash.model.enums.TriggerMode;
import com.grash.repository.MaintenanceIntervalRepository;
import com.grash.repository.PreventiveMaintenanceRepository;
import com.grash.repository.ReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceIntervalServiceBaselineTest {

    private static final long DAY = 24L * 3600 * 1000;
    private static final long METER_ID = 5L;
    private static final long PM_ID = 9L;

    @Mock
    private MaintenanceIntervalRepository maintenanceIntervalRepository;
    @Mock
    private ReadingRepository readingRepository;
    @Mock
    private PreventiveMaintenanceRepository preventiveMaintenanceRepository;

    private MaintenanceIntervalService service;
    private Meter meter;
    private PreventiveMaintenance pm;
    private final long now = System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        service = new MaintenanceIntervalService(maintenanceIntervalRepository, readingRepository,
                preventiveMaintenanceRepository);
        meter = new Meter();
        meter.setId(METER_ID);
        meter.setName("Spindle hours");
        meter.setUnit("h");
        pm = new PreventiveMaintenance();
        pm.setId(PM_ID);
        pm.setName("200-hour greasing");
        pm.setTriggerMode(TriggerMode.WHICHEVER_FIRST);
    }

    private Reading reading(long id, double value, long at) {
        Reading reading = new Reading();
        reading.setId(id);
        reading.setValue(value);
        reading.setCreatedAt(new Date(at));
        return reading;
    }

    private MaintenanceInterval meterInterval(double every, Double lastValue, long createdAt) {
        MaintenanceInterval interval = new MaintenanceInterval();
        interval.setId(1L);
        interval.setBasis(IntervalBasis.METER);
        interval.setMeter(meter);
        interval.setIntervalValue(every);
        interval.setUnit("h");
        interval.setLastCompletedValue(lastValue);
        interval.setCreatedAt(new Date(createdAt));
        return interval;
    }

    @Test
    void packOnAnOldMachineStartsCountingFromTheCurrentReading() {
        // The pack created the meter with no readings and wrote 0 as the baseline;
        // the commissioning engineer then entered today's counter.
        MaintenanceInterval interval = meterInterval(200, 0d, now - 2 * DAY);
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));
        when(readingRepository.findByMeter_Id(METER_ID))
                .thenReturn(List.of(reading(1, 11840, now - DAY)));

        IntervalStatusDTO status = service.status(pm);

        assertEquals(0d, status.getPercent());
        assertFalse(status.isDue());
        assertEquals(11840d, interval.getLastCompletedValue(), "baseline is persisted");
        verify(maintenanceIntervalRepository).save(interval);
    }

    @Test
    void progressIsMeasuredFromTheBaselineOnceSet() {
        MaintenanceInterval interval = meterInterval(200, null, now - 3 * DAY);
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(
                reading(1, 11700, now - 10 * DAY),
                reading(2, 11840, now - 4 * DAY),   // the value when the interval was created
                reading(3, 11990, now - DAY)));

        IntervalStatusDTO status = service.status(pm);

        assertEquals(75d, status.getPercent(), 0.001);
        assertEquals(11840d, interval.getLastCompletedValue());
        assertEquals(50d, status.getRemaining(), 0.001);
    }

    @Test
    void noReadingsMeansNotStarted() {
        MaintenanceInterval interval = meterInterval(500, null, now - DAY);
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(Collections.emptyList());

        IntervalStatusDTO status = service.status(pm);

        assertEquals(0d, status.getPercent());
        assertFalse(status.isDue());
        verify(maintenanceIntervalRepository, never()).save(any());
    }

    @Test
    void firstReadingAfterCreationBecomesTheBaseline() {
        MaintenanceInterval interval = meterInterval(500, null, now - 5 * DAY);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(
                reading(1, 3000, now - 4 * DAY),
                reading(2, 3100, now - DAY)));

        Optional<Double> baseline = service.meterBaseline(interval,
                List.of(reading(1, 3000, now - 4 * DAY), reading(2, 3100, now - DAY)));

        assertEquals(Optional.of(3000d), baseline);
    }

    @Test
    void aStoredCompletionValueIsTrusted() {
        MaintenanceInterval interval = meterInterval(500, 2600d, now - 50 * DAY);

        Optional<Double> baseline = service.meterBaseline(interval, List.of(reading(1, 3000, now - DAY)));

        assertEquals(Optional.of(2600d), baseline);
        verify(maintenanceIntervalRepository, never()).save(any());
    }

    @Test
    void readingPastTheIntervalMakesItDue() {
        MaintenanceInterval interval = meterInterval(200, 11840d, now - 30 * DAY);
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(reading(1, 12050, now - DAY)));

        IntervalStatusDTO status = service.status(pm);

        assertTrue(status.isDue());
    }

    @Test
    void calendarIntervalNeverCompletedCountsFromCreation() {
        MaintenanceInterval interval = new MaintenanceInterval();
        interval.setId(2L);
        interval.setBasis(IntervalBasis.CALENDAR);
        interval.setIntervalValue(3d);
        interval.setUnit("months");
        interval.setCreatedAt(new Date(now - 100 * DAY));
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));

        IntervalStatusDTO status = service.status(pm);

        assertNotNull(status.getPercent());
        assertTrue(status.isDue(), "100 days is past 3 months");
    }

    @Test
    void recordCompletionIsIdempotentOnTheCompletionTime() {
        MaintenanceInterval interval = meterInterval(200, 11840d, now - 30 * DAY);
        interval.setLastCompletedAt(new Date(now - 30 * DAY));
        when(maintenanceIntervalRepository.findByPreventiveMaintenance_Id(PM_ID)).thenReturn(List.of(interval));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(reading(1, 12050, now - DAY)));

        Date completedOn = new Date(now - 1000);
        assertTrue(service.recordCompletion(PM_ID, completedOn));
        assertEquals(12050d, interval.getLastCompletedValue());
        assertEquals(completedOn, interval.getLastCompletedAt());

        // Saving the same completed work order again changes nothing.
        assertFalse(service.recordCompletion(PM_ID, completedOn));
    }
}
