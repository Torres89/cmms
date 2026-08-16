package com.grash.controller;

import com.grash.dto.*;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The knowledge tier: retrieval, fault codes, diagnosis, history and plan
 * proposals.
 * <p>
 * These are the endpoints the AI tool surface is built on, so the response
 * shapes are part of the contract: citations are structured fields, and the
 * empty case is always an explicit answer rather than an absence.
 */
@RestController
@Tag(name = "knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DiagnosisService diagnosisService;
    private final MaintenancePlanProposalService maintenancePlanProposalService;
    private final FailureService failureService;
    private final AssetService assetService;
    private final UserService userService;
    private final WorkOrderService workOrderService;
    private final EntityManager em;

    // ------------------------------------------------------------------
    // Retrieval
    // ------------------------------------------------------------------

    @PostMapping("/knowledge/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SearchResponse search(@RequestBody SearchRequest request, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        String equipmentClass = request.getEquipmentClass();
        if (request.getAssetId() != null) {
            Asset asset = requireAsset(request.getAssetId(), user);
            equipmentClass = asset.getEquipmentClass();
        }
        int limit = request.getLimit() == null ? 8 : Math.min(request.getLimit(), 25);
        SearchResponse response = new SearchResponse();
        response.results = knowledgeService.search(user.getCompany().getId(), request.getQuery(),
                request.getAssetId(), equipmentClass, request.getDocType(), limit);
        return response;
    }

    public static class SearchRequest {
        private String query;
        private Long assetId;
        private String equipmentClass;
        private String docType;
        private Integer limit;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public Long getAssetId() {
            return assetId;
        }

        public void setAssetId(Long assetId) {
            this.assetId = assetId;
        }

        public String getEquipmentClass() {
            return equipmentClass;
        }

        public void setEquipmentClass(String equipmentClass) {
            this.equipmentClass = equipmentClass;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }

    public static class SearchResponse {
        public List<KnowledgeSearchResultDTO> results = new ArrayList<>();

        public List<KnowledgeSearchResultDTO> getResults() {
            return results;
        }

        public String getNote() {
            return results.isEmpty()
                    ? "Nothing indexed matches this query. Say the documents don't cover it rather than "
                    + "answering from general knowledge."
                    : null;
        }
    }

    // ------------------------------------------------------------------
    // Fault codes
    // ------------------------------------------------------------------

    /**
     * A code resolved three ways: the dictionary, the manuals, and every time
     * this exact code has come up on this machine before.
     */
    @GetMapping("/knowledge/fault-code")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> faultCode(@RequestParam("code") String code,
                                         @RequestParam(value = "assetId", required = false) Long assetId,
                                         HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Long companyId = user.getCompany().getId();
        String equipmentClass = null;
        if (assetId != null) {
            equipmentClass = requireAsset(assetId, user).getEquipmentClass();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", code);

        List<Object[]> dictionary = em.createNativeQuery(
                        "SELECT description_en, description_es, severity, likely_causes, "
                                + "recommended_action, source, manufacturer "
                                + "FROM fault_code_dictionary "
                                + "WHERE UPPER(code) = UPPER(:code) "
                                + "AND (company_id IS NULL OR company_id = :companyId) "
                                + "AND (:equipmentClass IS NULL OR equipment_class IS NULL "
                                + "     OR equipment_class = :equipmentClass) "
                                + "ORDER BY company_id NULLS LAST LIMIT 5")
                .setParameter("code", code)
                .setParameter("companyId", companyId)
                .setParameter("equipmentClass", equipmentClass)
                .getResultList();

        response.put("dictionary", dictionary.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("description", row[0]);
            entry.put("descriptionEs", row[1]);
            entry.put("severity", row[2]);
            entry.put("likelyCauses", row[3]);
            entry.put("recommendedAction", row[4]);
            entry.put("source", row[5]);
            entry.put("manufacturer", row[6]);
            return entry;
        }).collect(Collectors.toList()));

        // Lexical retrieval is what makes this work: "SV0410" has to match
        // "SV0410" and not its embedding neighbours.
        response.put("manualReferences",
                knowledgeService.search(companyId, code, assetId, equipmentClass, null, 6));

        if (assetId != null) {
            response.put("historyOnThisAsset", faultHistory(assetId, code));
        }

        if (dictionary.isEmpty() && ((List<?>) response.get("manualReferences")).isEmpty()) {
            response.put("note", "This code is not in the dictionary and does not appear in any indexed "
                    + "document. Do not guess what it means.");
        }
        return response;
    }

    /**
     * Every previous occurrence of this code on this machine — usually the most
     * useful part of the answer, because the shop has probably seen it before.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> faultHistory(Long assetId, String code) {
        try {
            List<Object[]> occurrences = em.createNativeQuery(
                            "SELECT id, occurred_at, cleared_at, description, work_order_id "
                                    + "FROM fault_event WHERE asset_id = :assetId "
                                    + "AND UPPER(code) = UPPER(:code) ORDER BY occurred_at DESC LIMIT 20")
                    .setParameter("assetId", assetId)
                    .setParameter("code", code)
                    .getResultList();
            SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            return occurrences.stream().map(row -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", row[0]);
                entry.put("occurredAt", row[1] == null ? null : stamp.format(row[1]));
                entry.put("clearedAt", row[2] == null ? null : stamp.format(row[2]));
                entry.put("description", row[3]);
                entry.put("workOrderId", row[4]);
                return entry;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            // Telemetry is optional per customer; no fault_event rows is a
            // perfectly normal state, not an error worth failing the lookup for.
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------
    // Diagnosis
    // ------------------------------------------------------------------

    @PostMapping("/assets/{id}/diagnose")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public DiagnosisDTO diagnose(@PathVariable("id") Long id,
                                 @RequestBody DiagnoseRequest request,
                                 HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Asset asset = requireAsset(id, user);
        if (request.getSymptom() == null || request.getSymptom().isBlank()) {
            throw new CustomException("A symptom is required", HttpStatus.BAD_REQUEST);
        }
        return diagnosisService.diagnose(asset, request.getSymptom(), request.getObservations(),
                user.getCompany().getId());
    }

    public static class DiagnoseRequest {
        private String symptom;
        private String observations;

        public String getSymptom() {
            return symptom;
        }

        public void setSymptom(String symptom) {
            this.symptom = symptom;
        }

        public String getObservations() {
            return observations;
        }

        public void setObservations(String observations) {
            this.observations = observations;
        }
    }

    // ------------------------------------------------------------------
    // Plan proposals and history
    // ------------------------------------------------------------------

    @GetMapping("/assets/{id}/maintenance-plan-proposal")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public MaintenancePlanProposalDTO proposePlan(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        return maintenancePlanProposalService.propose(requireAsset(id, user), user.getCompany().getId());
    }

    /**
     * The unified timeline: work orders and failure events together, because
     * "what happened to this machine" is one question, not two.
     */
    @GetMapping("/assets/{id}/maintenance-history")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Map<String, Object> maintenanceHistory(
            @PathVariable("id") Long id,
            @RequestParam(value = "since", required = false) String since,
            @RequestParam(value = "failureMode", required = false) String failureMode,
            @RequestParam(value = "componentId", required = false) Long componentId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        requireAsset(id, user);
        Date sinceDate = parseDate(since);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("workOrders", workOrderService.findByAsset(id).stream()
                .filter(workOrder -> sinceDate == null || workOrder.getCreatedAt() == null
                        || workOrder.getCreatedAt().after(sinceDate))
                .sorted(Comparator.comparing(workOrder ->
                        workOrder.getCreatedAt() == null ? new Date(0) : workOrder.getCreatedAt(),
                        Comparator.reverseOrder()))
                .limit(limit)
                .map(workOrder -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", workOrder.getId());
                    entry.put("title", workOrder.getTitle());
                    entry.put("status", workOrder.getStatus());
                    entry.put("priority", workOrder.getPriority());
                    entry.put("createdAt", workOrder.getCreatedAt());
                    entry.put("completedOn", workOrder.getCompletedOn());
                    entry.put("description", workOrder.getDescription());
                    return entry;
                })
                .collect(Collectors.toList()));

        response.put("failureEvents", (componentId != null
                ? failureService.findEventsForComponent(componentId)
                : failureService.findEventsForAssetSince(id, sinceDate)).stream()
                .filter(event -> failureMode == null
                        || (event.getFailureMode() != null
                        && failureMode.equalsIgnoreCase(event.getFailureMode().getCode())))
                .limit(limit)
                .map(event -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", event.getId());
                    entry.put("code", event.getFailureMode() == null ? null : event.getFailureMode().getCode());
                    entry.put("name", event.getFailureMode() == null ? null : event.getFailureMode().getNameEn());
                    entry.put("occurredAt", FailureService.when(event));
                    entry.put("mechanism", event.getMechanism());
                    entry.put("cause", event.getCause());
                    entry.put("detectedAt", event.getDetectedAt());
                    entry.put("severity", event.getSeverity());
                    entry.put("downtimeMinutes", event.getDowntimeMinutes());
                    entry.put("repairCost", event.getRepairCost());
                    entry.put("correctiveAction", event.getCorrectiveAction());
                    entry.put("preventiveRecommendation", event.getPreventiveRecommendation());
                    return entry;
                })
                .collect(Collectors.toList()));

        response.put("pareto", failureService.pareto(id));
        return response;
    }

    private Date parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(value.substring(0, Math.min(10, value.length())));
        } catch (Exception e) {
            throw new CustomException("since must be an ISO date, e.g. 2025-01-01", HttpStatus.BAD_REQUEST);
        }
    }

    private Asset requireAsset(Long id, OwnUser user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ASSETS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return assetService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Asset not found", HttpStatus.NOT_FOUND));
    }
}
