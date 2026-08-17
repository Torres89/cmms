package com.grash.model.enums;

public enum TrackingClass {
    /** No individual identity — a bag of identical parts. */
    NON_TRACKED,
    /** Identified by serial number and tracked through installs and removals. */
    SERIALIZED,
    /** Serialized and additionally bounded by hours, cycles or calendar age. */
    LIFE_LIMITED
}
