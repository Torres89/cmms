package com.grash.controller;

import com.grash.dto.IntervalStatusDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.MaintenanceInterval;
import com.grash.model.OwnUser;
import com.grash.model.PreventiveMaintenance;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.MaintenanceIntervalService;
import com.grash.service.PreventiveMaintenanceService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Date;
import java.util.List;

/**
 * The counters a preventive maintenance is measured against, and where it
 * currently stands against them.
 */
@RestController
@Tag(name = "maintenanceInterval")
@RequiredArgsConstructor
public class MaintenanceIntervalController {

    private final MaintenanceIntervalService maintenanceIntervalService;
    private final PreventiveMaintenanceService preventiveMaintenanceService;
    private final UserService userService;

    @GetMapping("/preventive-maintenances/{id}/intervals")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<MaintenanceInterval> list(@PathVariable("id") Long id, HttpServletRequest req) {
        requirePm(id, userService.whoami(req));
        return maintenanceIntervalService.findByPreventiveMaintenance(id);
    }

    /**
     * How far through each counter this PM is, and which one will fire first.
     */
    @GetMapping("/preventive-maintenances/{id}/status")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public IntervalStatusDTO status(@PathVariable("id") Long id, HttpServletRequest req) {
        return maintenanceIntervalService.status(requirePm(id, userService.whoami(req)));
    }

    @PostMapping("/maintenance-intervals")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public MaintenanceInterval create(@Valid @RequestBody MaintenanceInterval interval, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        if (interval.getPreventiveMaintenance() == null) {
            throw new CustomException("An interval must belong to a preventive maintenance",
                    HttpStatus.BAD_REQUEST);
        }
        requirePm(interval.getPreventiveMaintenance().getId(), user);
        return maintenanceIntervalService.save(interval);
    }

    @PatchMapping("/maintenance-intervals/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public MaintenanceInterval patch(@PathVariable("id") Long id, @RequestBody MaintenanceInterval patch,
                                     HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        MaintenanceInterval saved = require(id, user);
        if (patch.getBasis() != null) saved.setBasis(patch.getBasis());
        if (patch.getMeter() != null) saved.setMeter(patch.getMeter());
        if (patch.getIntervalValue() != null) saved.setIntervalValue(patch.getIntervalValue());
        if (patch.getUnit() != null) saved.setUnit(patch.getUnit());
        if (patch.getWarnAtPercent() != null) saved.setWarnAtPercent(patch.getWarnAtPercent());
        if (patch.getDescription() != null) saved.setDescription(patch.getDescription());
        return maintenanceIntervalService.save(saved);
    }

    /**
     * Record that the maintenance was done, resetting every counter on it.
     */
    @PostMapping("/preventive-maintenances/{id}/completed")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> markCompleted(@PathVariable("id") Long id,
                                                         @RequestParam(value = "at", required = false) Long at,
                                                         HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        requirePm(id, user);
        maintenanceIntervalService.markCompleted(id, at == null ? new Date() : new Date(at));
        return ResponseEntity.ok(new SuccessResponse(true, "Counters reset"));
    }

    @DeleteMapping("/maintenance-intervals/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        maintenanceIntervalService.delete(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private MaintenanceInterval require(Long id, OwnUser user) {
        MaintenanceInterval interval = maintenanceIntervalService.findById(id)
                .orElseThrow(() -> new CustomException("Interval not found", HttpStatus.NOT_FOUND));
        if (!interval.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return interval;
    }

    private PreventiveMaintenance requirePm(Long id, OwnUser user) {
        return preventiveMaintenanceService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Preventive maintenance not found", HttpStatus.NOT_FOUND));
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.PREVENTIVE_MAINTENANCES)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.PREVENTIVE_MAINTENANCES)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
