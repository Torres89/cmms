package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.OwnUser;
import com.grash.model.SpecKeyCatalog;
import com.grash.model.enums.PermissionEntity;
import com.grash.repository.SpecKeyCatalogRepository;
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
 * The expected spec keys per equipment class.
 * <p>
 * Normally seeded from a vertical pack; editable here because a customer with
 * an unusual machine needs a way to say so that is not a code change.
 */
@RestController
@RequestMapping("/spec-keys")
@Tag(name = "specKeyCatalog")
@RequiredArgsConstructor
public class SpecKeyCatalogController {

    private final SpecKeyCatalogRepository specKeyCatalogRepository;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<SpecKeyCatalog> list(@RequestParam(value = "equipmentClass", required = false) String equipmentClass,
                                     HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Long companyId = user.getCompany().getId();
        return equipmentClass == null
                ? specKeyCatalogRepository.findByCompany_Id(companyId)
                : specKeyCatalogRepository
                .findByEquipmentClassAndCompany_IdOrderByDisplayOrderAscSpecGroupAscSpecKeyAsc(
                        equipmentClass, companyId);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SpecKeyCatalog create(@Valid @RequestBody SpecKeyCatalog entry, HttpServletRequest req) {
        requireSettingsPermission(userService.whoami(req));
        return specKeyCatalogRepository.save(entry);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SpecKeyCatalog patch(@PathVariable("id") Long id, @RequestBody SpecKeyCatalog patch,
                                HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        SpecKeyCatalog saved = require(id, user);
        if (patch.getSpecGroup() != null) saved.setSpecGroup(patch.getSpecGroup());
        if (patch.getLabelEn() != null) saved.setLabelEn(patch.getLabelEn());
        if (patch.getLabelEs() != null) saved.setLabelEs(patch.getLabelEs());
        if (patch.getUnit() != null) saved.setUnit(patch.getUnit());
        if (patch.getValueType() != null) saved.setValueType(patch.getValueType());
        if (patch.getDisplayOrder() != null) saved.setDisplayOrder(patch.getDisplayOrder());
        saved.setRequired(patch.isRequired());
        return specKeyCatalogRepository.save(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        require(id, user);
        specKeyCatalogRepository.deleteById(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private SpecKeyCatalog require(Long id, OwnUser user) {
        SpecKeyCatalog entry = specKeyCatalogRepository.findById(id)
                .orElseThrow(() -> new CustomException("Spec key not found", HttpStatus.NOT_FOUND));
        if (!entry.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return entry;
    }

    private void requireSettingsPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.SETTINGS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
