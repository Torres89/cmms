package com.grash.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.AssetBomLine;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.AssetBomService;
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
 * Bill of materials lines.
 */
@RestController
@RequestMapping("/bom-lines")
@Tag(name = "bom")
@RequiredArgsConstructor
public class AssetBomController {

    private final AssetBomService assetBomService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @GetMapping("/part/{partId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<AssetBomLine> whereUsed(@PathVariable("partId") Long partId) {
        return assetBomService.findByPart(partId);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetBomLine create(@Valid @RequestBody AssetBomLine line, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return assetBomService.create(line);
    }

    /**
     * Bulk create — commissioning enters a BOM a screen at a time, not a field
     * at a time.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<AssetBomLine> createBulk(@Valid @RequestBody List<AssetBomLine> lines, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        lines.forEach(assetBomService::create);
        return lines;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AssetBomLine patch(@PathVariable("id") Long id, @RequestBody JsonNode body,
                              HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        AssetBomLine saved = require(id, user);
        AssetBomLine patch = readPatch(body);
        if (patch.getPart() != null) saved.setPart(patch.getPart());
        if (patch.getPositionCode() != null) saved.setPositionCode(patch.getPositionCode());
        if (patch.getQtyPerAssembly() != null) saved.setQtyPerAssembly(patch.getQtyPerAssembly());
        if (body.has("consumable")) saved.setConsumable(patch.isConsumable());
        if (body.has("replaceIntervalHours")) saved.setReplaceIntervalHours(patch.getReplaceIntervalHours());
        if (body.has("replaceIntervalMonths")) saved.setReplaceIntervalMonths(patch.getReplaceIntervalMonths());
        if (patch.getNotes() != null) saved.setNotes(patch.getNotes());
        return assetBomService.save(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireDeletePermission(user);
        require(id, user);
        assetBomService.delete(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private AssetBomLine require(Long id, OwnUser user) {
        AssetBomLine line = assetBomService.findById(id)
                .orElseThrow(() -> new CustomException("BOM line not found", HttpStatus.NOT_FOUND));
        if (!line.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return line;
    }

    /**
     * Deleting asset documentation takes the role's delete permission for
     * assets (or owning the company), not the create permission technicians
     * have.
     */
    private void requireDeletePermission(OwnUser user) {
        if (!user.isOwnsCompany()
                && !user.getRole().getDeleteOtherPermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.ASSETS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * A PATCH changes the fields it names and nothing else. Binding straight to
     * the entity cannot tell an omitted field from a null or false one, so a
     * client that sent only a label used to wipe the value beside it.
     */
    private AssetBomLine readPatch(JsonNode body) {
        try {
            return objectMapper.treeToValue(body, AssetBomLine.class);
        } catch (JsonProcessingException e) {
            throw new CustomException("Invalid body: " + e.getOriginalMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
