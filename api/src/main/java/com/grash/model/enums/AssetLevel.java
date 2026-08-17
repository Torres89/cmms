package com.grash.model.enums;

/**
 * Where an asset sits in the equipment breakdown structure, aligned with the
 * ISO 14224 nine-level taxonomy.
 * <p>
 * Sub-assemblies are modelled as {@code Asset} rows rather than as a parallel
 * entity. That one decision buys component-level work orders, meters, files,
 * PMs, downtime, permissions and search for free — everything in the system
 * already points at {@code Asset}.
 */
public enum AssetLevel {
    /** ISO 14224 L3 — plant / site. */
    SITE,
    /** L4/L5 — a system or cell, e.g. "Machining cell 1". */
    SYSTEM,
    /** L6 — the machine itself, e.g. "Haas VF-4SS". This is the historical Asset. */
    EQUIPMENT,
    /** L7 — a subunit, e.g. "Spindle assembly". */
    SUBUNIT,
    /** L8 — a maintainable component, e.g. "Spindle cartridge". */
    COMPONENT,
    /** L9 — a part, e.g. "Front bearing 7014". */
    PART;

    /**
     * The levels shown by default in asset lists. Sub-assemblies exist for
     * structure and history, not to bury the machine list.
     */
    public boolean isTopLevel() {
        return this == SITE || this == SYSTEM || this == EQUIPMENT;
    }
}
