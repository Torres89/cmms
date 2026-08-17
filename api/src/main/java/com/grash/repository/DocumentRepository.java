package com.grash.repository;

import com.grash.model.Document;
import com.grash.model.enums.IngestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    List<Document> findByAsset_Id(Long assetId);

    List<Document> findByEquipmentClassAndCompany_Id(String equipmentClass, Long companyId);

    List<Document> findByCompany_Id(Long companyId);

    List<Document> findByCompany_IdAndIngestStatus(Long companyId, IngestStatus status);

    Optional<Document> findByFile_Id(Long fileId);

    Optional<Document> findByChecksumAndCompany_Id(String checksum, Long companyId);

    long countByAsset_IdAndIngestStatus(Long assetId, IngestStatus status);
}
