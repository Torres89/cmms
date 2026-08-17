package com.grash.service;

import com.grash.dto.SpecCompletenessDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.AssetSpec;
import com.grash.model.OwnUser;
import com.grash.model.SpecKeyCatalog;
import com.grash.model.enums.SpecSource;
import com.grash.repository.AssetSpecRepository;
import com.grash.repository.SpecKeyCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The typed spec sheet, and the completeness meter over it.
 * <p>
 * The completeness meter is not a vanity metric. Commissioning is billed by the
 * day, so "27 of 34 specs captured" is what makes progress visible to the
 * person paying for it — and what stops a machine being called documented when
 * it is half documented.
 */
@Service
@RequiredArgsConstructor
public class AssetSpecService {

    private final AssetSpecRepository assetSpecRepository;
    private final SpecKeyCatalogRepository specKeyCatalogRepository;

    public List<AssetSpec> findByAsset(Long assetId) {
        return assetSpecRepository.findByAsset_IdOrderBySpecGroupAscSpecKeyAsc(assetId);
    }

    public Collection<AssetSpec> findByAssetAndGroup(Long assetId, String group) {
        return assetSpecRepository.findByAsset_IdAndSpecGroup(assetId, group);
    }

    public Optional<AssetSpec> findById(Long id) {
        return assetSpecRepository.findById(id);
    }

    public AssetSpec create(AssetSpec spec) {
        applyCatalogDefaults(spec);
        return assetSpecRepository.save(spec);
    }

    public AssetSpec save(AssetSpec spec) {
        return assetSpecRepository.save(spec);
    }

    public void delete(Long id) {
        assetSpecRepository.deleteById(id);
    }

    /**
     * Upsert by key — the shape both the extraction pipeline and CSV import need.
     */
    @Transactional
    public AssetSpec upsert(Asset asset, String specKey, String specGroup, String label,
                            String valueText, Double valueNum, String unit,
                            SpecSource source, Double confidence) {
        AssetSpec spec = assetSpecRepository.findByAsset_IdAndSpecKey(asset.getId(), specKey)
                .orElseGet(() -> {
                    AssetSpec fresh = new AssetSpec();
                    fresh.setAsset(asset);
                    fresh.setCompany(asset.getCompany());
                    fresh.setSpecKey(specKey);
                    return fresh;
                });
        // A machine-derived value must never silently overwrite one a person
        // verified. Verification is the whole point of tracking provenance.
        if (spec.isVerified() && source != SpecSource.MANUAL_ENTRY) {
            return spec;
        }
        spec.setSpecGroup(specGroup != null ? specGroup : Optional.ofNullable(spec.getSpecGroup()).orElse("General"));
        if (label != null) spec.setLabel(label);
        spec.setValueText(valueText);
        spec.setValueNum(valueNum);
        if (unit != null) spec.setUnit(unit);
        spec.setSource(source);
        spec.setConfidence(confidence);
        if (source == SpecSource.MANUAL_ENTRY) {
            spec.setVerifiedAt(new Date());
        }
        applyCatalogDefaults(spec);
        return assetSpecRepository.save(spec);
    }

    /**
     * Confirm an extracted value. One click, because the review queue has to be
     * approve-all-then-correct rather than confirm-each.
     */
    @Transactional
    public AssetSpec verify(Long specId, OwnUser user) {
        AssetSpec spec = assetSpecRepository.findById(specId)
                .orElseThrow(() -> new CustomException("Spec not found", HttpStatus.NOT_FOUND));
        spec.setVerifiedBy(user);
        spec.setVerifiedAt(new Date());
        return assetSpecRepository.save(spec);
    }

    /**
     * Withdraw a confirmation. The value stays; only the vouching goes, which
     * puts an extracted value back in the review queue and marks a hand-typed
     * one as no longer stood behind.
     */
    @Transactional
    public AssetSpec unverify(Long specId) {
        AssetSpec spec = assetSpecRepository.findById(specId)
                .orElseThrow(() -> new CustomException("Spec not found", HttpStatus.NOT_FOUND));
        spec.setVerifiedBy(null);
        spec.setVerifiedAt(null);
        return assetSpecRepository.save(spec);
    }

    @Transactional
    public int verifyAll(Collection<Long> specIds, OwnUser user) {
        int count = 0;
        for (Long id : specIds) {
            Optional<AssetSpec> spec = assetSpecRepository.findById(id);
            if (spec.isPresent()) {
                spec.get().setVerifiedBy(user);
                spec.get().setVerifiedAt(new Date());
                assetSpecRepository.save(spec.get());
                count++;
            }
        }
        return count;
    }

    public List<AssetSpec> findUnverified(Long companyId) {
        return assetSpecRepository.findUnverified(companyId);
    }

    /**
     * Fill in label, unit and group from the equipment class catalogue when the
     * caller didn't supply them.
     */
    private void applyCatalogDefaults(AssetSpec spec) {
        Asset asset = spec.getAsset();
        // On create, the spec's own company is not populated until @PrePersist
        // runs, so fall back to the asset's — otherwise every spec created
        // through the API would miss its catalogue label and group.
        Long companyId = spec.getCompany() != null ? spec.getCompany().getId()
                : (asset != null && asset.getCompany() != null ? asset.getCompany().getId() : null);
        if (asset == null || asset.getEquipmentClass() == null || companyId == null) {
            if (spec.getSpecGroup() == null) spec.setSpecGroup("General");
            return;
        }
        Optional<SpecKeyCatalog> catalog = specKeyCatalogRepository
                .findByEquipmentClassAndSpecKeyAndCompany_Id(
                        asset.getEquipmentClass(), spec.getSpecKey(), companyId);
        catalog.ifPresent(entry -> {
            // "General" is the caller not knowing the group, not a choice.
            if (spec.getSpecGroup() == null || "General".equals(spec.getSpecGroup())) {
                spec.setSpecGroup(entry.getSpecGroup());
            }
            if (spec.getLabel() == null) spec.setLabel(entry.getLabelEn());
            if (spec.getUnit() == null) spec.setUnit(entry.getUnit());
        });
        if (spec.getSpecGroup() == null) spec.setSpecGroup("General");
    }

    /**
     * How much of this machine's expected spec sheet actually exists.
     */
    public SpecCompletenessDTO completeness(Asset asset) {
        SpecCompletenessDTO dto = new SpecCompletenessDTO();
        List<AssetSpec> captured = findByAsset(asset.getId());
        Set<String> capturedKeys = captured.stream()
                .filter(s -> s.getValueText() != null || s.getValueNum() != null)
                .map(AssetSpec::getSpecKey)
                .collect(Collectors.toSet());
        dto.setCaptured(capturedKeys.size());
        dto.setVerified((int) captured.stream().filter(AssetSpec::isVerified).count());
        dto.setPendingVerification((int) captured.stream().filter(AssetSpec::isNeedsVerification).count());

        if (asset.getEquipmentClass() == null || asset.getCompany() == null) {
            // No catalogue to measure against: whatever exists is all there is.
            dto.setExpected(capturedKeys.size());
            dto.setMissingKeys(Collections.emptyList());
            return dto;
        }

        List<SpecKeyCatalog> expected = specKeyCatalogRepository
                .findByEquipmentClassAndCompany_IdOrderByDisplayOrderAscSpecGroupAscSpecKeyAsc(
                        asset.getEquipmentClass(), asset.getCompany().getId());
        dto.setExpected(expected.size());
        dto.setRequiredExpected((int) expected.stream().filter(SpecKeyCatalog::isRequired).count());
        dto.setRequiredCaptured((int) expected.stream()
                .filter(SpecKeyCatalog::isRequired)
                .filter(e -> capturedKeys.contains(e.getSpecKey()))
                .count());
        dto.setMissingKeys(expected.stream()
                .filter(e -> !capturedKeys.contains(e.getSpecKey()))
                .map(e -> {
                    SpecCompletenessDTO.MissingKey missing = new SpecCompletenessDTO.MissingKey();
                    missing.setSpecKey(e.getSpecKey());
                    missing.setSpecGroup(e.getSpecGroup());
                    missing.setLabel(e.getLabelEn());
                    missing.setUnit(e.getUnit());
                    missing.setRequired(e.isRequired());
                    return missing;
                })
                .collect(Collectors.toList()));
        return dto;
    }
}
