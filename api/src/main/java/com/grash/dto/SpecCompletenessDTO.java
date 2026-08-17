package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * "27 of 34 specs captured" — the commissioning progress bar.
 */
@Data
@NoArgsConstructor
public class SpecCompletenessDTO {

    private int captured;

    private int expected;

    private int requiredCaptured;

    private int requiredExpected;

    private int verified;

    /** Extracted values still waiting for a human to confirm them. */
    private int pendingVerification;

    private List<MissingKey> missingKeys = new ArrayList<>();

    public double getPercent() {
        return expected == 0 ? 0 : Math.round(1000.0 * captured / expected) / 10.0;
    }

    public boolean isComplete() {
        return expected > 0 && requiredCaptured >= requiredExpected;
    }

    @Data
    @NoArgsConstructor
    public static class MissingKey {
        private String specKey;
        private String specGroup;
        private String label;
        private String unit;
        private boolean required;
    }
}
