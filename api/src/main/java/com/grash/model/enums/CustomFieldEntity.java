package com.grash.model.enums;

/**
 * What a custom field is attached to.
 * <p>
 * Custom fields are the escape hatch that keeps per-customer requests out of
 * per-customer code branches, so they cannot stay bound to vendors alone.
 */
public enum CustomFieldEntity {
    VENDOR,
    ASSET,
    PART,
    WORK_ORDER,
    LOCATION,
    COMPONENT_INSTANCE
}
