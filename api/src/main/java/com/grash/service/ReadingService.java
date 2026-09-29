package com.grash.service;

import com.grash.dto.ReadingPatchDTO;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.exception.CustomException;
import com.grash.mapper.ReadingMapper;
import com.grash.model.OwnUser;
import com.grash.model.Reading;
import com.grash.model.enums.RoleType;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingService {
    private final ReadingRepository readingRepository;
    private final ReadingMapper readingMapper;
    private final LicenseService licenseService;
    private MeterService meterService;
    private ComponentService componentService;
    private IntervalMaintenanceService intervalMaintenanceService;

    @Autowired
    public void setDeps(@Lazy MeterService meterService, @Lazy ComponentService componentService,
                        @Lazy IntervalMaintenanceService intervalMaintenanceService
    ) {
        this.meterService = meterService;
        this.componentService = componentService;
        this.intervalMaintenanceService = intervalMaintenanceService;
    }

    public Reading create(Reading reading) {
        Reading saved = readingRepository.save(reading);
        // Every reading advances the counters of whatever serialized components
        // are installed at or under this meter's asset. Without this, remaining
        // life on a spindle cartridge is a number nobody maintains.
        componentService.applyReading(saved);
        // ...and may be the one that brings an hour-based PM due, which is when
        // its work order should appear — not at the next calendar tick.
        if (saved.getMeter() != null) {
            try {
                intervalMaintenanceService.onReading(saved.getMeter().getId());
            } catch (Exception e) {
                log.warn("Could not evaluate interval PMs for reading {}: {}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    public Reading update(Long id, ReadingPatchDTO reading) {
        if (readingRepository.existsById(id)) {
            Reading savedReading = readingRepository.findById(id).get();
            return readingRepository.save(readingMapper.updateReading(savedReading, reading));
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public Collection<Reading> getAll() {
        return readingRepository.findAll();
    }

    public void delete(Long id) {
        readingRepository.deleteById(id);
    }

    public Optional<Reading> findById(Long id) {
        return readingRepository.findById(id);
    }

    public Collection<Reading> findByCompany(Long id) {
        return readingRepository.findByCompany_Id(id);
    }

    public Collection<Reading> findByMeter(Long id) {
        return readingRepository.findByMeter_Id(id);
    }
}
