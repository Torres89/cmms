package com.grash.controller;

import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.factory.StorageServiceFactory;
import com.grash.model.Document;
import com.grash.model.OwnUser;
import com.grash.model.enums.IngestStatus;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.DocumentService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents: registration, ingest status, and a signed URL to read them.
 */
@RestController
@RequestMapping("/documents")
@Tag(name = "document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final UserService userService;
    private final StorageServiceFactory storageServiceFactory;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<Document> list(@RequestParam(value = "assetId", required = false) Long assetId,
                               @RequestParam(value = "status", required = false) IngestStatus status,
                               HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Long companyId = user.getCompany().getId();
        if (assetId != null) {
            return documentService.findByAsset(assetId);
        }
        return status == null
                ? documentService.findByCompany(companyId)
                : documentService.findByStatus(companyId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Document getById(@PathVariable("id") Long id, HttpServletRequest req) {
        return require(id, userService.whoami(req));
    }

    /**
     * A short-lived signed URL to the underlying file.
     * <p>
     * Always a signed URL, never bytes through this API: large manuals and
     * training video must be fetched straight from the object store, which
     * serves range requests natively so the reader can seek.
     */
    @GetMapping("/{id}/url")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Map<String, String> signedUrl(@PathVariable("id") Long id,
                                         @RequestParam(value = "page", required = false) Integer page,
                                         HttpServletRequest req) {
        Document document = require(id, userService.whoami(req));
        String url = storageServiceFactory.getStorageService()
                .generateSignedUrl(document.getFile(), 60);
        Map<String, String> response = new LinkedHashMap<>();
        // Closing the trust loop: a citation the reader can click straight to.
        response.put("url", page == null ? url : url + "#page=" + page);
        response.put("title", document.getTitle());
        return response;
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Document create(@Valid @RequestBody Document document, HttpServletRequest req) {
        requireEditPermission(userService.whoami(req));
        return documentService.create(document);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Document patch(@PathVariable("id") Long id, @RequestBody Document patch, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        Document saved = require(id, user);
        if (patch.getTitle() != null) saved.setTitle(patch.getTitle());
        if (patch.getDocType() != null) saved.setDocType(patch.getDocType());
        if (patch.getManufacturer() != null) saved.setManufacturer(patch.getManufacturer());
        if (patch.getRevision() != null) saved.setRevision(patch.getRevision());
        if (patch.getLanguage() != null) saved.setLanguage(patch.getLanguage());
        if (patch.getAsset() != null) saved.setAsset(patch.getAsset());
        if (patch.getEquipmentClass() != null) saved.setEquipmentClass(patch.getEquipmentClass());
        return documentService.save(saved);
    }

    /**
     * Parse and index again — after a metadata correction, or a failed run.
     */
    @PostMapping("/{id}/reindex")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> reindex(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        documentService.reindex(require(id, user));
        return ResponseEntity.ok(new SuccessResponse(true, "Queued for reindexing"));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Map<String, Long> queue(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return documentService.queueSummary(user.getCompany().getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireEditPermission(user);
        require(id, user);
        documentService.delete(id);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }

    private Document require(Long id, OwnUser user) {
        Document document = documentService.findById(id)
                .orElseThrow(() -> new CustomException("Document not found", HttpStatus.NOT_FOUND));
        if (!document.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return document;
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getCreatePermissions().contains(PermissionEntity.FILES)
                && !user.getRole().getEditOtherPermissions().contains(PermissionEntity.FILES)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
