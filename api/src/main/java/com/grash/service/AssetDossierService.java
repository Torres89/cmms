package com.grash.service;

import com.grash.dto.AssetDossierDTO;
import com.grash.dto.IntervalStatusDTO;
import com.grash.model.*;
import com.grash.model.enums.AssetLevel;
import com.grash.model.enums.ComponentEventType;
import com.grash.model.enums.IngestStatus;
import com.grash.model.enums.Status;
import com.grash.repository.ComponentEventRepository;
import com.grash.repository.DocumentRepository;
import com.grash.repository.PreventiveMaintenanceRepository;
import com.grash.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The machine dossier — the single most important thing in the system.
 * <p>
 * Everything else is a source: the spec sheet, the component ledger, the
 * meters, the PM counters, the failure history, the document index. This puts
 * them together into one compact, deterministic answer to "what is true about
 * this machine right now", which is exactly the question retrieval is worst at.
 * <p>
 * It is served two ways from one computation: JSON for the dossier page, and a
 * few-hundred-token text card for AI clients. The text card is what an MCP
 * client reads and what the in-app chat injects on every turn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetDossierService {

    private static final int RECENT_FAILURE_MONTHS = 24;
    private static final int MAX_KEY_SPECS = 12;

    private final AssetService assetService;
    private final AssetSpecService assetSpecService;
    private final ComponentService componentService;
    private final ComponentEventRepository componentEventRepository;
    private final MeterService meterService;
    private final ReadingRepository readingRepository;
    private final PreventiveMaintenanceRepository preventiveMaintenanceRepository;
    private final MaintenanceIntervalService maintenanceIntervalService;
    private final FailureService failureService;
    private final DocumentRepository documentRepository;
    private final WorkOrderService workOrderService;

    public AssetDossierDTO build(Asset asset) {
        AssetDossierDTO dossier = new AssetDossierDTO();

        dossier.setId(asset.getId());
        dossier.setName(asset.getName());
        dossier.setCustomId(asset.getCustomId());
        dossier.setModel(asset.getModel());
        dossier.setManufacturer(asset.getManufacturer());
        dossier.setSerialNumber(asset.getSerialNumber());
        dossier.setEquipmentClass(asset.getEquipmentClass());
        dossier.setLevel(asset.getLevel() == null ? AssetLevel.EQUIPMENT.name() : asset.getLevel().name());
        dossier.setStatus(asset.getStatus() == null ? null : asset.getStatus().name());
        dossier.setLocationPath(locationPath(asset));
        dossier.setArea(asset.getArea());
        dossier.setInServiceDate(asset.getInServiceDate());
        dossier.setWarrantyExpirationDate(asset.getWarrantyExpirationDate());
        dossier.setCriticality(asset.getCriticality());
        dossier.setDowntimeCostPerHour(asset.getDowntimeCostPerHour());
        dossier.setReplacementCost(asset.getReplacementCost());
        dossier.setDescription(asset.getDescription());
        dossier.setFunctionalDescription(asset.getFunctionalDescription());

        dossier.setMeters(meterSummaries(asset));
        dossier.setKeySpecs(specSummaries(asset));
        dossier.setSpecCompleteness(assetSpecService.completeness(asset));
        dossier.setComponents(componentSummaries(asset));
        dossier.setStructure(structure(asset));
        dossier.setUpcomingMaintenance(pmSummaries(asset));
        dossier.setOpenWorkOrders(openWorkOrders(asset));
        dossier.setRecentFailures(recentFailures(asset));
        dossier.setDocuments(documentSummaries(asset));

        dossier.setText(render(dossier));
        return dossier;
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private String locationPath(Asset asset) {
        Location location = asset.getLocation();
        if (location == null) {
            return null;
        }
        Deque<String> parts = new ArrayDeque<>();
        Location current = location;
        int guard = 0;  // locations are a tree, but never trust a tree you didn't build
        while (current != null && guard++ < 10) {
            parts.addFirst(current.getName());
            current = current.getParentLocation();
        }
        return String.join(" › ", parts);
    }

    private List<AssetDossierDTO.MeterReadingSummary> meterSummaries(Asset asset) {
        return meterService.findByAsset(asset.getId()).stream().map(meter -> {
            AssetDossierDTO.MeterReadingSummary summary = new AssetDossierDTO.MeterReadingSummary();
            summary.setMeterId(meter.getId());
            summary.setName(meter.getName());
            summary.setUnit(meter.getUnit());
            summary.setUpdateFrequency(meter.getUpdateFrequency());
            readingRepository.findByMeter_Id(meter.getId()).stream()
                    .max(Comparator.comparing(Reading::getCreatedAt))
                    .ifPresent(reading -> {
                        summary.setLastValue(reading.getValue());
                        summary.setLastReadingAt(reading.getCreatedAt());
                        long daysSince = (System.currentTimeMillis() - reading.getCreatedAt().getTime())
                                / (1000L * 60 * 60 * 24);
                        summary.setOverdue(meter.getUpdateFrequency() > 0
                                && daysSince > meter.getUpdateFrequency());
                    });
            return summary;
        }).collect(Collectors.toList());
    }

    private List<AssetDossierDTO.SpecSummary> specSummaries(Asset asset) {
        return assetSpecService.findByAsset(asset.getId()).stream()
                .filter(spec -> spec.getValueText() != null || spec.getValueNum() != null)
                .map(spec -> {
                    AssetDossierDTO.SpecSummary summary = new AssetDossierDTO.SpecSummary();
                    summary.setId(spec.getId());
                    summary.setSpecGroup(spec.getSpecGroup());
                    summary.setSpecKey(spec.getSpecKey());
                    summary.setLabel(spec.getLabel() != null ? spec.getLabel() : spec.getSpecKey());
                    summary.setValue(formatSpecValue(spec));
                    summary.setUnit(spec.getUnit());
                    summary.setSource(spec.getSource() == null ? null : spec.getSource().name());
                    summary.setVerified(spec.isVerified());
                    summary.setConfidence(spec.getConfidence());
                    summary.setSourcePage(spec.getSourcePage());
                    if (spec.getSourceDocument() != null) {
                        summary.setSourceDocumentTitle(spec.getSourceDocument().getTitle());
                    }
                    return summary;
                })
                .collect(Collectors.toList());
    }

    private String formatSpecValue(AssetSpec spec) {
        if (spec.getValueText() != null) {
            return spec.getValueText();
        }
        if (spec.getValueNum() == null) {
            return null;
        }
        // Machine-tool accuracy figures live below three decimal places —
        // rounding 0.0051 mm to "0.005" throws away the part that matters.
        double value = spec.getValueNum();
        if (value != 0 && Math.abs(value) < 0.01) {
            return new DecimalFormat("#,##0.#####").format(value);
        }
        return new DecimalFormat("#,##0.###").format(value);
    }

    private List<AssetDossierDTO.ComponentSummary> componentSummaries(Asset asset) {
        return componentService.findInstalledInSubtree(asset.getId()).stream().map(component -> {
            AssetDossierDTO.ComponentSummary summary = new AssetDossierDTO.ComponentSummary();
            summary.setId(component.getId());
            summary.setSerialNumber(component.getSerialNumber());
            summary.setName(component.getPartType() == null ? null : component.getPartType().getName());
            summary.setStatus(component.getStatus() == null ? null : component.getStatus().name());
            summary.setTotalHours(component.getTotalHours());
            summary.setHoursSinceOverhaul(component.getHoursSinceOverhaul());
            summary.setHourLimit(component.getHourLimit());
            summary.setTotalCycles(component.getTotalCycles());
            summary.setCycleLimit(component.getCycleLimit());
            summary.setCalendarLimitMonths(component.getCalendarLimitMonths());
            summary.setRemainingLifeFraction(component.getRemainingLifeFraction());
            if (component.getCurrentPosition() != null) {
                summary.setPositionId(component.getCurrentPosition().getId());
                summary.setPositionCode(component.getCurrentPosition().getPositionCode());
                summary.setPositionName(component.getCurrentPosition().getName());
            }
            componentEventRepository
                    .findFirstByComponent_IdAndTypeOrderByOccurredAtDesc(component.getId(),
                            ComponentEventType.INSTALLED)
                    .ifPresent(event -> summary.setInstalledAt(event.getOccurredAt()));
            return summary;
        }).collect(Collectors.toList());
    }

    private List<AssetDossierDTO.StructureNode> structure(Asset asset) {
        return childrenOf(asset.getId(), 0);
    }

    private List<AssetDossierDTO.StructureNode> childrenOf(Long parentId, int depth) {
        if (depth > 5) {  // the ISO taxonomy is nine levels; five below the machine is plenty
            return Collections.emptyList();
        }
        return assetService.findAssetChildren(parentId, org.springframework.data.domain.Sort.by("name"))
                .stream().map(child -> {
                    AssetDossierDTO.StructureNode node = new AssetDossierDTO.StructureNode();
                    node.setId(child.getId());
                    node.setName(child.getName());
                    node.setPositionCode(child.getPositionCode());
                    node.setLevel(child.getLevel() == null ? null : child.getLevel().name());
                    node.setTrackingClass(child.getTrackingClass() == null ? null
                            : child.getTrackingClass().name());
                    node.setCriticality(child.getCriticality());
                    node.setChildren(childrenOf(child.getId(), depth + 1));
                    return node;
                }).collect(Collectors.toList());
    }

    private List<AssetDossierDTO.PmSummary> pmSummaries(Asset asset) {
        List<PreventiveMaintenance> pms;
        try {
            pms = preventiveMaintenanceRepository.findInAssetSubtree(asset.getId());
        } catch (Exception e) {
            // Recursive CTE unsupported (e.g. an H2 test harness) — fall back to
            // the machine's own PMs rather than failing the whole dossier.
            log.debug("Falling back to direct PM lookup for asset {}: {}", asset.getId(), e.getMessage());
            pms = preventiveMaintenanceRepository.findByAsset_Id(asset.getId());
        }
        return pms.stream().map(pm -> {
            IntervalStatusDTO status = maintenanceIntervalService.status(pm);
            AssetDossierDTO.PmSummary summary = new AssetDossierDTO.PmSummary();
            summary.setId(pm.getId());
            summary.setTitle(status.getTitle());
            summary.setTriggerMode(status.getTriggerMode());
            summary.setPercent(status.getPercent());
            summary.setDue(status.isDue());
            summary.setWarning(status.isWarning());
            summary.setDrivingCounter(status.getDrivingCounter());
            summary.setRemaining(status.getRemaining());
            summary.setRemainingUnit(status.getRemainingUnit());
            if (pm.getSchedule() != null) {
                summary.setNextDueDate(pm.getSchedule().getStartsOn());
            }
            return summary;
        })
                // Closest to firing first — that is the order a planner reads in.
                .sorted(Comparator.comparing(
                        (AssetDossierDTO.PmSummary s) -> s.getPercent() == null ? -1 : s.getPercent()).reversed())
                .collect(Collectors.toList());
    }

    private List<AssetDossierDTO.WorkOrderSummary> openWorkOrders(Asset asset) {
        return workOrderService.findByAsset(asset.getId()).stream()
                .filter(workOrder -> workOrder.getStatus() != Status.COMPLETE)
                .map(workOrder -> {
                    AssetDossierDTO.WorkOrderSummary summary = new AssetDossierDTO.WorkOrderSummary();
                    summary.setId(workOrder.getId());
                    summary.setTitle(workOrder.getTitle());
                    summary.setStatus(workOrder.getStatus() == null ? null : workOrder.getStatus().name());
                    summary.setPriority(workOrder.getPriority() == null ? null : workOrder.getPriority().name());
                    summary.setDueDate(workOrder.getDueDate());
                    if (workOrder.getPrimaryUser() != null) {
                        summary.setAssignedTo(workOrder.getPrimaryUser().getFirstName());
                    }
                    return summary;
                })
                .collect(Collectors.toList());
    }

    private List<AssetDossierDTO.FailureSummary> recentFailures(Asset asset) {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.MONTH, -RECENT_FAILURE_MONTHS);
        return failureService.findEventsForAssetSince(asset.getId(), cutoff.getTime()).stream()
                .map(event -> {
                    AssetDossierDTO.FailureSummary summary = new AssetDossierDTO.FailureSummary();
                    summary.setId(event.getId());
                    if (event.getFailureMode() != null) {
                        summary.setCode(event.getFailureMode().getCode());
                        summary.setName(event.getFailureMode().getNameEn());
                    }
                    summary.setOccurredAt(FailureService.when(event));
                    summary.setDowntimeMinutes(event.getDowntimeMinutes());
                    summary.setSeverity(event.getSeverity());
                    summary.setCause(event.getCause());
                    summary.setCorrectiveAction(event.getCorrectiveAction());
                    return summary;
                })
                .collect(Collectors.toList());
    }

    private List<AssetDossierDTO.DocumentSummary> documentSummaries(Asset asset) {
        List<Document> documents = new ArrayList<>(documentRepository.findByAsset_Id(asset.getId()));
        if (asset.getEquipmentClass() != null && asset.getCompany() != null) {
            // One manual can serve every identical machine in the shop.
            documentRepository
                    .findByEquipmentClassAndCompany_Id(asset.getEquipmentClass(), asset.getCompany().getId())
                    .stream()
                    .filter(document -> documents.stream().noneMatch(d -> d.getId().equals(document.getId())))
                    .forEach(documents::add);
        }
        return documents.stream().map(document -> {
            AssetDossierDTO.DocumentSummary summary = new AssetDossierDTO.DocumentSummary();
            summary.setId(document.getId());
            summary.setTitle(document.getTitle());
            summary.setDocType(document.getDocType() == null ? null : document.getDocType().name());
            summary.setRevision(document.getRevision());
            summary.setPageCount(document.getPageCount());
            summary.setIngestStatus(document.getIngestStatus() == null
                    ? IngestStatus.PENDING.name() : document.getIngestStatus().name());
            summary.setChunkCount(document.getChunkCount());
            return summary;
        }).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // The text card
    // ------------------------------------------------------------------

    /**
     * The dossier as a few hundred tokens of plain text.
     * <p>
     * Written for a model to read: dense, no markup, every line a fact. Sections
     * with nothing in them are omitted rather than shown empty, because "no
     * documents indexed" is better said once than implied by a blank heading.
     */
    private String render(AssetDossierDTO d) {
        SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd");
        DecimalFormat number = new DecimalFormat("#,##0.##");
        StringBuilder out = new StringBuilder();

        out.append("MACHINE: ").append(d.getName());
        // Shops routinely name the asset after its model, so only add the model
        // when it says something the name doesn't.
        if (d.getModel() != null && !d.getName().toLowerCase(Locale.ROOT)
                .contains(d.getModel().toLowerCase(Locale.ROOT))) {
            out.append(" ").append(d.getModel());
        }
        out.append("  (asset #").append(d.getId());
        if (d.getEquipmentClass() != null) out.append(", ").append(d.getEquipmentClass());
        out.append(")\n");

        List<String> identity = new ArrayList<>();
        if (d.getManufacturer() != null) identity.add(d.getManufacturer());
        if (d.getSerialNumber() != null) identity.add("Serial " + d.getSerialNumber());
        if (d.getInServiceDate() != null) identity.add("in service " + ymd.format(d.getInServiceDate()));
        if (d.getWarrantyExpirationDate() != null) {
            boolean expired = d.getWarrantyExpirationDate().before(new Date());
            identity.add("warranty " + (expired ? "expired " : "to ")
                    + ymd.format(d.getWarrantyExpirationDate()));
        }
        if (d.getCriticality() != null) identity.add("criticality " + d.getCriticality() + "/5");
        if (d.getDowntimeCostPerHour() != null) {
            identity.add("downtime cost " + number.format(d.getDowntimeCostPerHour()) + "/h");
        }
        if (!identity.isEmpty()) out.append(String.join(" · ", identity)).append("\n");

        out.append("Status: ").append(d.getStatus() == null ? "UNKNOWN" : d.getStatus());
        if (d.getLocationPath() != null) out.append("   Location: ").append(d.getLocationPath());
        out.append("\n");

        if (!d.getMeters().isEmpty()) {
            out.append("Meters: ").append(d.getMeters().stream().map(m -> {
                StringBuilder line = new StringBuilder(m.getName());
                if (m.getLastValue() != null) {
                    line.append(" ").append(number.format(m.getLastValue()));
                    if (m.getUnit() != null) line.append(" ").append(m.getUnit());
                    if (m.getLastReadingAt() != null) {
                        line.append(" (").append(ymd.format(m.getLastReadingAt()));
                        if (m.isOverdue()) line.append(", reading overdue");
                        line.append(")");
                    }
                } else {
                    line.append(" (no readings)");
                }
                return line.toString();
            }).collect(Collectors.joining(" · "))).append("\n");
        }

        if (!d.getKeySpecs().isEmpty()) {
            out.append("Key specs: ").append(d.getKeySpecs().stream()
                    .limit(MAX_KEY_SPECS)
                    .map(spec -> {
                        StringBuilder line = new StringBuilder(spec.getLabel()).append(" ").append(spec.getValue());
                        if (spec.getUnit() != null) line.append(" ").append(spec.getUnit());
                        if (!spec.isVerified()) line.append(" [unverified]");
                        return line.toString();
                    })
                    .collect(Collectors.joining(" · "))).append("\n");
        }
        if (d.getSpecCompleteness() != null && d.getSpecCompleteness().getExpected() > 0) {
            out.append("Spec sheet: ").append(d.getSpecCompleteness().getCaptured())
                    .append(" of ").append(d.getSpecCompleteness().getExpected()).append(" captured");
            if (d.getSpecCompleteness().getPendingVerification() > 0) {
                out.append(", ").append(d.getSpecCompleteness().getPendingVerification())
                        .append(" awaiting verification");
            }
            out.append("\n");
        }

        if (!d.getComponents().isEmpty()) {
            out.append("Installed serialized components:\n");
            for (AssetDossierDTO.ComponentSummary c : d.getComponents()) {
                out.append("  ");
                if (c.getPositionCode() != null) out.append(c.getPositionCode()).append("  ");
                out.append(c.getName() == null ? "Component" : c.getName());
                out.append(" SN ").append(c.getSerialNumber());
                if (c.getTotalHours() != null && c.getTotalHours() > 0) {
                    out.append(" — ").append(number.format(c.getTotalHours())).append(" h");
                }
                if (c.getHourLimit() != null) {
                    out.append(" (limit ").append(number.format(c.getHourLimit())).append(" h)");
                }
                if (c.getRemainingLifeFraction() != null) {
                    out.append(" — ").append(Math.round(c.getRemainingLifeFraction() * 100)).append(" % remaining");
                }
                out.append("\n");
            }
        }

        List<AssetDossierDTO.PmSummary> notable = d.getUpcomingMaintenance().stream()
                .filter(pm -> pm.isDue() || pm.isWarning() || pm.getNextDueDate() != null)
                .limit(6)
                .collect(Collectors.toList());
        if (!notable.isEmpty()) {
            out.append("Maintenance due: ").append(notable.stream().map(pm -> {
                StringBuilder line = new StringBuilder(pm.getTitle() == null ? "PM" : pm.getTitle());
                if (pm.getPercent() != null) {
                    line.append(" (").append(Math.round(pm.getPercent())).append(" %");
                    if (pm.getRemaining() != null) {
                        line.append(" → due in ~").append(number.format(pm.getRemaining()));
                        if (pm.getRemainingUnit() != null) line.append(" ").append(pm.getRemainingUnit());
                    }
                    line.append(")");
                } else if (pm.getNextDueDate() != null) {
                    line.append(" (due ").append(ymd.format(pm.getNextDueDate())).append(")");
                }
                return line.toString();
            }).collect(Collectors.joining(" · "))).append("\n");
        }

        if (!d.getOpenWorkOrders().isEmpty()) {
            out.append("Open work orders: ").append(d.getOpenWorkOrders().stream()
                    .map(w -> "#" + w.getId() + " " + w.getTitle() + " [" + w.getStatus() + "]")
                    .collect(Collectors.joining(" · "))).append("\n");
        }

        if (!d.getRecentFailures().isEmpty()) {
            Map<String, List<AssetDossierDTO.FailureSummary>> byCode = d.getRecentFailures().stream()
                    .filter(f -> f.getCode() != null)
                    .collect(Collectors.groupingBy(AssetDossierDTO.FailureSummary::getCode));
            if (!byCode.isEmpty()) {
                out.append("Recent failures (").append(RECENT_FAILURE_MONTHS).append(" mo): ")
                        .append(byCode.entrySet().stream().map(entry -> {
                            int downtime = entry.getValue().stream()
                                    .mapToInt(f -> f.getDowntimeMinutes() == null ? 0 : f.getDowntimeMinutes())
                                    .sum();
                            StringBuilder line = new StringBuilder(entry.getKey())
                                    .append(" ×").append(entry.getValue().size());
                            if (downtime > 0) {
                                line.append(" (").append(Math.round(downtime / 60.0)).append(" h down)");
                            }
                            return line.toString();
                        }).collect(Collectors.joining(" · "))).append("\n");
            }
        }

        if (d.getDocuments().isEmpty()) {
            out.append("Documents indexed: none. Anything not in the records above is not documented "
                    + "for this machine.\n");
        } else {
            out.append("Documents indexed: ").append(d.getDocuments().stream().map(doc -> {
                StringBuilder line = new StringBuilder(doc.getTitle());
                if (doc.getRevision() != null) line.append(" (rev ").append(doc.getRevision()).append(")");
                if (!IngestStatus.READY.name().equals(doc.getIngestStatus())) {
                    line.append(" [").append(doc.getIngestStatus().toLowerCase(Locale.ROOT)).append("]");
                }
                return line.toString();
            }).collect(Collectors.joining(" · "))).append("\n");
        }

        return out.toString();
    }
}
