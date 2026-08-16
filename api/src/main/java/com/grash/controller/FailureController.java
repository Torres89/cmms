package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.FailureEvent;
import com.grash.model.FailureMode;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.FailureService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

/**
 * The failure taxonomy and the events recorded against it.
 */
@RestController
@Tag(name = "failure")
@RequiredArgsConstructor
public class FailureController {

    private final FailureService failureService;
    private final UserService userService;

    // --- catalogue ------------------------------------------------------

    @GetMapping("/failure-modes")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FailureMode> listModes(
            @RequestParam(value = "equipmentClass", required = false) String equipmentClass,
            @RequestParam(value = "subunit", required = false) String subunit,
            HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Long companyId = user.getCompany().getId();
        if (equipmentClass == null) {
            return failureService.findModesByCompany(companyId);
        }
        return subunit == null
                ? failureService.findModesForClass(equipmentClass, companyId)
                : failureService.findModesForSubunit(equipmentClass, subunit, companyId);
    }

    @PostMapping("/failure-modes")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FailureMode createMode(@Valid @RequestBody FailureMode mode, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return failureService.saveMode(mode);
    }

    @PatchMapping("/failure-modes/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FailureMode patchMode(@PathVariable("id") Long id, @RequestBody FailureMode patch,
                                 HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        FailureMode saved = failureService.findModeById(id)
                .orElseThrow(() -> new CustomException("Failure mode not found", HttpStatus.NOT_FOUND));
        assertSameCompany(saved.getCompany().getId(), user);
        if (patch.getNameEn() != null) saved.setNameEn(patch.getNameEn());
        if (patch.getNameEs() != null) saved.setNameEs(patch.getNameEs());
        if (patch.getSubunit() != null) saved.setSubunit(patch.getSubunit());
        if (patch.getDescription() != null) saved.setDescription(patch.getDescription());
        if (patch.getTypicalMechanism() != null) saved.setTypicalMechanism(patch.getTypicalMechanism());
        if (patch.getTypicalCauses() != null) saved.setTypicalCauses(patch.getTypicalCauses());
        if (patch.getDetectionMethods() != null) saved.setDetectionMethods(patch.getDetectionMethods());
        if (patch.getSeverityDefault() != null) saved.setSeverityDefault(patch.getSeverityDefault());
        return failureService.saveMode(saved);
    }

    @DeleteMapping("/failure-modes/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> deleteMode(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        FailureMode saved = failureService.findModeById(id)
                .orElseThrow(() -> new CustomException("Failure mode not found", HttpStatus.NOT_FOUND));
        assertSameCompany(saved.getCompany().getId(), user);
        failureService.deleteMode(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    // --- events ---------------------------------------------------------

    /**
     * Record what broke. Normally posted from the work-order close dialog,
     * which is the only moment anyone actually knows the answer.
     */
    @PostMapping("/failure-events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FailureEvent recordEvent(@Valid @RequestBody FailureEvent event, HttpServletRequest req) {
        requireWorkOrderPermission(userService.whoami(req));
        return failureService.record(event);
    }

    @PatchMapping("/failure-events/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FailureEvent patchEvent(@PathVariable("id") Long id, @RequestBody FailureEvent patch,
                                   HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireWorkOrderPermission(user);
        FailureEvent saved = failureService.findEventById(id)
                .orElseThrow(() -> new CustomException("Failure event not found", HttpStatus.NOT_FOUND));
        assertSameCompany(saved.getCompany().getId(), user);
        if (patch.getFailureMode() != null) saved.setFailureMode(patch.getFailureMode());
        if (patch.getComponent() != null) saved.setComponent(patch.getComponent());
        if (patch.getOccurredAt() != null) saved.setOccurredAt(patch.getOccurredAt());
        if (patch.getWorkOrder() != null) saved.setWorkOrder(patch.getWorkOrder());
        if (patch.getMechanism() != null) saved.setMechanism(patch.getMechanism());
        if (patch.getCause() != null) saved.setCause(patch.getCause());
        if (patch.getDetectionMethod() != null) saved.setDetectionMethod(patch.getDetectionMethod());
        if (patch.getDetectedAt() != null) saved.setDetectedAt(patch.getDetectedAt());
        if (patch.getSeverity() != null) saved.setSeverity(patch.getSeverity());
        if (patch.getDowntimeMinutes() != null) saved.setDowntimeMinutes(patch.getDowntimeMinutes());
        if (patch.getRepairCost() != null) saved.setRepairCost(patch.getRepairCost());
        if (patch.getCorrectiveAction() != null) saved.setCorrectiveAction(patch.getCorrectiveAction());
        if (patch.getPreventiveRecommendation() != null) {
            saved.setPreventiveRecommendation(patch.getPreventiveRecommendation());
        }
        return failureService.record(saved);
    }

    @GetMapping("/work-orders/{id}/failure-events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FailureEvent> forWorkOrder(@PathVariable("id") Long id) {
        return failureService.findEventsForWorkOrder(id);
    }

    @GetMapping("/components/{id}/failure-events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FailureEvent> forComponent(@PathVariable("id") Long id) {
        return failureService.findEventsForComponent(id);
    }

    @DeleteMapping("/failure-events/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> deleteEvent(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireWorkOrderPermission(user);
        FailureEvent saved = failureService.findEventById(id)
                .orElseThrow(() -> new CustomException("Failure event not found", HttpStatus.NOT_FOUND));
        assertSameCompany(saved.getCompany().getId(), user);
        failureService.deleteEvent(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private void assertSameCompany(Long companyId, OwnUser user) {
        if (!companyId.equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.SETTINGS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireWorkOrderPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.WORK_ORDERS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.WORK_ORDERS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
