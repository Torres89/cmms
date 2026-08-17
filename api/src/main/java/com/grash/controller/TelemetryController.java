package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.SourceType;
import com.grash.repository.FaultEventRepository;
import com.grash.repository.MeterSourceRepository;
import com.grash.service.AssetService;
import com.grash.service.MeterService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Meter sources and fault events.
 * <p>
 * Fault events can also be posted by hand, which matters: manual entry is the
 * path that always works, and a shop with no telemetry at all should still be
 * able to record "the machine threw SV0410 on Tuesday" and get it into the
 * history the diagnostics read from.
 */
@RestController
@Tag(name = "telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final MeterSourceRepository meterSourceRepository;
    private final FaultEventRepository faultEventRepository;
    private final MeterService meterService;
    private final AssetService assetService;
    private final UserService userService;

    // --- meter sources --------------------------------------------------

    @GetMapping("/meter-sources")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<MeterSource> list(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return meterSourceRepository.findByCompany_Id(user.getCompany().getId());
    }

    @GetMapping("/meters/{id}/source")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public MeterSource forMeter(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireMeter(id, user);
        return meterSourceRepository.findByMeter_Id(id).orElseGet(() -> {
            // No row means manual entry, which is a real answer rather than a
            // 404 the UI has to special-case.
            MeterSource implicit = new MeterSource();
            implicit.setSourceType(SourceType.MANUAL);
            return implicit;
        });
    }

    @PostMapping("/meter-sources")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public MeterSource createSource(@Valid @RequestBody MeterSource source, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        if (source.getMeter() == null) {
            throw new CustomException("A meter source must reference a meter", HttpStatus.BAD_REQUEST);
        }
        requireMeter(source.getMeter().getId(), user);
        meterSourceRepository.findByMeter_Id(source.getMeter().getId())
                .ifPresent(existing -> source.setId(existing.getId()));
        return meterSourceRepository.save(source);
    }

    @DeleteMapping("/meter-sources/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> deleteSource(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        MeterSource source = meterSourceRepository.findById(id)
                .orElseThrow(() -> new CustomException("Meter source not found", HttpStatus.NOT_FOUND));
        assertSameCompany(source.getCompany().getId(), user);
        meterSourceRepository.deleteById(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    // --- fault events ----------------------------------------------------

    @GetMapping("/assets/{id}/fault-events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FaultEvent> faultEvents(@PathVariable("id") Long id,
                                        @RequestParam(value = "activeOnly", defaultValue = "false")
                                        boolean activeOnly,
                                        HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireAsset(id, user);
        return activeOnly
                ? faultEventRepository.findByAsset_IdAndClearedAtIsNull(id)
                : faultEventRepository.findByAsset_IdOrderByOccurredAtDesc(id);
    }

    /**
     * Fault-code frequency for the machine's Data tab — is this alarm routine
     * here, or is it new?
     */
    @GetMapping("/assets/{id}/fault-events/frequency")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<Map<String, Object>> frequency(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireAsset(id, user);
        return faultEventRepository.frequencyByCode(id).stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", row[0]);
            entry.put("count", ((Number) row[1]).longValue());
            entry.put("lastSeen", row[2]);
            return entry;
        }).collect(Collectors.toList());
    }

    @PostMapping("/fault-events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FaultEvent recordFault(@Valid @RequestBody FaultEvent event, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (!user.getRole().getCreatePermissions().contains(PermissionEntity.WORK_ORDERS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.METERS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(new Date());
        }
        return faultEventRepository.save(event);
    }

    @PostMapping("/fault-events/{id}/clear")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FaultEvent clearFault(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        FaultEvent event = faultEventRepository.findById(id)
                .orElseThrow(() -> new CustomException("Fault event not found", HttpStatus.NOT_FOUND));
        assertSameCompany(event.getCompany().getId(), user);
        event.setClearedAt(new Date());
        return faultEventRepository.save(event);
    }

    private Meter requireMeter(Long id, OwnUser user) {
        return meterService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Meter not found", HttpStatus.NOT_FOUND));
    }

    private Asset requireAsset(Long id, OwnUser user) {
        return assetService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
    }

    private void assertSameCompany(Long companyId, OwnUser user) {
        if (!companyId.equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireSettingsPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.SETTINGS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.METERS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
