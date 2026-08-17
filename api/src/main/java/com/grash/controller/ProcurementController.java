package com.grash.controller;

import com.grash.dto.RestockKitDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.OwnUser;
import com.grash.model.Part;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.AssetService;
import com.grash.service.PartService;
import com.grash.service.RestockService;
import com.grash.service.UserService;
import com.grash.service.catalog.SupplierCatalogService;
import com.grash.service.catalog.SupplierOffer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Procurement depth: restock kits, reorder points, and catalogue lookups.
 */
@RestController
@Tag(name = "procurement")
@RequiredArgsConstructor
public class ProcurementController {

    private final RestockService restockService;
    private final SupplierCatalogService supplierCatalogService;
    private final AssetService assetService;
    private final PartService partService;
    private final UserService userService;

    /**
     * Everything due on this machine soon that isn't already on the shelf.
     */
    @GetMapping("/assets/{id}/restock-kit")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public RestockKitDTO restockKit(@PathVariable("id") Long id,
                                    @RequestParam(value = "horizonDays", defaultValue = "60") int horizonDays,
                                    HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Asset asset = assetService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
        return restockService.kitFor(asset, horizonDays);
    }

    /**
     * A suggested reorder point from a year of consumption and the lead time.
     * <p>
     * Returns null when there is nothing real to compute from — a made-up
     * reorder point causes worse decisions than none at all.
     */
    @GetMapping("/parts/{id}/reorder-point")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Map<String, Object> reorderPoint(@PathVariable("id") Long id, HttpServletRequest req) {
        Part part = requirePart(id, userService.whoami(req));
        Map<String, Object> response = new LinkedHashMap<>();
        Double suggestion = restockService.suggestReorderPoint(part);
        response.put("partId", part.getId());
        response.put("current", part.getReorderPoint());
        response.put("suggested", suggestion);
        response.put("onHand", part.getQuantity());
        if (suggestion == null) {
            response.put("note", "Not enough consumption history or lead-time data to suggest one.");
        }
        return response;
    }

    @GetMapping("/catalog/adapters")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<Map<String, Object>> adapters() {
        return supplierCatalogService.availableAdapters();
    }

    /**
     * Ask every configured catalogue about this part.
     * <p>
     * Results are returned for a human to accept, never written straight in: an
     * automatically imported price for the wrong part is worse than no price,
     * because nobody knows to doubt it.
     */
    @GetMapping("/parts/{id}/catalog-lookup")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<SupplierOffer> catalogLookup(@PathVariable("id") Long id, HttpServletRequest req) {
        return supplierCatalogService.lookup(requirePart(id, userService.whoami(req)));
    }

    private Part requirePart(Long id, OwnUser user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.PARTS_AND_MULTIPARTS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        Part part = partService.findById(id)
                .orElseThrow(() -> new CustomException("Part not found", HttpStatus.NOT_FOUND));
        if (!part.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return part;
    }
}
