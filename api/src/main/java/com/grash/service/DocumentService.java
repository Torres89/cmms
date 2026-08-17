package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.Document;
import com.grash.model.enums.IngestStatus;
import com.grash.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Documents and the ingest queue.
 * <p>
 * The queue is a Postgres table drained by the Python worker with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}. Ingestion is bursty and slow —
 * twenty customers uploading 400-page manuals would saturate four vCPUs — so
 * jobs run serially and overnight. Nobody is waiting on them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeService knowledgeService;
    private final EntityManager em;

    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }

    public List<Document> findByAsset(Long assetId) {
        return documentRepository.findByAsset_Id(assetId);
    }

    public List<Document> findByCompany(Long companyId) {
        return documentRepository.findByCompany_Id(companyId);
    }

    public List<Document> findByStatus(Long companyId, IngestStatus status) {
        return documentRepository.findByCompany_IdAndIngestStatus(companyId, status);
    }

    public Optional<Document> findByFile(Long fileId) {
        return documentRepository.findByFile_Id(fileId);
    }

    @Transactional
    public Document create(Document document) {
        if (document.getFile() == null) {
            throw new CustomException("A document must reference an uploaded file", HttpStatus.BAD_REQUEST);
        }
        Document saved = documentRepository.save(document);
        if (saved.getDocType() != null && saved.getDocType().isIndexable()) {
            enqueue(saved, 100);
        } else {
            // Photos, videos and CAD files are stored and served, not indexed.
            saved.setIngestStatus(IngestStatus.SKIPPED);
            saved = documentRepository.save(saved);
        }
        return saved;
    }

    public Document save(Document document) {
        return documentRepository.save(document);
    }

    /**
     * Put a document (back) in the queue.
     *
     * @param priority lower runs sooner; use it to jump a document to the front
     *                 during a commissioning visit while the customer watches.
     */
    @Transactional
    public void enqueue(Document document, int priority) {
        document.setIngestStatus(IngestStatus.PENDING);
        document.setIngestError(null);
        documentRepository.save(document);
        em.createNativeQuery(
                        "INSERT INTO ingest_job (document_id, company_id, status, priority) "
                                + "VALUES (:documentId, :companyId, 'QUEUED', :priority)")
                .setParameter("documentId", document.getId())
                .setParameter("companyId", document.getCompany().getId())
                .setParameter("priority", priority)
                .executeUpdate();
    }

    @Transactional
    public void reindex(Document document) {
        knowledgeService.deleteChunksForDocument(document.getId());
        document.setChunkCount(0);
        enqueue(document, 50);
    }

    @Transactional
    public void delete(Long id) {
        knowledgeService.deleteChunksForDocument(id);
        documentRepository.deleteById(id);
    }

    /**
     * How ingestion is going, for the commissioning queue view.
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Long> queueSummary(Long companyId) {
        java.util.Map<String, Long> summary = new java.util.LinkedHashMap<>();
        for (IngestStatus status : IngestStatus.values()) {
            summary.put(status.name(),
                    (long) documentRepository.findByCompany_IdAndIngestStatus(companyId, status).size());
        }
        return summary;
    }
}
