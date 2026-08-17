package com.grash.model.enums;

/**
 * How several intervals on the same maintenance combine.
 * <p>
 * Manufacturer charts are written as "every 500 hours or 3 months", so
 * {@link #WHICHEVER_FIRST} is the default and the common case.
 */
public enum TriggerMode {
    WHICHEVER_FIRST,
    ALL_MUST_ELAPSE
}
