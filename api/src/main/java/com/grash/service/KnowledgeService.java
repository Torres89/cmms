package com.grash.service;

import com.grash.dto.KnowledgeSearchResultDTO;
import com.grash.model.Asset;
import com.grash.model.Document;
import com.grash.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval over the customer's own documents.
 * <p>
 * Hybrid is not an optimisation here, it is the requirement. Pure vector search
 * fails on exactly the tokens that matter in maintenance: alarm codes
 * ({@code SV0410}), part numbers ({@code 93-1000306}), lubricant designations
 * ("Mobil Vactra No. 2"), model strings, SPN/FMI pairs. Embeddings smear those
 * identifiers into their semantic neighbourhood, so {@code SV0410} cheerfully
 * returns {@code SV0411}. Lexical search nails identifiers and misses
 * paraphrases; vector search does the opposite. So both run, and Reciprocal
 * Rank Fusion merges them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeService {

    /** The standard RRF constant: damps the influence of top ranks just enough. */
    private static final int RRF_K = 60;
    private static final int CANDIDATES_PER_ARM = 50;

    private final EntityManager em;
    private final DocumentRepository documentRepository;
    private final EmbeddingClient embeddingClient;

    /**
     * @param assetId        restrict to one machine (its own documents plus any
     *                       for its equipment class)
     * @param equipmentClass restrict to a class when no specific asset is in scope
     * @param docType        optional document type filter
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDTO> search(Long companyId, String query, Long assetId,
                                                 String equipmentClass, String docType, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        float[] embedding = embeddingClient.embedQuery(query);

        Map<Long, Double> scores = new HashMap<>();
        // Lexical arm always runs: it is the one that finds "SV0410".
        accumulate(scores, lexicalRanks(companyId, query, assetId, equipmentClass, docType));
        if (embedding != null) {
            accumulate(scores, vectorRanks(companyId, embedding, assetId, equipmentClass, docType));
        } else {
            log.debug("No embedding available; falling back to lexical-only retrieval");
        }

        List<Long> ids = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return hydrate(ids, scores);
    }

    private void accumulate(Map<Long, Double> scores, List<Long> rankedIds) {
        for (int i = 0; i < rankedIds.size(); i++) {
            scores.merge(rankedIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> lexicalRanks(Long companyId, String query, Long assetId,
                                    String equipmentClass, String docType) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id FROM document_chunk c "
                        + "JOIN document d ON d.id = c.document_id "
                        + "WHERE c.company_id = :companyId "
                        + "AND c.content_tsv @@ plainto_tsquery('simple', :query) ");
        appendScope(sql, assetId, equipmentClass, docType);
        sql.append("ORDER BY ts_rank_cd(c.content_tsv, plainto_tsquery('simple', :query)) DESC LIMIT :limit");

        Query nativeQuery = em.createNativeQuery(sql.toString());
        nativeQuery.setParameter("companyId", companyId);
        nativeQuery.setParameter("query", query);
        bindScope(nativeQuery, assetId, equipmentClass, docType);
        nativeQuery.setParameter("limit", CANDIDATES_PER_ARM);
        return toIds(nativeQuery.getResultList());
    }

    @SuppressWarnings("unchecked")
    private List<Long> vectorRanks(Long companyId, float[] embedding, Long assetId,
                                   String equipmentClass, String docType) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id FROM document_chunk c "
                        + "JOIN document d ON d.id = c.document_id "
                        + "WHERE c.company_id = :companyId AND c.embedding IS NOT NULL ");
        appendScope(sql, assetId, equipmentClass, docType);
        sql.append("ORDER BY c.embedding <=> CAST(:embedding AS vector) LIMIT :limit");

        try {
            Query nativeQuery = em.createNativeQuery(sql.toString());
            nativeQuery.setParameter("companyId", companyId);
            nativeQuery.setParameter("embedding", toVectorLiteral(embedding));
            bindScope(nativeQuery, assetId, equipmentClass, docType);
            nativeQuery.setParameter("limit", CANDIDATES_PER_ARM);
            return toIds(nativeQuery.getResultList());
        } catch (Exception e) {
            // pgvector missing or unhealthy — lexical results are still useful,
            // and a degraded answer beats an error page in a machine shop.
            log.warn("Vector search unavailable, continuing lexical-only: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Scope every query to the tenant, and within it to the machine in
     * question, its equipment class, or company-wide documents.
     */
    private void appendScope(StringBuilder sql, Long assetId, String equipmentClass, String docType) {
        if (assetId != null) {
            sql.append("AND (c.asset_id = :assetId OR c.equipment_class = :equipmentClass "
                    + "OR (c.asset_id IS NULL AND c.equipment_class IS NULL)) ");
        } else if (equipmentClass != null) {
            sql.append("AND (c.equipment_class = :equipmentClass OR c.equipment_class IS NULL) ");
        }
        if (docType != null) {
            sql.append("AND d.doc_type = :docType ");
        }
    }

    private void bindScope(Query query, Long assetId, String equipmentClass, String docType) {
        if (assetId != null) {
            query.setParameter("assetId", assetId);
            query.setParameter("equipmentClass", equipmentClass);
        } else if (equipmentClass != null) {
            query.setParameter("equipmentClass", equipmentClass);
        }
        if (docType != null) {
            query.setParameter("docType", docType);
        }
    }

    private List<Long> toIds(List<?> rows) {
        return rows.stream().map(row -> ((Number) row).longValue()).collect(Collectors.toList());
    }

    static String toVectorLiteral(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(String.valueOf(value));
        }
        return joiner.toString();
    }

    /**
     * Load the winning chunks with the citation fields attached.
     * <p>
     * Title and page are structured fields, not prose, so any client — including
     * one whose system prompt we do not control — can render a real citation.
     */
    @SuppressWarnings("unchecked")
    private List<KnowledgeSearchResultDTO> hydrate(List<Long> ids, Map<Long, Double> scores) {
        Query query = em.createNativeQuery(
                "SELECT c.id, c.document_id, d.title, d.doc_type, d.revision, "
                        + "c.page_from, c.page_to, c.section, c.content, c.asset_id "
                        + "FROM document_chunk c JOIN document d ON d.id = c.document_id "
                        + "WHERE c.id IN (:ids)");
        query.setParameter("ids", ids);

        Map<Long, KnowledgeSearchResultDTO> byId = new HashMap<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            KnowledgeSearchResultDTO result = new KnowledgeSearchResultDTO();
            result.setChunkId(((Number) columns[0]).longValue());
            result.setDocumentId(((Number) columns[1]).longValue());
            result.setDocumentTitle((String) columns[2]);
            result.setDocType((String) columns[3]);
            result.setRevision((String) columns[4]);
            result.setPageFrom(columns[5] == null ? null : ((Number) columns[5]).intValue());
            result.setPageTo(columns[6] == null ? null : ((Number) columns[6]).intValue());
            result.setSection((String) columns[7]);
            result.setContent((String) columns[8]);
            result.setAssetId(columns[9] == null ? null : ((Number) columns[9]).longValue());
            result.setScore(scores.getOrDefault(result.getChunkId(), 0.0));
            byId.put(result.getChunkId(), result);
        }
        // Preserve fusion order — the SQL IN clause does not.
        return ids.stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Chunk maintenance
    // ------------------------------------------------------------------

    @Transactional
    public void deleteChunksForDocument(Long documentId) {
        em.createNativeQuery("DELETE FROM document_chunk WHERE document_id = :documentId")
                .setParameter("documentId", documentId)
                .executeUpdate();
    }

    public long countChunks(Long documentId) {
        Object result = em.createNativeQuery(
                        "SELECT COUNT(*) FROM document_chunk WHERE document_id = :documentId")
                .setParameter("documentId", documentId)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    /**
     * Documents visible to a machine: its own, plus any covering its class.
     */
    public List<Document> documentsFor(Asset asset) {
        List<Document> documents = new ArrayList<>(documentRepository.findByAsset_Id(asset.getId()));
        if (asset.getEquipmentClass() != null && asset.getCompany() != null) {
            documentRepository
                    .findByEquipmentClassAndCompany_Id(asset.getEquipmentClass(), asset.getCompany().getId())
                    .stream()
                    .filter(candidate -> documents.stream().noneMatch(d -> d.getId().equals(candidate.getId())))
                    .forEach(documents::add);
        }
        return documents;
    }
}
