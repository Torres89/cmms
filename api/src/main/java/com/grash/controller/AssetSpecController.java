package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.AssetSpec;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.SpecSource;
import com.grash.service.AssetService;
import com.grash.service.AssetSpecService;
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
import java.util.Optional;

/**
 * The spec sheet, including the review queue for values a machine proposed.
 */
@RestController
@RequestMapping("/asset-specs")
@Tag(name = "assetSpec")
@RequiredArgsConstructor
public class AssetSpecController {

    private final AssetSpecService assetSpecService;
    private final AssetService assetService;
    private final UserService userService;

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetSpec create(@Valid @RequestBody AssetSpec spec, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        Asset asset = requireAsset(spec.getAsset() == null ? null : spec.getAsset().getId(), user);
        spec.setAsset(asset);
        // A person typing a value in is a person vouching for it. Only machine
        // output arrives unverified.
        if (spec.getSource() == null || spec.getSource() == SpecSource.MANUAL_ENTRY) {
            spec.setSource(SpecSource.MANUAL_ENTRY);
            spec.setVerifiedBy(user);
            spec.setVerifiedAt(new java.util.Date());
        }
        return assetSpecService.create(spec);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetSpec patch(@PathVariable("id") Long id, @RequestBody AssetSpec patch, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        AssetSpec saved = require(id, user);
        if (patch.getSpecKey() != null) saved.setSpecKey(patch.getSpecKey());
        if (patch.getSpecGroup() != null) saved.setSpecGroup(patch.getSpecGroup());
        if (patch.getLabel() != null) saved.setLabel(patch.getLabel());
        if (patch.getUnit() != null) saved.setUnit(patch.getUnit());
        saved.setValueText(patch.getValueText());
        saved.setValueNum(patch.getValueNum());
        // A person editing a value is a person vouching for it.
        saved.setVerifiedBy(user);
        saved.setVerifiedAt(new java.util.Date());
        return assetSpecService.save(saved);
    }

    /**
     * Confirm an extracted value.
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetSpec verify(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        return assetSpecService.verify(id, user);
    }

    /**
     * Withdraw a confirmation, so a value can be put back in question without
     * deleting it.
     */
    @PostMapping("/{id}/unverify")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetSpec unverify(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        return assetSpecService.unverify(id);
    }

    /**
     * Approve a batch. Commissioning has to be approve-all-then-correct; a
     * confirm-each queue is what makes people stop confirming.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> verifyAll(@RequestBody List<Long> ids, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        ids.forEach(id -> require(id, user));
        int count = assetSpecService.verifyAll(ids, user);
        return ResponseEntity.ok(new SuccessResponse(true, count + " specs verified"));
    }

    /**
     * Everything a machine proposed and nobody has confirmed yet.
     */
    @GetMapping("/unverified")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<AssetSpec> unverified(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return assetSpecService.findUnverified(user.getCompany().getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        assetSpecService.delete(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private AssetSpec require(Long id, OwnUser user) {
        AssetSpec spec = assetSpecService.findById(id)
                .orElseThrow(() -> new CustomException("Spec not found", HttpStatus.NOT_FOUND));
        if (!spec.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return spec;
    }

    private Asset requireAsset(Long assetId, OwnUser user) {
        if (assetId == null) {
            throw new CustomException("A spec must reference an asset", HttpStatus.BAD_REQUEST);
        }
        Optional<Asset> asset = assetService.findByIdAndCompany(assetId, user.getCompany().getId());
        return asset.orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.ASSETS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
