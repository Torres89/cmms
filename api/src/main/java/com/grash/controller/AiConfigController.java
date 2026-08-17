package com.grash.controller;

import com.grash.dto.AiConfigDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.CompanySettings;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.CompanySettingsService;
import com.grash.service.SecretEncryptionService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Door 2 and Door 3 configuration: which model this company uses and whose key
 * pays for it.
 * <p>
 * The key goes in and never comes back out. Reads return the provider, the
 * model and a masked suffix — enough for someone to recognise which key is
 * installed, and nothing more.
 */
@RestController
@RequestMapping("/ai-config")
@Tag(name = "aiConfig")
@RequiredArgsConstructor
public class AiConfigController {

    private final CompanySettingsService companySettingsService;
    private final SecretEncryptionService secretEncryptionService;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AiConfigDTO get(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        CompanySettings settings = settings(user);
        AiConfigDTO dto = new AiConfigDTO();
        dto.setProvider(settings.getAiProvider() == null ? "NONE" : settings.getAiProvider());
        dto.setModel(settings.getAiModel());
        dto.setBaseUrl(settings.getAiBaseUrl());
        dto.setMonthlyTokenCap(settings.getAiMonthlyTokenCap());
        String key = secretEncryptionService.decrypt(settings.getAiApiKeyEncrypted());
        dto.setApiKeyMasked(secretEncryptionService.mask(key));
        dto.setKeyConfigured(key != null && !key.isBlank());
        return dto;
    }

    @PatchMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AiConfigDTO update(@RequestBody AiConfigDTO request, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        CompanySettings settings = settings(user);

        if (request.getProvider() != null) {
            settings.setAiProvider(request.getProvider().toUpperCase());
        }
        if (request.getModel() != null) {
            settings.setAiModel(request.getModel().isBlank() ? null : request.getModel());
        }
        if (request.getBaseUrl() != null) {
            settings.setAiBaseUrl(request.getBaseUrl().isBlank() ? null : request.getBaseUrl());
        }
        settings.setAiMonthlyTokenCap(request.getMonthlyTokenCap());

        // A blank string means "clear it"; absent means "leave it alone", so a
        // settings screen can save other fields without re-entering the key.
        if (request.getApiKey() != null) {
            settings.setAiApiKeyEncrypted(request.getApiKey().isBlank()
                    ? null : secretEncryptionService.encrypt(request.getApiKey().trim()));
        }
        companySettingsService.update(settings);
        return get(req);
    }

    @DeleteMapping("/key")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> clearKey(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireSettingsPermission(user);
        CompanySettings settings = settings(user);
        settings.setAiApiKeyEncrypted(null);
        companySettingsService.update(settings);
        return ResponseEntity.ok(new SuccessResponse(true, "API key removed"));
    }

    private CompanySettings settings(OwnUser user) {
        CompanySettings settings = user.getCompany().getCompanySettings();
        if (settings == null) {
            throw new CustomException("Company settings not found", HttpStatus.NOT_FOUND);
        }
        return settings;
    }

    private void requireSettingsPermission(OwnUser user) {
        if (!user.isOwnsCompany()
                && !user.getRole().getEditOtherPermissions().contains(PermissionEntity.SETTINGS)) {
            throw new CustomException("Only an administrator can change AI settings", HttpStatus.FORBIDDEN);
        }
    }
}
