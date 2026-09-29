package com.grash.controller;

import com.grash.dto.CustomFieldPatchDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.CustomField;
import com.grash.model.OwnUser;
import com.grash.model.enums.CustomFieldEntity;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.AssetService;
import com.grash.service.ComponentService;
import com.grash.service.CustomFieldService;
import com.grash.service.LocationService;
import com.grash.service.PartService;
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

import java.util.Collection;
import java.util.Optional;

@RestController
@RequestMapping("/custom-fields")
@Tag(name = "customField")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final UserService userService;
    private final AssetService assetService;
    private final PartService partService;
    private final WorkOrderService workOrderService;
    private final LocationService locationService;
    private final VendorService vendorService;
    private final ComponentService componentService;

    /**
     * Every custom field attached to one entity — assets, parts, work orders,
     * locations, vendors or component instances.
     */
    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<CustomField> getForEntity(@PathVariable("entityType") CustomFieldEntity entityType,
                                                @PathVariable("entityId") Long entityId,
                                                HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return customFieldService.findForEntity(entityType, entityId, user.getCompany().getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")

    public CustomField getById(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<CustomField> optionalCustomField = customFieldService.findById(id);
        if (optionalCustomField.isPresent() && (user.getRole().getRoleType().equals(RoleType.ROLE_SUPER_ADMIN)
                || belongsToCompany(optionalCustomField.get(), user))) {
            return optionalCustomField.get();
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    CustomField create(@Valid @RequestBody CustomField customFieldReq,
                       HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user, customFieldReq.getEntityType());
        // entityId is a bare number, not a relation, so nothing else stops a
        // field being hung on another company's record.
        if (!entityBelongsToCompany(customFieldReq.getEntityType(), customFieldReq.getEntityId(), user)) {
            throw new CustomException("Entity not found", HttpStatus.NOT_FOUND);
        }
        customFieldReq.setId(null);
        return customFieldService.create(customFieldReq);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public CustomField patch(@Valid @RequestBody CustomFieldPatchDTO customField, @PathVariable("id") Long id,
                             HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        CustomField savedCustomField = require(id, user);
        requireEditPermission(user, savedCustomField.getEntityType());
        return customFieldService.update(id, customField);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        CustomField savedCustomField = require(id, user);
        requireDeletePermission(user, savedCustomField.getEntityType());
        customFieldService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"),
                HttpStatus.OK);
    }

    private CustomField require(Long id, OwnUser user) {
        return customFieldService.findById(id)
                .filter(customField -> belongsToCompany(customField, user))
                .orElseThrow(() -> new CustomException("CustomField not found", HttpStatus.NOT_FOUND));
    }

    private boolean belongsToCompany(CustomField customField, OwnUser user) {
        return customField.getCompany() != null
                && customField.getCompany().getId().equals(user.getCompany().getId());
    }

    private boolean entityBelongsToCompany(CustomFieldEntity entityType, Long entityId, OwnUser user) {
        if (entityType == null || entityId == null) return false;
        Long companyId = user.getCompany().getId();
        switch (entityType) {
            case ASSET:
                return assetService.findByIdAndCompany(entityId, companyId).isPresent();
            case PART:
                return partService.findByIdAndCompany(entityId, companyId).isPresent();
            case WORK_ORDER:
                return workOrderService.findByIdAndCompany(entityId, companyId).isPresent();
            case LOCATION:
                return locationService.findByIdAndCompany(entityId, companyId).isPresent();
            case VENDOR:
                return vendorService.findById(entityId)
                        .filter(vendor -> vendor.getCompany().getId().equals(companyId)).isPresent();
            case COMPONENT_INSTANCE:
                return componentService.findById(entityId)
                        .filter(component -> component.getCompany().getId().equals(companyId)).isPresent();
            default:
                return false;
        }
    }

    /**
     * The permission that governs the record a custom field hangs off.
     */
    private PermissionEntity permissionFor(CustomFieldEntity entityType) {
        if (entityType == null) return PermissionEntity.SETTINGS;
        switch (entityType) {
            case PART:
                return PermissionEntity.PARTS_AND_MULTIPARTS;
            case WORK_ORDER:
                return PermissionEntity.WORK_ORDERS;
            case LOCATION:
                return PermissionEntity.LOCATIONS;
            case VENDOR:
                return PermissionEntity.VENDORS_AND_CUSTOMERS;
            case ASSET:
            case COMPONENT_INSTANCE:
            default:
                return PermissionEntity.ASSETS;
        }
    }

    private void requireEditPermission(OwnUser user, CustomFieldEntity entityType) {
        PermissionEntity entity = permissionFor(entityType);
        if (!user.isOwnsCompany()
                && !user.getRole().getCreatePermissions().contains(entity)
                && !user.getRole().getEditOtherPermissions().contains(entity)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireDeletePermission(OwnUser user, CustomFieldEntity entityType) {
        if (!user.isOwnsCompany()
                && !user.getRole().getDeleteOtherPermissions().contains(permissionFor(entityType))) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
