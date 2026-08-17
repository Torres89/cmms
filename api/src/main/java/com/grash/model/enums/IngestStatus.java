package com.grash.model.enums;

public enum IngestStatus {
    PENDING,
    PARSING,
    EMBEDDING,
    READY,
    FAILED,
    /** Not worth indexing — a photo, a video, a CAD file. */
    SKIPPED
}
