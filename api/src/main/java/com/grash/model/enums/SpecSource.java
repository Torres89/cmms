package com.grash.model.enums;

/**
 * How a spec value came to be known. A value a vision model read off a
 * nameplate is not the same fact as one a technician typed, and the system is
 * only trustworthy if it keeps track of the difference.
 */
public enum SpecSource {
    MANUAL_ENTRY,
    NAMEPLATE_OCR,
    DOC_EXTRACTION,
    TELEMETRY,
    IMPORT
}
