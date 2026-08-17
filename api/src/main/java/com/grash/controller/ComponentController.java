package com.grash.controller;

import com.grash.dto.ComponentActionDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.ComponentEvent;
import com.grash.model.ComponentInstance;
import com.grash.model.OwnUser;
import com.grash.model.Vendor;
import com.grash.model.WorkOrder;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.ComponentService;
import com.grash.service.UserService;
import com.grash.service.VendorService;
import com.grash.service.WorkOrderService;
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
 * Serialized components: the register, the ledger, and the operations that
 * write to it.
 * <p>
 * Note that installs, removals and overhauls are POSTs against explicit
 * actions rather than PATCHes on fields. Configuration changes are events with
 * a time, a position and a meter value, and modelling them as edits would lose
 * exactly the history the whole feature exists to keep.
 */
@RestController
@RequestMapping("/components")
@Tag(name = "component")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;
    private final UserService userService;
    private final WorkOrderService workOrderService;
    private final VendorService vendorService;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<ComponentInstance> list(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return componentService.findByCompany(user.getCompany().getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance getById(@PathVariable("id") Long id, HttpServletRequest req) {
        return require(id, userService.whoami(req));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<ComponentEvent> history(@PathVariable("id") Long id, HttpServletRequest req) {
        require(id, userService.whoami(req));
        return componentService.historyOf(id);
    }

    /**
     * The ledger for a position rather than for a component — every spindle
     * cartridge this machine has ever had.
     */
    @GetMapping("/position/{assetId}/history")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<ComponentEvent> positionHistory(@PathVariable("assetId") Long assetId) {
        return componentService.historyOfPosition(assetId);
    }

    @GetMapping("/life-alerts")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<ComponentService.LifeAlert> lifeAlerts(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return componentService.findLifeAlerts(user.getCompany().getId());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance create(@Valid @RequestBody ComponentInstance component, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return componentService.create(component);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance patch(@PathVariable("id") Long id, @RequestBody ComponentInstance patch,
                                   HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        ComponentInstance saved = require(id, user);
        if (patch.getSerialNumber() != null) saved.setSerialNumber(patch.getSerialNumber());
        if (patch.getManufacturer() != null) saved.setManufacturer(patch.getManufacturer());
        if (patch.getMpn() != null) saved.setMpn(patch.getMpn());
        if (patch.getPartType() != null) saved.setPartType(patch.getPartType());
        if (patch.getManufactureDate() != null) saved.setManufactureDate(patch.getManufactureDate());
        if (patch.getAcquiredAt() != null) saved.setAcquiredAt(patch.getAcquiredAt());
        if (patch.getAcquisitionCost() != null) saved.setAcquisitionCost(patch.getAcquisitionCost());
        if (patch.getHourLimit() != null) saved.setHourLimit(patch.getHourLimit());
        if (patch.getCycleLimit() != null) saved.setCycleLimit(patch.getCycleLimit());
        if (patch.getCalendarLimitMonths() != null) saved.setCalendarLimitMonths(patch.getCalendarLimitMonths());
        if (patch.getImage() != null) saved.setImage(patch.getImage());
        if (patch.getNotes() != null) saved.setNotes(patch.getNotes());
        // Position and counters are only ever changed through the ledger.
        return componentService.save(saved);
    }

    @PostMapping("/{id}/install")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance install(@PathVariable("id") Long id,
                                     @RequestBody ComponentActionDTO action,
                                     HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        if (action.getPositionAssetId() == null) {
            throw new CustomException("An install needs a position", HttpStatus.BAD_REQUEST);
        }
        return componentService.install(id, action.getPositionAssetId(), action.getOccurredAt(),
                action.getMeterValue(), workOrder(action, user), user, action.getReason());
    }

    @PostMapping("/{id}/remove")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance remove(@PathVariable("id") Long id,
                                    @RequestBody ComponentActionDTO action,
                                    HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        return componentService.remove(id, action.getOccurredAt(), action.getMeterValue(),
                workOrder(action, user), user, action.getReason());
    }

    @PostMapping("/{id}/overhaul")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance overhaul(@PathVariable("id") Long id,
                                      @RequestBody ComponentActionDTO action,
                                      HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        Vendor vendor = null;
        if (action.getVendorId() != null) {
            vendor = vendorService.findById(action.getVendorId())
                    .orElseThrow(() -> new CustomException("Vendor not found", HttpStatus.NOT_FOUND));
        }
        return componentService.overhaul(id, action.getOccurredAt(), vendor, action.getCost(),
                user, action.getReason());
    }

    @PostMapping("/{id}/scrap")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentInstance scrap(@PathVariable("id") Long id,
                                   @RequestBody ComponentActionDTO action,
                                   HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        return componentService.scrap(id, action.getOccurredAt(), user, action.getReason());
    }

    /**
     * Free-form ledger entry — an inspection, a repair, a modification.
     */
    @PostMapping("/events")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ComponentEvent logEvent(@Valid @RequestBody ComponentEvent event, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(event.getComponent().getId(), user);
        if (event.getPerformedBy() == null) {
            event.setPerformedBy(user);
        }
        return componentService.logEvent(event);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        componentService.delete(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private WorkOrder workOrder(ComponentActionDTO action, OwnUser user) {
        if (action.getWorkOrderId() == null) {
            return null;
        }
        return workOrderService.findByIdAndCompany(action.getWorkOrderId(), user.getCompany().getId())
                .orElse(null);
    }

    private ComponentInstance require(Long id, OwnUser user) {
        ComponentInstance component = componentService.findById(id)
                .orElseThrow(() -> new CustomException("Component not found", HttpStatus.NOT_FOUND));
        if (!component.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return component;
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.ASSETS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
