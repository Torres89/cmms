package com.grash.controller;

import com.grash.dto.PartSourcingDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.OwnUser;
import com.grash.model.Part;
import com.grash.model.PartCrossReference;
import com.grash.model.PartSupplier;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.PartService;
import com.grash.service.PartSourcingService;
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
 * Part sourcing: suppliers, prices, lead times, links and alternates.
 */
@RestController
@Tag(name = "partSourcing")
@RequiredArgsConstructor
public class PartSourcingController {

    private final PartSourcingService partSourcingService;
    private final PartService partService;
    private final UserService userService;

    /**
     * Everything needed to decide whether, where and how much to buy.
     */
    @GetMapping("/parts/{id}/sourcing")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PartSourcingDTO sourcing(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Part part = partService.findById(id)
                .orElseThrow(() -> new CustomException("Part not found", HttpStatus.NOT_FOUND));
        if (!part.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return partSourcingService.sourcingFor(part);
    }

    @PostMapping("/part-suppliers")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PartSupplier createSupplier(@Valid @RequestBody PartSupplier supplier, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return partSourcingService.saveSupplier(supplier);
    }

    @PatchMapping("/part-suppliers/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PartSupplier patchSupplier(@PathVariable("id") Long id, @RequestBody PartSupplier patch,
                                      HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        PartSupplier saved = partSourcingService.findSupplierById(id)
                .orElseThrow(() -> new CustomException("Supplier record not found", HttpStatus.NOT_FOUND));
        if (!saved.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (patch.getVendor() != null) saved.setVendor(patch.getVendor());
        if (patch.getSupplierSku() != null) saved.setSupplierSku(patch.getSupplierSku());
        if (patch.getProductUrl() != null) saved.setProductUrl(patch.getProductUrl());
        if (patch.getUnitPrice() != null) {
            saved.setUnitPrice(patch.getUnitPrice());
            // A price is only meaningful with a date on it.
            saved.setPriceCheckedAt(patch.getPriceCheckedAt() != null
                    ? patch.getPriceCheckedAt() : new java.util.Date());
        }
        if (patch.getCurrency() != null) saved.setCurrency(patch.getCurrency());
        if (patch.getMoq() != null) saved.setMoq(patch.getMoq());
        if (patch.getLeadTimeDays() != null) saved.setLeadTimeDays(patch.getLeadTimeDays());
        if (patch.getNotes() != null) saved.setNotes(patch.getNotes());
        saved.setPreferred(patch.isPreferred());
        return partSourcingService.saveSupplier(saved);
    }

    @DeleteMapping("/part-suppliers/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> deleteSupplier(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        PartSupplier saved = partSourcingService.findSupplierById(id)
                .orElseThrow(() -> new CustomException("Supplier record not found", HttpStatus.NOT_FOUND));
        if (!saved.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        partSourcingService.deleteSupplier(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    @GetMapping("/parts/{id}/cross-references")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<PartCrossReference> crossReferences(@PathVariable("id") Long id) {
        return partSourcingService.findAlternates(id);
    }

    @PostMapping("/part-cross-references")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PartCrossReference createCrossReference(@Valid @RequestBody PartCrossReference reference,
                                                   HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return partSourcingService.saveCrossReference(reference);
    }

    @DeleteMapping("/part-cross-references/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> deleteCrossReference(@PathVariable("id") Long id,
                                                                HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        PartCrossReference saved = partSourcingService.findCrossReferenceById(id)
                .orElseThrow(() -> new CustomException("Cross reference not found", HttpStatus.NOT_FOUND));
        if (!saved.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        partSourcingService.deleteCrossReference(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.PARTS_AND_MULTIPARTS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.PARTS_AND_MULTIPARTS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
