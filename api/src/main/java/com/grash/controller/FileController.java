package com.grash.controller;


import com.grash.advancedsearch.FilterField;
import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.FilePatchDTO;
import com.grash.dto.FileShowDTO;
import com.grash.dto.SuccessResponse;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.exception.CustomException;
import com.grash.factory.StorageServiceFactory;
import com.grash.mapper.FileMapper;
import com.grash.model.Document;
import com.grash.model.File;
import com.grash.model.OwnUser;
import com.grash.model.Task;
import com.grash.model.enums.*;
import com.grash.service.AssetService;
import com.grash.service.DocumentService;
import com.grash.service.FileService;
import com.grash.service.LicenseService;
import com.grash.service.TaskService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.*;
import java.util.stream.Collectors;


@RestController
@Tag(name = "file")
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final StorageServiceFactory storageServiceFactory;
    private final FileService fileService;
    private final UserService userService;
    private final TaskService taskService;
    private final FileMapper fileMapper;
    private final LicenseService licenseService;
    private final DocumentService documentService;
    private final AssetService assetService;

    /**
     * Upload one or more files.
     * <p>
     * When the type is something worth indexing — a manual, a parts catalog, a
     * schematic — a {@link com.grash.model.Document} is registered alongside the
     * file and queued for ingestion. That is what turns "we have the PDF
     * somewhere" into "the answer is on page 5-14".
     *
     * @param assetId        attach the resulting documents to one machine
     * @param equipmentClass or to a whole class, when one manual serves N identical machines
     */
    @PostMapping(value = "/upload", produces = "application/json")
    public List<FileShowDTO> handleFileUpload(@RequestParam("files") MultipartFile[] filesReq,
                                              @RequestParam("folder") String folder,
                                              @RequestParam("hidden") String hidden, HttpServletRequest req,
                                              @RequestParam("type") FileType fileType,
                                              @RequestParam(value = "taskId", required = false) Integer taskId,
                                              @RequestParam(value = "assetId", required = false) Long assetId,
                                              @RequestParam(value = "equipmentClass", required = false)
                                              String equipmentClass,
                                              @RequestParam(value = "title", required = false) String title,
                                              @RequestParam(value = "revision", required = false) String revision) {
        if (!licenseService.hasEntitlement(LicenseEntitlement.FILE_ATTACHMENTS))
            throw new CustomException("You need a license to add a file", HttpStatus.FORBIDDEN);
        OwnUser user = userService.whoami(req);
        if (user.getRole().getCreatePermissions().contains(PermissionEntity.FILES) &&
                user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.FILE)) {
            Collection<File> result = new ArrayList<>();
            Arrays.asList(filesReq).forEach(fileReq -> {
                String filePath = storageServiceFactory.getStorageService().upload(fileReq, folder);
                Task task = null;
                if (taskId != null) {
                    Optional<Task> optionalTask = taskService.findById(taskId.longValue());
                    if (optionalTask.isPresent()) {
                        task = optionalTask.get();
                    }
                }
                File saved = fileService.create(new File(fileReq.getOriginalFilename(), filePath, fileType, task,
                        hidden.equals("true")));
                registerDocument(saved, fileReq, fileType, user, assetId, equipmentClass, title, revision);
                result.add(saved);
            });
            return result.stream().map(fileMapper::toShowDto).collect(Collectors.toList());
        } else throw new CustomException("Access Denied", HttpStatus.FORBIDDEN);
    }

    private void registerDocument(File file, MultipartFile upload, FileType fileType, OwnUser user,
                                  Long assetId, String equipmentClass, String title, String revision) {
        if (fileType == null || !fileType.isIndexable()) {
            return;
        }
        Document document = new Document();
        document.setFile(file);
        document.setCompany(user.getCompany());
        document.setDocType(fileType);
        document.setTitle(title != null && !title.isBlank()
                ? title : stripExtension(upload.getOriginalFilename()));
        document.setRevision(revision);
        document.setEquipmentClass(equipmentClass);
        if (assetId != null) {
            assetService.findByIdAndCompany(assetId, user.getCompany().getId()).ifPresent(asset -> {
                document.setAsset(asset);
                if (document.getEquipmentClass() == null) {
                    document.setEquipmentClass(asset.getEquipmentClass());
                }
            });
        }
        try {
            documentService.create(document);
        } catch (Exception e) {
            // The file is safely stored either way; a failed ingest registration
            // must not lose the upload the technician just made.
            log.warn("Could not queue {} for ingestion: {}", file.getName(), e.getMessage());
        }
    }

    private String stripExtension(String filename) {
        if (filename == null) return "Document";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    @PostMapping("/search")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Page<FileShowDTO>> search(@RequestBody SearchCriteria searchCriteria,
                                                    HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (user.getRole().getRoleType().equals(RoleType.ROLE_CLIENT)) {
            if (user.getRole().getViewPermissions().contains(PermissionEntity.FILES)) {
                searchCriteria.filterCompany(user);
                boolean canViewOthers = user.getRole().getViewOtherPermissions().contains(PermissionEntity.FILES);
                if (!canViewOthers) {
                    searchCriteria.filterCreatedBy(user);
                }
                searchCriteria.getFilterFields().add(FilterField.builder()
                        .field("hidden")
                        .value(false)
                        .operation("eq")
                        .values(new ArrayList<>())
                        .alternatives(new ArrayList<>()).build());
            } else throw new CustomException("Access Denied", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(fileService.findBySearchCriteria(searchCriteria).map(fileMapper::toShowDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")

    public FileShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<File> optionalFile = fileService.findById(id);
        if (optionalFile.isPresent()) {
            File savedFile = optionalFile.get();
            if (user.getRole().getViewPermissions().contains(PermissionEntity.FILES) &&
                    (user.getRole().getViewOtherPermissions().contains(PermissionEntity.FILES) || savedFile.getCreatedBy().equals(user.getId()))) {
                return fileMapper.toShowDto(savedFile);
            } else throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public FileShowDTO patch(@Valid @RequestBody FilePatchDTO file,
                             @PathVariable("id") Long id,
                             HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<File> optionalFile = fileService.findById(id);

        if (optionalFile.isPresent()) {
            File savedFile = optionalFile.get();
            if (user.getRole().getEditOtherPermissions().contains(PermissionEntity.FILES) || savedFile.getCreatedBy().equals(user.getId())) {
                savedFile.setName(file.getName());
                return fileMapper.toShowDto(fileService.update(savedFile));
            } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        } else throw new CustomException("File not found", HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);

        Optional<File> optionalFile = fileService.findById(id);
        if (optionalFile.isPresent()) {
            File savedFile = optionalFile.get();
            if (user.getId().equals(savedFile.getCreatedBy())
                    || user.getRole().getDeleteOtherPermissions().contains(PermissionEntity.FILES)) {
                fileService.delete(id);
                return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"),
                        HttpStatus.OK);
            } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        } else throw new CustomException("File not found", HttpStatus.NOT_FOUND);
    }

//    @GetMapping("/download/tos")
//    public byte[] downloadTOS() {
//        return storageServiceFactory.getStorageService().download("terms and privacy/Atlas CMMS Terms of service
//        .pdf");
//    }
//
//    @GetMapping("/download/privacy-policy")
//    public byte[] downloadPrivacyPolicy() {
//        return storageServiceFactory.getStorageService().download("terms and privacy/Atlas CMMS privacy policy.pdf");
//    }
}


