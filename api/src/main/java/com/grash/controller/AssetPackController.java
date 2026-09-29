package com.grash.controller;

import com.grash.dto.pack.AssetPackDTO;
import com.grash.dto.pack.PackInstantiationResultDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.AssetPackService;
import com.grash.service.AssetService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * Vertical packs — the customisation mechanism that keeps customers off code
 * branches.
 * <p>
 * Every caller sees the shipped packs plus the packs its own company
 * registered, and nothing else.
 */
@RestController
@RequestMapping("/asset-templates")
@Tag(name = "assetPack")
@RequiredArgsConstructor
public class AssetPackController {

    private final AssetPackService assetPackService;
    private final AssetService assetService;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<AssetPackDTO> list(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return assetPackService.findAll(user.getCompany().getId());
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetPackDTO get(@PathVariable("key") String key, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return assetPackService.findByKey(key, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Unknown pack: " + key, HttpStatus.NOT_FOUND));
    }

    /**
     * Build a machine out from its pack — step one of commissioning.
     *
     * @param dryRun preview what would be created without creating it
     */
    @PostMapping("/{key}/instantiate")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PackInstantiationResultDTO instantiate(@PathVariable("key") String key,
                                                  @RequestParam("assetId") Long assetId,
                                                  @RequestParam(value = "dryRun", defaultValue = "false")
                                                  boolean dryRun,
                                                  HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireCommissioningPermission(user);
        Asset asset = assetService.findByIdAndCompany(assetId, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
        return assetPackService.instantiate(key, asset, user, dryRun);
    }

    /**
     * Register a customer-specific pack at runtime, for the caller's company.
     * <p>
     * The point of this endpoint is that a customer who wants different PM
     * templates gets a JSON file the same day, and nobody has to maintain a
     * branch for them. It is stored against the caller's company, survives a
     * restart, and cannot reuse a shipped pack's key (409).
     */
    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetPackDTO register(@Valid @RequestBody AssetPackDTO pack, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        return assetPackService.register(pack, user.getCompany().getId());
    }

    private void requireCommissioningPermission(OwnUser user) {
        if (!user.isOwnsCompany()
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Defining what a class of machine looks like for the whole company is an
     * administrator's job, not a technician's.
     */
    private void requireSettingsPermission(OwnUser user) {
        if (!user.isOwnsCompany()
                && !user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
