package com.grash.model.enums;

/**
 * What a stored file actually is.
 * <p>
 * {@code IMAGE} and {@code OTHER} were enough for attachments; they are not
 * enough to decide what to index, what to route through an OCR pass, or what
 * must never be served through the API's heap. Note that this widens an enum
 * shared with upstream, so it is one of the deliberate divergence points.
 */
public enum FileType {
    IMAGE,
    MANUAL,
    PARTS_CATALOG,
    SCHEMATIC,
    DRAWING,
    CERTIFICATE,
    INSPECTION_REPORT,
    OIL_ANALYSIS,
    VIDEO,
    CAD,
    OTHER;

    /**
     * Types worth parsing, chunking and embedding for retrieval.
     */
    public boolean isIndexable() {
        switch (this) {
            case MANUAL:
            case PARTS_CATALOG:
            case SCHEMATIC:
            case CERTIFICATE:
            case INSPECTION_REPORT:
            case OIL_ANALYSIS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Types that must always be served by signed URL, whatever their size —
     * the browser needs range requests to seek, and the JVM must stay out of
     * the data path.
     */
    public boolean requiresSignedUrlOnly() {
        return this == VIDEO || this == CAD;
    }
}
