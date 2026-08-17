package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.pack.AssetPackDTO;
import com.grash.dto.pack.PackInstantiationResultDTO;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vertical packs: loading them, and instantiating them onto a machine.
 * <p>
 * Instantiating a pack is the first step of commissioning. It creates the
 * equipment breakdown structure, the spec-key catalogue, the meters, the PM
 * templates with their multi-counter intervals, the failure-mode catalogue and
 * the consumable BOM lines — so roughly 80 % of a machine's structure exists
 * before anyone types anything, and the rest is filling in values with the
 * customer watching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetPackService {

    private static final String PACK_LOCATION = "classpath:packs/*.json";

    private final ObjectMapper objectMapper;
    private final AssetRepository assetRepository;
    private final AssetSpecRepository assetSpecRepository;
    private final SpecKeyCatalogRepository specKeyCatalogRepository;
    private final MeterRepository meterRepository;
    private final PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    private final MaintenanceIntervalRepository maintenanceIntervalRepository;
    private final FailureModeRepository failureModeRepository;
    private final AssetBomLineRepository assetBomLineRepository;
    private final PartRepository partRepository;
    private final ReadingRepository readingRepository;

    private final Map<String, AssetPackDTO> packs = new LinkedHashMap<>();

    @PostConstruct
    void loadPacks() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PACK_LOCATION);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    AssetPackDTO pack = objectMapper.readValue(in, AssetPackDTO.class);
                    if (pack.getKey() == null) {
                        log.warn("Skipping pack {} — no key", resource.getFilename());
                        continue;
                    }
                    packs.put(pack.getKey(), pack);
                }
            }
            log.info("Loaded {} asset packs: {}", packs.size(), packs.keySet());
        } catch (Exception e) {
            log.warn("Could not load asset packs: {}", e.getMessage());
        }
    }

    public Collection<AssetPackDTO> findAll() {
        return packs.values();
    }

    public Optional<AssetPackDTO> findByKey(String key) {
        return Optional.ofNullable(packs.get(key));
    }

    /**
     * Register a pack supplied by a customer at runtime.
     * <p>
     * Deliberately possible without a deploy — a customer with an unusual
     * machine gets a JSON file the same day, not a release.
     */
    public AssetPackDTO register(AssetPackDTO pack) {
        if (pack.getKey() == null || pack.getKey().isBlank()) {
            throw new CustomException("A pack needs a key", HttpStatus.BAD_REQUEST);
        }
        packs.put(pack.getKey(), pack);
        return pack;
    }

    /**
     * Build out a machine from its pack.
     *
     * @param dryRun report what would be created without creating it — useful
     *               when standing in front of a customer deciding whether to run it
     */
    @Transactional
    public PackInstantiationResultDTO instantiate(String key, Asset asset, OwnUser user, boolean dryRun) {
        AssetPackDTO pack = findByKey(key)
                .orElseThrow(() -> new CustomException("Unknown pack: " + key, HttpStatus.NOT_FOUND));
        Company company = asset.getCompany();
        PackInstantiationResultDTO result = new PackInstantiationResultDTO();
        result.setPackKey(pack.getKey());
        result.setPackVersion(pack.getVersion());
        result.setAssetId(asset.getId());
        result.setDryRun(dryRun);

        if (!dryRun) {
            asset.setEquipmentClass(pack.getKey());
            if (asset.getLevel() == null) {
                asset.setLevel(AssetLevel.EQUIPMENT);
            }
            assetRepository.save(asset);
        }

        // 1. Spec-key catalogue — the thing the completeness meter measures against.
        for (AssetPackDTO.SpecKey specKey : pack.getSpecKeys()) {
            boolean exists = specKeyCatalogRepository
                    .findByEquipmentClassAndSpecKeyAndCompany_Id(pack.getKey(), specKey.getKey(),
                            company.getId())
                    .isPresent();
            if (exists) {
                continue;
            }
            result.getSpecKeys().add(specKey.getKey());
            if (dryRun) continue;
            SpecKeyCatalog entry = new SpecKeyCatalog();
            entry.setCompany(company);
            entry.setEquipmentClass(pack.getKey());
            entry.setSpecGroup(specKey.getGroup() == null ? "General" : specKey.getGroup());
            entry.setSpecKey(specKey.getKey());
            entry.setLabelEn(specKey.getLabelEn());
            entry.setLabelEs(specKey.getLabelEs());
            entry.setUnit(specKey.getUnit());
            entry.setValueType(parseEnum(SpecValueType.class, specKey.getType(), SpecValueType.TEXT));
            entry.setRequired(specKey.isRequired());
            entry.setDisplayOrder(specKey.getOrder());
            entry.setSystemSeeded(true);
            specKeyCatalogRepository.save(entry);
        }

        // 2. Failure-mode catalogue.
        for (AssetPackDTO.FailureModeTemplate template : pack.getFailureModes()) {
            if (failureModeRepository.findByCodeAndCompany_Id(template.getCode(), company.getId()).isPresent()) {
                continue;
            }
            result.getFailureModes().add(template.getCode());
            if (dryRun) continue;
            FailureMode mode = new FailureMode();
            mode.setCompany(company);
            mode.setEquipmentClass(pack.getKey());
            mode.setSubunit(template.getSubunit());
            mode.setCode(template.getCode());
            mode.setNameEn(template.getNameEn());
            mode.setNameEs(template.getNameEs());
            mode.setDescription(template.getDescription());
            mode.setTypicalMechanism(template.getTypicalMechanism());
            mode.setTypicalCauses(template.getTypicalCauses());
            mode.setDetectionMethods(template.getDetectionMethods());
            mode.setSeverityDefault(template.getSeverityDefault());
            mode.setSystemSeeded(true);
            failureModeRepository.save(mode);
        }

        // 3. Equipment breakdown structure — sub-assets under the machine.
        Map<String, Asset> positions = new LinkedHashMap<>();
        for (AssetPackDTO.EbsNode node : pack.getEbs()) {
            createEbs(node, asset, company, positions, result, dryRun);
        }

        // 4. Meters.
        Map<String, Meter> meters = new LinkedHashMap<>();
        for (AssetPackDTO.MeterTemplate template : pack.getMeters()) {
            Optional<Meter> existing = meterRepository.findByAsset_Id(asset.getId()).stream()
                    .filter(meter -> template.getName().equalsIgnoreCase(meter.getName()))
                    .findFirst();
            boolean usageBasis = Boolean.TRUE.equals(template.getUsageBasis());
            if (existing.isPresent()) {
                Meter meter = existing.get();
                // Re-instantiating is also how a pack revision states which
                // meter is the usage basis, so let it correct an older record.
                if (usageBasis && !meter.isUsageBasis() && !dryRun) {
                    meter.setUsageBasis(true);
                    meterRepository.save(meter);
                }
                meters.put(template.getName(), meter);
                continue;
            }
            result.getMeters().add(template.getName());
            if (dryRun) continue;
            Meter meter = new Meter();
            meter.setCompany(company);
            meter.setAsset(asset);
            meter.setName(template.getName());
            meter.setUnit(template.getUnit());
            meter.setUsageBasis(usageBasis);
            meter.setUpdateFrequency(template.getUpdateFrequency() == null ? 30 : template.getUpdateFrequency());
            meters.put(template.getName(), meterRepository.save(meter));
        }

        // 5. Preventive maintenance with its multi-counter intervals.
        //
        // Instantiating a pack twice is a normal thing to do — a pack gets a new
        // revision, or someone re-runs commissioning — and it must not leave the
        // machine with two of every service. Positions and meters above already
        // match on their natural key; PMs match on the template they came from.
        Set<String> existingPmTemplates = preventiveMaintenanceRepository
                .findByAsset_Id(asset.getId()).stream()
                .map(PreventiveMaintenance::getTemplateKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (AssetPackDTO.PmTemplate template : pack.getPmTemplates()) {
            if (existingPmTemplates.contains(template.getTitle())) continue;
            result.getPreventiveMaintenances().add(template.getTitle());
            if (dryRun) continue;
            PreventiveMaintenance pm = new PreventiveMaintenance();
            pm.setCompany(company);
            pm.setAsset(asset);
            pm.setName(template.getTitle());
            pm.setTitle(template.getTitle());
            pm.setDescription(buildDescription(template));
            pm.setPriority(parseEnum(Priority.class, template.getPriority(), Priority.MEDIUM));
            pm.setTriggerMode(parseEnum(TriggerMode.class, template.getTriggerMode(),
                    TriggerMode.WHICHEVER_FIRST));
            pm.setEquipmentClass(pack.getKey());
            pm.setTemplateKey(template.getTitle());
            PreventiveMaintenance savedPm = preventiveMaintenanceRepository.save(pm);

            for (AssetPackDTO.IntervalTemplate intervalTemplate : template.getIntervals()) {
                MaintenanceInterval interval = new MaintenanceInterval();
                interval.setCompany(company);
                interval.setPreventiveMaintenance(savedPm);
                interval.setBasis(parseEnum(IntervalBasis.class, intervalTemplate.getBasis(),
                        IntervalBasis.CALENDAR));
                interval.setIntervalValue(intervalTemplate.getValue());
                interval.setUnit(intervalTemplate.getUnit());
                if (intervalTemplate.getWarnAtPercent() != null) {
                    interval.setWarnAtPercent(intervalTemplate.getWarnAtPercent());
                }
                if (interval.getBasis() == IntervalBasis.METER && intervalTemplate.getMeter() != null) {
                    Meter meter = meters.get(intervalTemplate.getMeter());
                    if (meter == null) {
                        // A meter interval with no meter would silently never
                        // fire, which is worse than not creating it.
                        log.warn("Pack {} references meter '{}' that was not created; skipping interval",
                                pack.getKey(), intervalTemplate.getMeter());
                        continue;
                    }
                    interval.setMeter(meter);
                    // Baseline against where the meter is now. A machine with
                    // 11,840 hours on it that was commissioned into the system
                    // this morning is not 2,300 % overdue for its 500-hour
                    // service — it just has no recorded history yet, and saying
                    // otherwise trains people to ignore the due list.
                    interval.setLastCompletedValue(latestReading(meter));
                }
                // Counters start now: the machine has just been commissioned.
                interval.setLastCompletedAt(new Date());
                maintenanceIntervalRepository.save(interval);
            }
        }

        // 6. Consumables as BOM lines, with a placeholder part each.
        for (AssetPackDTO.Consumable consumable : pack.getConsumables()) {
            result.getConsumables().add(consumable.getName());
            if (dryRun) continue;
            Part part = partRepository
                    .findByNameIgnoreCaseAndCompany_Id(consumable.getName(), company.getId())
                    .orElseGet(() -> {
                        Part fresh = new Part();
                        fresh.setCompany(company);
                        fresh.setName(consumable.getName());
                        fresh.setUnit(consumable.getUnit());
                        fresh.setNonStock(true);
                        // Flagged for stocking but not yet sourced — the
                        // commissioning visit fills in supplier and price.
                        fresh.setStockRecommended(true);
                        return partRepository.save(fresh);
                    });

            AssetBomLine line = new AssetBomLine();
            line.setCompany(company);
            line.setAsset(positions.getOrDefault(consumable.getPositionCode(), asset));
            line.setPart(part);
            line.setPositionCode(consumable.getPositionCode());
            line.setQtyPerAssembly(consumable.getQtyPerAssembly() == null ? 1.0
                    : consumable.getQtyPerAssembly());
            line.setConsumable(true);
            line.setReplaceIntervalHours(consumable.getReplaceIntervalHours());
            line.setReplaceIntervalMonths(consumable.getReplaceIntervalMonths());
            assetBomLineRepository.save(line);
        }

        return result;
    }

    /**
     * The meter's current value, or 0 when it has no readings yet.
     */
    private Double latestReading(Meter meter) {
        return readingRepository.findByMeter_Id(meter.getId()).stream()
                .max(Comparator.comparing(Reading::getCreatedAt))
                .map(Reading::getValue)
                .orElse(0d);
    }

    private void createEbs(AssetPackDTO.EbsNode node, Asset parent, Company company,
                           Map<String, Asset> positions, PackInstantiationResultDTO result,
                           boolean dryRun) {
        // Don't duplicate a position that already exists under this parent.
        Optional<Asset> existing = assetRepository
                .findByParentAsset_Id(parent.getId(), Sort.by("name")).stream()
                .filter(child -> node.getPositionCode() != null
                        && node.getPositionCode().equals(child.getPositionCode()))
                .findFirst();

        Asset created;
        if (existing.isPresent()) {
            created = existing.get();
        } else {
            result.getPositions().add(node.getPositionCode() + " — " + node.getName());
            if (dryRun) {
                node.getChildren().forEach(child ->
                        createEbs(child, parent, company, positions, result, true));
                return;
            }
            Asset child = new Asset();
            child.setCompany(company);
            child.setParentAsset(parent);
            child.setName(node.getName());
            child.setPositionCode(node.getPositionCode());
            child.setFunctionalDescription(node.getFunctionalDescription());
            child.setLevel(parseEnum(AssetLevel.class, node.getLevel(), AssetLevel.SUBUNIT));
            child.setTrackingClass(parseEnum(TrackingClass.class, node.getTrackingClass(),
                    TrackingClass.NON_TRACKED));
            child.setEquipmentClass(parent.getEquipmentClass());
            child.setLocation(parent.getLocation());
            child.setCriticality(node.getCriticality() != null ? node.getCriticality()
                    : (node.isCritical() ? 5 : null));
            created = assetRepository.save(child);
        }

        if (node.getPositionCode() != null) {
            positions.put(node.getPositionCode(), created);
        }
        for (AssetPackDTO.EbsNode child : node.getChildren()) {
            createEbs(child, created, company, positions, result, dryRun);
        }
    }

    private String buildDescription(AssetPackDTO.PmTemplate template) {
        StringBuilder description = new StringBuilder();
        if (template.getDescription() != null) {
            description.append(template.getDescription()).append("\n\n");
        }
        template.getTasks().forEach(task -> description.append("• ").append(task).append("\n"));
        return description.toString().trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown {} value '{}' in a pack; using {}", type.getSimpleName(), value, fallback);
            return fallback;
        }
    }
}
