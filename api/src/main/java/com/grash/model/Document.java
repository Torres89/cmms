package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.FileType;
import com.grash.model.enums.IngestStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * A document that has been (or is being) turned into searchable knowledge.
 * <p>
 * A {@link File} is bytes in a bucket; a Document is what those bytes mean —
 * which machine or equipment class they describe, which revision they are, and
 * how far ingestion has got. One manual can serve every identical machine in
 * the shop, which is why {@code equipmentClass} exists alongside {@code asset}.
 * <p>
 * Only the customer's own documents, in the customer's own tenancy. There is
 * never a shared corpus across customers — that is both the legal answer and
 * the privacy pitch.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_document_asset", columnList = "asset_id"),
        @Index(name = "idx_document_class", columnList = "equipment_class"),
        @Index(name = "idx_document_status", columnList = "ingest_status")
})
public class Document extends CompanyAudit {

    @OneToOne(fetch = FetchType.LAZY)
    @NotNull
    private File file;

    @ManyToOne(fetch = FetchType.LAZY)
    private Asset asset;

    /** Set instead of (or as well as) an asset when one manual serves N machines. */
    private String equipmentClass;

    @Enumerated(EnumType.STRING)
    private FileType docType = FileType.OTHER;

    @NotNull
    private String title;

    private String manufacturer;

    private String revision;

    /** ISO 639-1, e.g. "en", "es". */
    private String language;

    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    private IngestStatus ingestStatus = IngestStatus.PENDING;

    private Date ingestedAt;

    /** Content hash, so re-uploading the same file doesn't re-index it. */
    private String checksum;

    @Column(length = 2000)
    private String ingestError;

    /** How many chunks this document produced; 0 until ingestion finishes. */
    private Integer chunkCount = 0;
}
