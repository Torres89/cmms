package com.grash.controller;

import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.Company;
import com.grash.model.CompanySettings;
import com.grash.model.FaultEvent;
import com.grash.model.Meter;
import com.grash.model.Reading;
import com.grash.model.enums.SourceType;
import com.grash.repository.AssetRepository;
import com.grash.repository.FaultEventRepository;
import com.grash.repository.MeterRepository;
import com.grash.repository.MeterSourceRepository;
import com.grash.service.CompanyService;
import com.grash.service.ReadingService;
import com.grash.service.SecretEncryptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service-to-service endpoints. Not for browsers, not for users.
 * <p>
 * The agent needs a company's decrypted AI key in order to make Door 2 calls on
 * its behalf. That key is never returned by any user-facing endpoint, so it is
 * served here instead, behind a shared secret that is compared in constant time
 * and only ever reachable inside the compose network.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "internal")
@RequiredArgsConstructor
@Slf4j
public class InternalController {

    @Value("${internal.service-token:}")
    private String serviceToken;

    private final CompanyService companyService;
    private final SecretEncryptionService secretEncryptionService;
    private final MeterSourceRepository meterSourceRepository;
    private final MeterRepository meterRepository;
    private final AssetRepository assetRepository;
    private final FaultEventRepository faultEventRepository;
    private final ReadingService readingService;

    @GetMapping("/ai-config/{companyId}")
    @PreAuthorize("permitAll()")
    public Map<String, Object> aiConfig(@PathVariable("companyId") Long companyId,
                                        HttpServletRequest req) {
        authorize(req);
        Optional<Company> company = companyService.findById(companyId);
        if (company.isEmpty()) {
            throw new CustomException("Company not found", HttpStatus.NOT_FOUND);
        }
        CompanySettings settings = company.get().getCompanySettings();
        Map<String, Object> response = new LinkedHashMap<>();
        if (settings == null) {
            response.put("provider", "NONE");
            return response;
        }
        response.put("provider", settings.getAiProvider() == null ? "NONE" : settings.getAiProvider());
        response.put("model", settings.getAiModel());
        response.put("baseUrl", settings.getAiBaseUrl());
        response.put("monthlyTokenCap", settings.getAiMonthlyTokenCap());
        response.put("apiKey", secretEncryptionService.decrypt(settings.getAiApiKeyEncrypted()));
        return response;
    }

    /**
     * Every meter source the collector should be polling.
     */
    @GetMapping("/telemetry/sources")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> telemetrySources(HttpServletRequest req) {
        authorize(req);
        return meterSourceRepository.findByEnabledTrue().stream()
                .filter(source -> source.getSourceType() != SourceType.MANUAL)
                .map(source -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", source.getId());
                    entry.put("companyId", source.getCompany().getId());
                    entry.put("meterId", source.getMeter().getId());
                    entry.put("meterName", source.getMeter().getName());
                    entry.put("meterUnit", source.getMeter().getUnit());
                    entry.put("assetId", source.getMeter().getAsset() == null
                            ? null : source.getMeter().getAsset().getId());
                    entry.put("sourceType", source.getSourceType().name());
                    entry.put("config", source.getConfig());
                    entry.put("pollIntervalMinutes", source.getPollIntervalMinutes());
                    entry.put("lastSyncAt", source.getLastSyncAt());
                    return entry;
                })
                .collect(Collectors.toList());
    }

    /**
     * Post a reading gathered by a collector.
     * <p>
     * Deliberately goes through {@code ReadingService} rather than straight into
     * the table: that is what rolls the delta into every serialized component
     * installed under the asset and fires meter-based work order triggers.
     */
    @PostMapping("/telemetry/readings")
    @PreAuthorize("permitAll()")
    @Transactional
    public Map<String, Object> postReading(@RequestBody TelemetryReading payload,
                                           HttpServletRequest req) {
        authorize(req);
        Meter meter = meterRepository.findById(payload.getMeterId())
                .orElseThrow(() -> new CustomException("Meter not found", HttpStatus.NOT_FOUND));

        Reading reading = new Reading();
        reading.setMeter(meter);
        reading.setValue(payload.getValue());
        Reading saved = readingService.create(reading);

        meterSourceRepository.findByMeter_Id(meter.getId()).ifPresent(source -> {
            source.setLastSyncAt(new Date());
            source.setLastSyncError(null);
            meterSourceRepository.save(source);
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("value", saved.getValue());
        return response;
    }

    /**
     * Post a fault reported by a machine.
     * <p>
     * Repeats of a still-active fault are folded into the existing row rather
     * than piling up: a control that re-asserts the same alarm every poll would
     * otherwise bury the history it is supposed to build.
     */
    @PostMapping("/telemetry/faults")
    @PreAuthorize("permitAll()")
    @Transactional
    public Map<String, Object> postFault(@RequestBody TelemetryFault payload,
                                         HttpServletRequest req) {
        authorize(req);
        Asset asset = assetRepository.findById(payload.getAssetId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));

        Optional<FaultEvent> active = faultEventRepository
                .findFirstByAsset_IdAndCodeIgnoreCaseAndClearedAtIsNull(asset.getId(), payload.getCode());

        if (payload.isCleared()) {
            active.ifPresent(event -> {
                event.setClearedAt(payload.getOccurredAt() == null ? new Date() : payload.getOccurredAt());
                faultEventRepository.save(event);
            });
            return Map.of("cleared", active.isPresent());
        }

        if (active.isPresent()) {
            return Map.of("id", active.get().getId(), "alreadyActive", true);
        }

        FaultEvent event = new FaultEvent();
        event.setCompany(asset.getCompany());
        event.setAsset(asset);
        event.setCode(payload.getCode());
        event.setDescription(payload.getDescription());
        event.setSeverity(payload.getSeverity());
        event.setOccurredAt(payload.getOccurredAt() == null ? new Date() : payload.getOccurredAt());
        event.setSource(payload.getSource() == null
                ? SourceType.WEBHOOK : SourceType.valueOf(payload.getSource()));
        event.setRawPayload(payload.getRawPayload());
        FaultEvent saved = faultEventRepository.save(event);
        return Map.of("id", saved.getId(), "alreadyActive", false);
    }

    /**
     * Record that a source failed, so the UI can show why data stopped arriving.
     */
    @PostMapping("/telemetry/sources/{id}/error")
    @PreAuthorize("permitAll()")
    public Map<String, Object> reportSourceError(@PathVariable("id") Long id,
                                                 @RequestBody Map<String, String> payload,
                                                 HttpServletRequest req) {
        authorize(req);
        meterSourceRepository.findById(id).ifPresent(source -> {
            source.setLastSyncError(payload.getOrDefault("error", "").substring(
                    0, Math.min(2000, payload.getOrDefault("error", "").length())));
            meterSourceRepository.save(source);
        });
        return Map.of("recorded", true);
    }

    public static class TelemetryReading {
        private Long meterId;
        private double value;

        public Long getMeterId() {
            return meterId;
        }

        public void setMeterId(Long meterId) {
            this.meterId = meterId;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }
    }

    public static class TelemetryFault {
        private Long assetId;
        private String code;
        private String description;
        private String severity;
        private Date occurredAt;
        private boolean cleared;
        private String source;
        private String rawPayload;

        public Long getAssetId() {
            return assetId;
        }

        public void setAssetId(Long assetId) {
            this.assetId = assetId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public Date getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(Date occurredAt) {
            this.occurredAt = occurredAt;
        }

        public boolean isCleared() {
            return cleared;
        }

        public void setCleared(boolean cleared) {
            this.cleared = cleared;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getRawPayload() {
            return rawPayload;
        }

        public void setRawPayload(String rawPayload) {
            this.rawPayload = rawPayload;
        }
    }

    private void authorize(HttpServletRequest req) {
        if (serviceToken == null || serviceToken.isBlank()) {
            // Not configured means not enabled. Failing closed is the only safe
            // default for an endpoint that hands out decrypted keys.
            throw new CustomException("Internal endpoints are disabled", HttpStatus.NOT_FOUND);
        }
        String provided = req.getHeader("X-Internal-Token");
        byte[] expectedBytes = serviceToken.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Rejected an internal request with a bad service token from {}", req.getRemoteAddr());
            throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
