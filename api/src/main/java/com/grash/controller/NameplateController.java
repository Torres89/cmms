package com.grash.controller;

import com.grash.dto.NameplateExtractionDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.factory.StorageServiceFactory;
import com.grash.model.Asset;
import com.grash.model.AssetSpec;
import com.grash.model.File;
import com.grash.model.OwnUser;
import com.grash.model.SpecKeyCatalog;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.SpecSource;
import com.grash.repository.AssetSpecRepository;
import com.grash.repository.SpecKeyCatalogRepository;
import com.grash.service.AssetService;
import com.grash.service.AssetSpecService;
import com.grash.service.FileService;
import com.grash.service.OcrClient;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nameplate capture: photograph a machine's plate, get a filled-in asset form.
 * <p>
 * Industry reports put nameplate onboarding at seconds versus ten-plus minutes
 * by hand, which makes it both the best demo in the product and the biggest
 * single accelerator during a commissioning visit.
 * <p>
 * It is split in two on purpose. {@code prepare} assembles the evidence — a
 * signed image URL, whatever local CPU OCR could read, and the exact fields
 * this equipment class expects. The caller's own model does the reading.
 * {@code apply} takes the result and writes it as <em>unverified</em> specs,
 * which then show up with a "verify" chip until a person confirms them.
 */
@RestController
@RequestMapping("/assets")
@Tag(name = "nameplate")
@RequiredArgsConstructor
public class NameplateController {

    private final AssetService assetService;
    private final AssetSpecService assetSpecService;
    private final AssetSpecRepository assetSpecRepository;
    private final SpecKeyCatalogRepository specKeyCatalogRepository;
    private final FileService fileService;
    private final StorageServiceFactory storageServiceFactory;
    private final OcrClient ocrClient;
    private final UserService userService;

    @GetMapping("/{id}/nameplate/prepare")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public NameplateExtractionDTO prepare(@PathVariable("id") Long id,
                                          @RequestParam("fileId") Long fileId,
                                          HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Asset asset = requireAsset(id, user);
        File file = fileService.findById(fileId)
                .orElseThrow(() -> new CustomException("File not found", HttpStatus.NOT_FOUND));
        if (!file.getCompany().getId().equals(user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }

        NameplateExtractionDTO dto = new NameplateExtractionDTO();
        dto.setAssetId(asset.getId());
        dto.setAssetName(asset.getName());
        dto.setEquipmentClass(asset.getEquipmentClass());
        dto.setImageUrl(storageServiceFactory.getStorageService().generateSignedUrl(file, 30));
        dto.setOcrText(ocrClient.read(file));

        Map<String, AssetSpec> existing = assetSpecService.findByAsset(asset.getId()).stream()
                .collect(Collectors.toMap(AssetSpec::getSpecKey, spec -> spec, (a, b) -> a));

        List<SpecKeyCatalog> catalog = asset.getEquipmentClass() == null
                ? Collections.emptyList()
                : specKeyCatalogRepository
                .findByEquipmentClassAndCompany_IdOrderByDisplayOrderAscSpecGroupAscSpecKeyAsc(
                        asset.getEquipmentClass(), user.getCompany().getId());

        dto.setExpectedFields(catalog.stream().map(entry -> {
            NameplateExtractionDTO.ExpectedField field = new NameplateExtractionDTO.ExpectedField();
            field.setSpecKey(entry.getSpecKey());
            field.setSpecGroup(entry.getSpecGroup());
            field.setLabel(entry.getLabelEn());
            field.setUnit(entry.getUnit());
            field.setValueType(entry.getValueType() == null ? "TEXT" : entry.getValueType().name());
            field.setRequired(entry.isRequired());
            AssetSpec current = existing.get(entry.getSpecKey());
            if (current != null) {
                field.setCurrentValue(current.getValueText() != null
                        ? current.getValueText()
                        : (current.getValueNum() == null ? null : String.valueOf(current.getValueNum())));
            }
            return field;
        }).collect(Collectors.toList()));

        dto.setResponseSchema(responseSchema());
        dto.setInstructions(
                "Read the nameplate in the image. Return only fields you can actually see on the plate. "
                        + "Leave anything out that you cannot read clearly rather than guessing — an omitted "
                        + "field costs someone thirty seconds of typing, and a wrong one can cost a machine. "
                        + "Use the specKey values given in expectedFields; do not invent keys. Also return "
                        + "manufacturer, model and serialNumber if they are on the plate.");
        return dto;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> specItem = new LinkedHashMap<>();
        specItem.put("type", "object");
        specItem.put("properties", Map.of(
                "specKey", Map.of("type", "string"),
                "value", Map.of("type", "string"),
                "unit", Map.of("type", "string"),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)));
        specItem.put("required", List.of("specKey", "value"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("manufacturer", Map.of("type", "string"));
        properties.put("model", Map.of("type", "string"));
        properties.put("serialNumber", Map.of("type", "string"));
        properties.put("specs", Map.of("type", "array", "items", specItem));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    /**
     * Write an extraction result as unverified specs.
     */
    @PostMapping("/{id}/nameplate/apply")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> apply(@PathVariable("id") Long id,
                                                 @RequestBody NameplateResult result,
                                                 HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Asset asset = requireAsset(id, user);
        requireEditPermission(user);

        int written = 0;
        // Identity fields go on the asset itself, but only where it is currently
        // blank — a plate read is not grounds for overwriting what someone typed.
        if (isBlank(asset.getManufacturer()) && !isBlank(result.getManufacturer())) {
            asset.setManufacturer(result.getManufacturer());
        }
        if (isBlank(asset.getModel()) && !isBlank(result.getModel())) {
            asset.setModel(result.getModel());
        }
        if (isBlank(asset.getSerialNumber()) && !isBlank(result.getSerialNumber())) {
            asset.setSerialNumber(result.getSerialNumber());
        }
        assetService.save(asset);

        for (NameplateResult.SpecValue value : result.getSpecs()) {
            if (isBlank(value.getSpecKey()) || isBlank(value.getValue())) {
                continue;
            }
            Optional<SpecKeyCatalog> catalogEntry = asset.getEquipmentClass() == null
                    ? Optional.empty()
                    : specKeyCatalogRepository.findByEquipmentClassAndSpecKeyAndCompany_Id(
                    asset.getEquipmentClass(), value.getSpecKey(), user.getCompany().getId());

            Double numeric = parseNumber(value.getValue());
            assetSpecService.upsert(
                    asset,
                    value.getSpecKey(),
                    catalogEntry.map(SpecKeyCatalog::getSpecGroup).orElse("Nameplate"),
                    catalogEntry.map(SpecKeyCatalog::getLabelEn).orElse(null),
                    value.getValue(),
                    numeric,
                    value.getUnit() != null ? value.getUnit()
                            : catalogEntry.map(SpecKeyCatalog::getUnit).orElse(null),
                    SpecSource.NAMEPLATE_OCR,
                    value.getConfidence());
            written++;
        }

        return ResponseEntity.ok(new SuccessResponse(true,
                written + " values captured from the nameplate. They are marked unverified "
                        + "until someone confirms them."));
    }

    private Double parseNumber(String raw) {
        try {
            String cleaned = raw.replaceAll("[^0-9.\\-]", "");
            return cleaned.isEmpty() ? null : Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Asset requireAsset(Long id, OwnUser user) {
        return assetService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
    }

    private void requireEditPermission(OwnUser user) {
        if (!user.getRole().getEditOtherPermissions().contains(PermissionEntity.ASSETS)
                && !user.getRole().getCreatePermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * What a vision model returns for a nameplate.
     */
    public static class NameplateResult {
        private String manufacturer;
        private String model;
        private String serialNumber;
        private List<SpecValue> specs = new ArrayList<>();

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public void setSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
        }

        public List<SpecValue> getSpecs() {
            return specs;
        }

        public void setSpecs(List<SpecValue> specs) {
            this.specs = specs == null ? new ArrayList<>() : specs;
        }

        public static class SpecValue {
            private String specKey;
            private String value;
            private String unit;
            private Double confidence;

            public String getSpecKey() {
                return specKey;
            }

            public void setSpecKey(String specKey) {
                this.specKey = specKey;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }

            public String getUnit() {
                return unit;
            }

            public void setUnit(String unit) {
                this.unit = unit;
            }

            public Double getConfidence() {
                return confidence;
            }

            public void setConfidence(Double confidence) {
                this.confidence = confidence;
            }
        }
    }
}
