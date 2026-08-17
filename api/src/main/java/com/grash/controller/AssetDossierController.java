package com.grash.controller;

import com.grash.dto.AssetDossierDTO;
import com.grash.dto.SpecCompletenessDTO;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The dossier and the per-asset views that hang off it.
 * <p>
 * {@code GET /assets/{id}/dossier} is the endpoint everything else leans on:
 * the dossier page renders the JSON, the MCP server exposes it as a resource,
 * and the in-app chat injects the text form on every turn.
 */
@RestController
@RequestMapping("/assets")
@Tag(name = "asset")
@RequiredArgsConstructor
public class AssetDossierController {

    private final AssetService assetService;
    private final AssetDossierService assetDossierService;
    private final AssetSpecService assetSpecService;
    private final AssetBomService assetBomService;
    private final ComponentService componentService;
    private final FailureService failureService;
    private final UserService userService;

    /**
     * Everything true about one machine right now.
     *
     * @param format {@code json} for the UI, {@code text} for AI clients.
     */
    @GetMapping("/{id}/dossier")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<?> dossier(@PathVariable("id") Long id,
                                     @RequestParam(value = "format", defaultValue = "json") String format,
                                     HttpServletRequest req) {
        Asset asset = requireVisibleAsset(id, req);
        AssetDossierDTO dossier = assetDossierService.build(asset);
        if ("text".equalsIgnoreCase(format)) {
            // Still JSON-wrapped so a tool call gets a predictable envelope.
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Collections.singletonMap("text", dossier.getText()));
        }
        return ResponseEntity.ok(dossier);
    }

    @GetMapping("/{id}/specs")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<AssetSpec> specs(@PathVariable("id") Long id,
                                       @RequestParam(value = "group", required = false) String group,
                                       HttpServletRequest req) {
        requireVisibleAsset(id, req);
        return group == null
                ? assetSpecService.findByAsset(id)
                : assetSpecService.findByAssetAndGroup(id, group);
    }

    @GetMapping("/{id}/specs/completeness")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SpecCompletenessDTO completeness(@PathVariable("id") Long id, HttpServletRequest req) {
        return assetSpecService.completeness(requireVisibleAsset(id, req));
    }

    @GetMapping("/{id}/components")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<ComponentInstance> components(@PathVariable("id") Long id, HttpServletRequest req) {
        requireVisibleAsset(id, req);
        return componentService.findInstalledInSubtree(id);
    }

    @GetMapping("/{id}/bom")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public BomResponse bom(@PathVariable("id") Long id,
                           @RequestParam(value = "subunit", required = false) String subunit,
                           HttpServletRequest req) {
        requireVisibleAsset(id, req);
        BomResponse response = new BomResponse();
        response.lines = subunit == null
                ? assetBomService.findByAsset(id)
                : assetBomService.findByAssetAndPosition(id, subunit);
        return response;
    }

    /**
     * Wrapped so the empty case is unambiguous to a model reading the response:
     * an empty list is the answer, not an invitation to guess a part number.
     */
    public static class BomResponse {
        public List<AssetBomLine> lines;

        public String getNote() {
            return (lines == null || lines.isEmpty())
                    ? "No bill of materials has been captured for this asset."
                    : null;
        }

        public List<AssetBomLine> getLines() {
            return lines;
        }
    }

    @GetMapping("/{id}/failures")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FailureEvent> failures(@PathVariable("id") Long id, HttpServletRequest req) {
        requireVisibleAsset(id, req);
        return failureService.findEventsForAsset(id);
    }

    @GetMapping("/{id}/failures/pareto")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<com.grash.dto.FailureParetoDTO> pareto(@PathVariable("id") Long id, HttpServletRequest req) {
        requireVisibleAsset(id, req);
        return failureService.pareto(id);
    }

    /**
     * Failure modes worth offering for this machine, ranked by what has
     * actually happened to it.
     */
    @GetMapping("/{id}/failure-modes")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FailureMode> candidateFailureModes(@PathVariable("id") Long id,
                                                   @RequestParam(value = "subunit", required = false) String subunit,
                                                   HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Asset asset = requireVisibleAsset(id, req);
        return failureService.rankedCandidates(asset, subunit, user.getCompany().getId());
    }

    private Asset requireVisibleAsset(Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<Asset> optionalAsset = assetService.findByIdAndCompany(id, user.getCompany().getId());
        if (optionalAsset.isEmpty()) {
            throw new CustomException("Asset not found", HttpStatus.NOT_FOUND);
        }
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return optionalAsset.get();
    }
}
