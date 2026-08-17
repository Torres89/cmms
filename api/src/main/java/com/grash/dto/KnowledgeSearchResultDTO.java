package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One retrieved excerpt, with its citation as structured fields.
 * <p>
 * Title and page are separate fields rather than baked into the text on
 * purpose. An external model we don't control is reading these, so the
 * citation has to be something any client can render, not a convention the
 * model has to be trusted to follow.
 */
@Data
@NoArgsConstructor
public class KnowledgeSearchResultDTO {

    private Long chunkId;

    private Long documentId;

    private String documentTitle;

    private String docType;

    private String revision;

    private Integer pageFrom;

    private Integer pageTo;

    /** Heading path, e.g. "5 Maintenance > 5.3 Spindle". */
    private String section;

    private String content;

    private Long assetId;

    private double score;

    /**
     * Ready-to-print citation, e.g. "Maintenance Manual (rev D), p. 5-14".
     */
    public String getCitation() {
        StringBuilder citation = new StringBuilder(documentTitle == null ? "Document" : documentTitle);
        if (revision != null && !revision.isBlank()) {
            citation.append(" (rev ").append(revision).append(")");
        }
        if (pageFrom != null) {
            citation.append(", p. ").append(pageFrom);
            if (pageTo != null && !pageTo.equals(pageFrom)) {
                citation.append("-").append(pageTo);
            }
        }
        return citation.toString();
    }
}
