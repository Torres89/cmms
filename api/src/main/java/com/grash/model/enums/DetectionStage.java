package com.grash.model.enums;

/**
 * Where in the chain a failure was caught. The distribution across these values
 * is the honest measure of whether preventive maintenance is working.
 */
public enum DetectionStage {
    OPERATOR,
    PM_INSPECTION,
    CONDITION_MONITORING,
    BREAKDOWN
}
