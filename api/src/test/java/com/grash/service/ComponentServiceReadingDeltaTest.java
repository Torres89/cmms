package com.grash.service;

import com.grash.model.Asset;
import com.grash.model.ComponentEvent;
import com.grash.model.ComponentInstance;
import com.grash.model.Meter;
import com.grash.model.Reading;
import com.grash.model.enums.ComponentEventType;
import com.grash.repository.ComponentEventRepository;
import com.grash.repository.ComponentInstanceRepository;
import com.grash.repository.MeterRepository;
import com.grash.repository.ReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComponentServiceReadingDeltaTest {

    private static final long METER_ID = 7L;

    @Mock
    private ComponentInstanceRepository componentInstanceRepository;
    @Mock
    private ComponentEventRepository componentEventRepository;
    @Mock
    private ReadingRepository readingRepository;
    @Mock
    private MeterRepository meterRepository;

    private ComponentService service;
    private long clock;

    @BeforeEach
    void setUp() {
        service = new ComponentService(componentInstanceRepository, componentEventRepository,
                readingRepository, meterRepository);
        clock = 1_700_000_000_000L;
    }

    private Reading reading(long id, double value) {
        Reading reading = new Reading();
        reading.setId(id);
        reading.setValue(value);
        clock += 3_600_000L;
        reading.setCreatedAt(new Date(clock));
        return reading;
    }

    @Test
    void firstReadingIsABaselineNotUsage() {
        Reading first = reading(1, 1500);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(first));

        assertEquals(0d, service.deltaSincePreviousReading(first, METER_ID));
    }

    @Test
    void deltaIsAgainstTheChronologicallyLatestReading() {
        Reading r1 = reading(1, 1000);
        Reading r2 = reading(2, 1200);
        Reading correction = reading(3, 1100);
        Reading next = reading(4, 1150);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(r1, r2, correction, next));

        // Not max(<= 1150) = 1100 by accident of values: the previous reading in time.
        assertEquals(50d, service.deltaSincePreviousReading(next, METER_ID));
    }

    @Test
    void aCorrectionDownwardsCreditsNothingAndBecomesTheBaseline() {
        Reading r1 = reading(1, 1000);
        Reading r2 = reading(2, 1200);
        Reading correction = reading(3, 1100);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(r1, r2, correction));
        assertEquals(0d, service.deltaSincePreviousReading(correction, METER_ID));

        Reading after = reading(4, 1210);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(r1, r2, correction, after));
        // 110 h since the corrected 1100, not 10 h since the stale 1200.
        assertEquals(110d, service.deltaSincePreviousReading(after, METER_ID));
    }

    @Test
    void laterReadingsAreIgnoredWhenReplayingAnOlderOne() {
        Reading r1 = reading(1, 100);
        Reading r2 = reading(2, 150);
        Reading r3 = reading(3, 400);
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(r1, r2, r3));

        assertEquals(50d, service.deltaSincePreviousReading(r2, METER_ID));
    }

    @Test
    void firstReadingDoesNotAgeANewlyInstalledComponent() {
        Asset machine = new Asset();
        machine.setId(3L);
        Meter meter = new Meter();
        meter.setId(METER_ID);
        meter.setUnit("h");
        meter.setName("Spindle hours");
        meter.setAsset(machine);
        Reading first = reading(1, 1500);
        first.setMeter(meter);

        when(meterRepository.findById(METER_ID)).thenReturn(Optional.of(meter));
        when(meterRepository.findByAsset_Id(3L)).thenReturn(List.of(meter));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(first));
        ComponentInstance spindle = new ComponentInstance();
        spindle.setId(11L);
        spindle.setTotalHours(0d);
        when(componentInstanceRepository.findInstalledInSubtree(3L)).thenReturn(List.of(spindle));
        // Installed with no meter value: nothing to measure the first reading against.
        when(componentEventRepository.findFirstByComponent_IdAndTypeOrderByOccurredAtDesc(11L,
                ComponentEventType.INSTALLED)).thenReturn(Optional.empty());

        service.applyReading(first);

        assertEquals(0d, spindle.getTotalHours());
    }

    @Test
    void secondReadingRollsTheIncreaseIntoInstalledComponents() {
        Asset machine = new Asset();
        machine.setId(3L);
        Meter meter = new Meter();
        meter.setId(METER_ID);
        meter.setUnit("h");
        meter.setAsset(machine);
        Reading first = reading(1, 1500);
        Reading second = reading(2, 1540);
        second.setMeter(meter);
        ComponentInstance spindle = new ComponentInstance();
        spindle.setTotalHours(0d);

        when(meterRepository.findById(METER_ID)).thenReturn(Optional.of(meter));
        when(meterRepository.findByAsset_Id(3L)).thenReturn(List.of(meter));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(first, second));
        when(componentInstanceRepository.findInstalledInSubtree(3L)).thenReturn(List.of(spindle));

        service.applyReading(second);

        assertEquals(40d, spindle.getTotalHours());
        assertEquals(40d, spindle.getHoursSinceOverhaul());
    }

    @Test
    void firstReadingCreditsOnlyUsageSinceAnInstallWithAMeterValue() {
        Asset machine = new Asset();
        machine.setId(3L);
        Meter meter = new Meter();
        meter.setId(METER_ID);
        meter.setUnit("h");
        meter.setAsset(machine);
        Reading first = reading(1, 5010);
        first.setMeter(meter);
        ComponentInstance spindle = new ComponentInstance();
        spindle.setId(11L);
        spindle.setTotalHours(0d);
        ComponentEvent install = new ComponentEvent();
        install.setPositionMeterValue(5000d);

        when(meterRepository.findById(METER_ID)).thenReturn(Optional.of(meter));
        when(meterRepository.findByAsset_Id(3L)).thenReturn(List.of(meter));
        when(readingRepository.findByMeter_Id(METER_ID)).thenReturn(List.of(first));
        when(componentInstanceRepository.findInstalledInSubtree(3L)).thenReturn(List.of(spindle));
        when(componentEventRepository.findFirstByComponent_IdAndTypeOrderByOccurredAtDesc(11L,
                ComponentEventType.INSTALLED)).thenReturn(Optional.of(install));

        service.applyReading(first);

        assertEquals(10d, spindle.getTotalHours());
        assertEquals(10d, spindle.getHoursSinceOverhaul());
    }
}
