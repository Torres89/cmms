package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.FaultCodeDictionary;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.repository.FaultCodeDictionaryRepository;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The fault-code dictionary — customer-extensible on purpose.
 * <p>
 * A control tells a technician "SV0410" and stops. What that means lives in a
 * manual, in a service tool, or in someone's head; this is where a shop puts it
 * so it is there the next time, at 2am, for whoever is on shift.
 */
@RestController
@RequestMapping("/fault-codes")
@Tag(name = "faultCode")
@RequiredArgsConstructor
public class FaultCodeDictionaryController {

    private final FaultCodeDictionaryRepository faultCodeDictionaryRepository;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FaultCodeDictionary> list(
            @RequestParam(value = "equipmentClass", required = false) String equipmentClass,
            HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Long companyId = user.getCompany().getId();
        return equipmentClass == null
                ? faultCodeDictionaryRepository.findByCompanyIdOrCompanyIdIsNull(companyId)
                : faultCodeDictionaryRepository
                .findByEquipmentClassAndCompanyIdOrCompanyIdIsNull(equipmentClass, companyId);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<FaultCodeDictionary> lookup(
            @RequestParam("code") String code,
            @RequestParam(value = "equipmentClass", required = false) String equipmentClass,
            HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return faultCodeDictionaryRepository.lookup(code, user.getCompany().getId(), equipmentClass);
    }

    /**
     * Record what a code means. Always scoped to the caller's company — nobody
     * writes into the shared reference set through this API.
     */
    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FaultCodeDictionary create(@Valid @RequestBody FaultCodeDictionary entry,
                                      HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        entry.setCompanyId(user.getCompany().getId());
        if (entry.getSource() == null) {
            entry.setSource("MANUAL_ENTRY");
        }
        // Re-recording a code the shop already documented should correct it, not
        // add a second answer to the same question.
        return faultCodeDictionaryRepository
                .findFirstByCodeIgnoreCaseAndCompanyIdAndEquipmentClass(
                        entry.getCode(), user.getCompany().getId(), entry.getEquipmentClass())
                .map(existing -> {
                    entry.setId(existing.getId());
                    return faultCodeDictionaryRepository.save(entry);
                })
                .orElseGet(() -> faultCodeDictionaryRepository.save(entry));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public FaultCodeDictionary patch(@PathVariable("id") Long id,
                                     @RequestBody FaultCodeDictionary patch,
                                     HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        FaultCodeDictionary saved = require(id, user);
        if (patch.getDescriptionEn() != null) saved.setDescriptionEn(patch.getDescriptionEn());
        if (patch.getDescriptionEs() != null) saved.setDescriptionEs(patch.getDescriptionEs());
        if (patch.getSeverity() != null) saved.setSeverity(patch.getSeverity());
        if (patch.getLikelyCauses() != null) saved.setLikelyCauses(patch.getLikelyCauses());
        if (patch.getRecommendedAction() != null) saved.setRecommendedAction(patch.getRecommendedAction());
        return faultCodeDictionaryRepository.save(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        faultCodeDictionaryRepository.deleteById(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private FaultCodeDictionary require(Long id, OwnUser user) {
        FaultCodeDictionary entry = faultCodeDictionaryRepository.findById(id)
                .orElseThrow(() -> new CustomException("Fault code not found", HttpStatus.NOT_FOUND));
        if (entry.getCompanyId() == null) {
            throw new CustomException("Shared reference entries cannot be edited", HttpStatus.FORBIDDEN);
        }
        if (!entry.getCompanyId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return entry;
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.SETTINGS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
