package com.grash.service;

import com.grash.dto.RestockKitDTO;
import com.grash.model.Asset;
import com.grash.model.AssetBomLine;
import com.grash.model.Meter;
import com.grash.model.Part;
import com.grash.model.Reading;
import com.grash.repository.AssetRepository;
import com.grash.repository.PartConsumptionRepository;
import com.grash.repository.PartQuantityRepository;
import com.grash.repository.ReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestockServiceDueDateTest {

    private static final long DAY = 24L * 3600 * 1000;

    @Mock
    private AssetBomService assetBomService;
    @Mock
    private PartSourcingService partSourcingService;
    @Mock
    private MeterService meterService;
    @Mock
    private ReadingRepository readingRepository;
    @Mock
    private PartConsumptionRepository partConsumptionRepository;
    @Mock
    private PartQuantityRepository partQuantityRepository;
    @Mock
    private AssetRepository assetRepository;

    private RestockService service;
    private long now;
    private Asset machine;
    private Part filter;

    @BeforeEach
    void setUp() {
        service = new RestockService(assetBomService, partSourcingService, meterService, readingRepository,
                partConsumptionRepository, partQuantityRepository, assetRepository);
        now = 1_760_000_000_000L;
        service.setClock(Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()));

        machine = new Asset();
        machine.setId(1L);
        machine.setName("VF-2");
        filter = new Part();
        filter.setId(20L);
        filter.setName("Coolant filter");
        filter.setQuantity(0);

        when(assetRepository.findByParentAsset_Id(anyLong(), any())).thenReturn(Collections.emptyList());
        when(partSourcingService.findPreferredSupplier(anyLong())).thenReturn(Optional.empty());
        when(partSourcingService.findSuppliers(anyLong())).thenReturn(Collections.emptyList());
        when(meterService.findByAsset(1L)).thenReturn(Collections.emptyList());
    }

    private AssetBomLine monthlyLine(int months, long documentedAt) {
        AssetBomLine line = new AssetBomLine();
        line.setId(100L);
        line.setAsset(machine);
        line.setPart(filter);
        line.setConsumable(true);
        line.setReplaceIntervalMonths(months);
        line.setCreatedAt(new Date(documentedAt));
        return line;
    }

    @Test
    void recentlyReplacedIsNotDueForAlmostTheWholeInterval() {
        AssetBomLine line = monthlyLine(3, now - 400 * DAY);
        when(partQuantityRepository.findLastCompletedUse(eq(20L), any())).thenReturn(new Date(now - DAY));

        Date last = service.lastReplacement(line, machine, List.of(1L));
        Integer days = service.daysUntilDue(line, last, Collections.emptyList(), 0);

        assertEquals(new Date(now - DAY), last);
        assertTrue(days >= 88 && days <= 91, "about three months minus a day, was " + days);
    }

    @Test
    void neverReplacedCountsFromWhenTheLineWasDocumented() {
        AssetBomLine line = monthlyLine(3, now - 200 * DAY);
        when(partQuantityRepository.findLastCompletedUse(eq(20L), any())).thenReturn(null);

        Date last = service.lastReplacement(line, machine, List.of(1L));
        Integer days = service.daysUntilDue(line, last, Collections.emptyList(), 0);

        assertEquals(new Date(now - 200 * DAY), last);
        assertTrue(days < 0, "overdue, was " + days);
    }

    @Test
    void anOlderInServiceDateWinsOverTheDocumentationDate() {
        AssetBomLine line = monthlyLine(12, now - 10 * DAY);
        machine.setInServiceDate(new Date(now - 800 * DAY));
        when(partQuantityRepository.findLastCompletedUse(eq(20L), any())).thenReturn(null);

        assertEquals(new Date(now - 800 * DAY), service.lastReplacement(line, machine, List.of(1L)));
    }

    @Test
    void hourIntervalUsesHoursRunSinceTheReplacement() {
        AssetBomLine line = new AssetBomLine();
        line.setPart(filter);
        line.setReplaceIntervalHours(500d);
        List<Reading> readings = List.of(
                reading(1, 10_000, now - 40 * DAY),   // at the replacement
                reading(2, 10_320, now - DAY));
        // 320 h used, 180 h left at 8 h/day
        Integer days = service.daysUntilDue(line, new Date(now - 40 * DAY), readings, 8);
        assertEquals(22, days);

        // 520 h used: overdue
        List<Reading> overdue = List.of(reading(1, 10_000, now - 60 * DAY), reading(2, 10_520, now - DAY));
        assertTrue(service.daysUntilDue(line, new Date(now - 60 * DAY), overdue, 8) < 0);
    }

    @Test
    void overdueConsumableWithALongIntervalAppearsInTheKitAsUrgent() {
        // A 12-month filter last changed 13 months ago, with a 30-day horizon.
        AssetBomLine line = monthlyLine(12, now - 800 * DAY);
        when(assetBomService.findConsumables(1L)).thenReturn(List.of(line));
        when(partQuantityRepository.findLastCompletedUse(eq(20L), any())).thenReturn(new Date(now - 395 * DAY));

        RestockKitDTO kit = service.kitFor(machine, 30);

        assertEquals(1, kit.getLines().size());
        RestockKitDTO.KitLine kitLine = kit.getLines().get(0);
        assertTrue(kitLine.getDaysUntilDue() < 0);
        assertTrue(kitLine.isUrgent());
    }

    @Test
    void shortIntervalReplacedYesterdayIsNotInTheKit() {
        AssetBomLine line = monthlyLine(1, now - 800 * DAY);
        when(assetBomService.findConsumables(1L)).thenReturn(List.of(line));
        when(partQuantityRepository.findLastCompletedUse(eq(20L), any())).thenReturn(new Date(now - DAY));

        RestockKitDTO kit = service.kitFor(machine, 14);

        assertTrue(kit.getLines().isEmpty());
    }

    @Test
    void theHoursMeterIsTheUsageBasisOne() {
        Meter idle = new Meter();
        idle.setId(2L);
        idle.setUnit("h");
        Meter spindle = new Meter();
        spindle.setId(3L);
        spindle.setUnit("h");
        spindle.setUsageBasis(true);
        when(meterService.findByAsset(1L)).thenReturn(List.of(idle, spindle));
        when(readingRepository.findByMeter_Id(3L)).thenReturn(List.of(
                reading(1, 1000, now - 10 * DAY), reading(2, 1080, now)));
        when(readingRepository.findByMeter_Id(2L)).thenReturn(List.of(
                reading(3, 1000, now - 10 * DAY), reading(4, 1500, now)));
        when(assetBomService.findConsumables(1L)).thenReturn(Collections.emptyList());

        assertEquals(8d, service.kitFor(machine, 30).getHoursPerDay(), 0.001);
    }

    private Reading reading(long id, double value, long at) {
        Reading reading = new Reading();
        reading.setId(id);
        reading.setValue(value);
        reading.setCreatedAt(new Date(at));
        return reading;
    }
}
