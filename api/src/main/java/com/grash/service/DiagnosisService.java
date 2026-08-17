package com.grash.service;

import com.grash.dto.DiagnosisDTO;
import com.grash.dto.KnowledgeSearchResultDTO;
import com.grash.model.Asset;
import com.grash.model.AssetBomLine;
import com.grash.model.FailureEvent;
import com.grash.model.FailureMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrated diagnosis.
 * <p>
 * The sequence is deliberate: the dossier first (what is true now), then
 * candidate failure modes drawn from the catalogue but ranked by what has
 * actually happened to <em>this</em> machine, then retrieval per candidate so
 * every suggestion arrives with a page reference, then the parts it will
 * probably take.
 * <p>
 * No model runs here. This assembles evidence; the customer's model does the
 * reasoning over it. That is the whole architecture in one method.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private static final int MAX_CANDIDATES = 5;
    private static final int REFERENCES_PER_CANDIDATE = 3;

    /**
     * Lines a manual writes safety instructions on. Matching is deliberately
     * broad — a false positive costs a redundant warning, a false negative
     * costs somebody a hand.
     */
    private static final Pattern SAFETY = Pattern.compile(
            "(?i)\\b(danger|warning|caution|lockout|tagout|lock out|tag out|de-?energi[sz]e|"
                    + "isolate|disconnect power|high voltage|peligro|advertencia|precaución|"
                    + "bloqueo|desconecte|alta tensión)\\b");

    private final AssetDossierService assetDossierService;
    private final KnowledgeService knowledgeService;
    private final FailureService failureService;
    private final AssetBomService assetBomService;

    public DiagnosisDTO diagnose(Asset asset, String symptom, String observations, Long companyId) {
        DiagnosisDTO diagnosis = new DiagnosisDTO();
        diagnosis.setAssetId(asset.getId());
        diagnosis.setAssetName(asset.getName());
        diagnosis.setSymptom(symptom);
        diagnosis.setDossier(assetDossierService.build(asset).getText());

        String fullQuery = observations == null || observations.isBlank()
                ? symptom : symptom + " " + observations;

        // 1. What the manuals say about the symptom itself.
        List<KnowledgeSearchResultDTO> general = knowledgeService.search(
                companyId, fullQuery, asset.getId(), asset.getEquipmentClass(), null, 6);
        diagnosis.setGeneralReferences(general);

        // 2. Candidates from the catalogue, ranked by this machine's own history.
        List<FailureMode> ranked = failureService.rankedCandidates(asset, null, companyId);
        Map<String, List<FailureEvent>> historyByCode = failureService.findEventsForAsset(asset.getId()).stream()
                .filter(event -> event.getFailureMode() != null)
                .collect(Collectors.groupingBy(event -> event.getFailureMode().getCode()));

        List<FailureMode> shortlist = shortlist(ranked, fullQuery, historyByCode);

        for (FailureMode mode : shortlist) {
            DiagnosisDTO.Candidate candidate = new DiagnosisDTO.Candidate();
            candidate.setCode(mode.getCode());
            candidate.setName(mode.getNameEn());
            candidate.setSubunit(mode.getSubunit());
            candidate.setTypicalMechanism(mode.getTypicalMechanism());
            candidate.setTypicalCauses(mode.getTypicalCauses());
            candidate.setDetectionMethods(mode.getDetectionMethods());
            candidate.setSeverity(mode.getSeverityDefault());

            List<FailureEvent> here = historyByCode.getOrDefault(mode.getCode(), Collections.emptyList());
            candidate.setTimesSeenOnThisAsset(here.size());
            here.stream()
                    .filter(event -> event.getCreatedAt() != null)
                    .max(Comparator.comparing(FailureEvent::getCreatedAt))
                    .ifPresent(event -> {
                        candidate.setLastSeenOnThisAsset(event.getCreatedAt());
                        candidate.setPreviousCorrectiveAction(event.getCorrectiveAction());
                    });
            candidate.setWhy(explain(mode, here.size(), fullQuery));

            // 3. Manual excerpts for this specific candidate.
            String candidateQuery = mode.getNameEn()
                    + (mode.getSubunit() == null ? "" : " " + mode.getSubunit())
                    + " " + symptom;
            candidate.setReferences(knowledgeService.search(
                    companyId, candidateQuery, asset.getId(), asset.getEquipmentClass(),
                    null, REFERENCES_PER_CANDIDATE));

            // 4. Parts that sit at the implicated position.
            candidate.setLikelyParts(likelyParts(asset, mode));

            diagnosis.getCandidates().add(candidate);
        }

        diagnosis.setSafetySteps(extractSafetySteps(diagnosis));
        diagnosis.setCoverageNote(coverageNote(shortlist, general, asset));
        return diagnosis;
    }

    /**
     * Narrow the ranked catalogue to a handful, preferring modes whose wording
     * overlaps the reported symptom.
     */
    private List<FailureMode> shortlist(List<FailureMode> ranked, String query,
                                        Map<String, List<FailureEvent>> historyByCode) {
        Set<String> queryTerms = tokenize(query);
        return ranked.stream()
                .sorted(Comparator.comparingDouble((FailureMode mode) -> -(
                        // Seen here before is the strongest signal there is.
                        2.0 * historyByCode.getOrDefault(mode.getCode(), Collections.emptyList()).size()
                                + textOverlap(mode, queryTerms)
                                + 0.1 * (mode.getSeverityDefault() == null ? 0 : mode.getSeverityDefault())))
                )
                .limit(MAX_CANDIDATES)
                .collect(Collectors.toList());
    }

    private double textOverlap(FailureMode mode, Set<String> queryTerms) {
        Set<String> modeTerms = tokenize(String.join(" ",
                orEmpty(mode.getNameEn()), orEmpty(mode.getNameEs()), orEmpty(mode.getDescription()),
                orEmpty(mode.getTypicalCauses()), orEmpty(mode.getSubunit())));
        modeTerms.retainAll(queryTerms);
        return modeTerms.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null) return new HashSet<>();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() > 3)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String explain(FailureMode mode, int timesSeen, String query) {
        List<String> reasons = new ArrayList<>();
        if (timesSeen > 0) {
            reasons.add("this machine has had this failure " + timesSeen
                    + (timesSeen == 1 ? " time before" : " times before"));
        }
        if (textOverlap(mode, tokenize(query)) > 0) {
            reasons.add("the reported symptom matches its description");
        }
        if (mode.getSeverityDefault() != null && mode.getSeverityDefault() >= 4) {
            reasons.add("it is high severity on this equipment class, so worth ruling out early");
        }
        if (reasons.isEmpty()) {
            reasons.add("it is a known failure mode for this equipment class");
        }
        return String.join("; ", reasons);
    }

    private List<DiagnosisDTO.SuggestedPart> likelyParts(Asset asset, FailureMode mode) {
        List<AssetBomLine> lines = assetBomService.findByAsset(asset.getId());
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        String subunit = mode.getSubunit() == null ? null : mode.getSubunit().toLowerCase(Locale.ROOT);
        String codePrefix = mode.getCode() == null ? null
                : mode.getCode().split("-")[0].toLowerCase(Locale.ROOT);

        return lines.stream()
                .filter(line -> {
                    if (line.getPositionCode() == null) return false;
                    String position = line.getPositionCode().toLowerCase(Locale.ROOT);
                    return (codePrefix != null && position.startsWith(codePrefix))
                            || (subunit != null && position.contains(subunit.substring(0,
                            Math.min(3, subunit.length()))));
                })
                .map(line -> {
                    DiagnosisDTO.SuggestedPart part = new DiagnosisDTO.SuggestedPart();
                    if (line.getPart() != null) {
                        part.setPartId(line.getPart().getId());
                        part.setName(line.getPart().getName());
                        part.setMpn(line.getPart().getMpn());
                        part.setOnHand(line.getPart().getQuantity());
                        part.setInStock(line.getPart().getQuantity() > 0);
                    }
                    part.setPositionCode(line.getPositionCode());
                    part.setQtyPerAssembly(line.getQtyPerAssembly());
                    return part;
                })
                .collect(Collectors.toList());
    }

    /**
     * Pull the manual's own safety lines out of the retrieved excerpts so they
     * survive as their own field.
     */
    private List<String> extractSafetySteps(DiagnosisDTO diagnosis) {
        List<KnowledgeSearchResultDTO> all = new ArrayList<>(diagnosis.getGeneralReferences());
        diagnosis.getCandidates().forEach(candidate -> all.addAll(candidate.getReferences()));

        LinkedHashSet<String> steps = new LinkedHashSet<>();
        for (KnowledgeSearchResultDTO reference : all) {
            if (reference.getContent() == null) continue;
            for (String line : reference.getContent().split("(?<=[.!?])\\s+|\\n")) {
                String trimmed = line.trim();
                if (trimmed.length() < 12 || trimmed.length() > 400) continue;
                Matcher matcher = SAFETY.matcher(trimmed);
                if (matcher.find()) {
                    steps.add(trimmed + "  [" + reference.getCitation() + "]");
                }
            }
            if (steps.size() >= 8) break;
        }
        return new ArrayList<>(steps);
    }

    private String coverageNote(List<FailureMode> shortlist, List<KnowledgeSearchResultDTO> general, Asset asset) {
        List<String> gaps = new ArrayList<>();
        if (asset.getEquipmentClass() == null) {
            gaps.add("this asset has no equipment class set, so no failure-mode catalogue applies to it");
        } else if (shortlist.isEmpty()) {
            gaps.add("no failure modes are catalogued for equipment class " + asset.getEquipmentClass());
        }
        if (general.isEmpty()) {
            gaps.add("no manual content is indexed for this machine");
        }
        if (gaps.isEmpty()) {
            return null;
        }
        return "Limited evidence: " + String.join("; ", gaps)
                + ". Say so rather than reasoning from general knowledge about similar machines.";
    }
}
