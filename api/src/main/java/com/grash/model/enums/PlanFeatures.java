package com.grash.model.enums;

public enum PlanFeatures {
    PREVENTIVE_MAINTENANCE,
    CHECKLIST,
    FILE,
    PURCHASE_ORDER,
    METER,
    REQUEST_CONFIGURATION,
    ADDITIONAL_TIME,
    ADDITIONAL_COST,
    ANALYTICS,
    REQUEST_PORTAL,
    SIGNATURE,
    ROLE,
    WORKFLOW,
    API_ACCESS,
    WEBHOOK,
    IMPORT_CSV,

    // Machine-specialist features. These gate what is enabled in our hosted
    // service; they are not closed-source feature gating on the AGPL source,
    // and nothing here should ever be wired to the Keygen licence machinery.

    /** Document ingestion, hybrid retrieval, citations. */
    MACHINE_KNOWLEDGE,
    /** Serialized components, the back-to-birth ledger, life limits. */
    COMPONENT_TRACKING,
    /** MTConnect / OPC UA / FOCAS / ISO 15143 ingestion. */
    TELEMETRY,
    /** Supplier catalogue adapters and purchase-order submission. */
    SUPPLIER_CATALOG,
    /** Door 1 - the remote MCP server. */
    MCP_ACCESS,
    /** Door 3 - the managed AI add-on, on our key with a fair-use cap. */
    MANAGED_AI
}
