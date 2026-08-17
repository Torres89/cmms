package com.grash.model.enums;

public enum IntervalBasis {
    /** Elapsed time: every 3 months. */
    CALENDAR,
    /** A counter: every 500 spindle hours. */
    METER,
    /** Triggered by something happening rather than by elapsed usage. */
    EVENT
}
