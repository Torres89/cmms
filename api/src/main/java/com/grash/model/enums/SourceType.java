package com.grash.model.enums;

/**
 * Where a meter's readings or a machine's faults actually come from.
 * <p>
 * {@link #MANUAL} is the one that always works, and PMs never depend on
 * anything else. Getting data off a specific control is routinely the hardest
 * part of a project — control kernel versus HMI OS versus network boundaries
 * vary by vendor and vintage — so telemetry is scoped as paid setup per
 * machine, never as something that "just works".
 */
public enum SourceType {
    /** Typed in, or scanned at the machine. Always available. */
    MANUAL,
    /** The open CNC standard: one vocabulary across controls, even legacy ones. */
    MTCONNECT,
    /** Siemens and newer controls. */
    OPCUA,
    /** Fanuc's native protocol, where MTConnect isn't available. */
    FOCAS,
    /** ISO 15143-3 (AEMP 2.0) — one adapter covers every major earthmoving OEM. */
    ISO15143,
    WEBHOOK,
    CSV_IMPORT
}
