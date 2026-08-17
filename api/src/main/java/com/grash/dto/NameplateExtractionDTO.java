package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything a model needs to read a nameplate, and the shape it must answer in.
 * <p>
 * We do not run a vision model. This hands the caller's model a signed image
 * URL, the OCR text we could extract locally on CPU, and the exact list of
 * fields this equipment class expects — so the model is filling in a known form
 * rather than inventing a schema, and anything it returns lands as an
 * unverified proposal.
 */
@Data
@NoArgsConstructor
public class NameplateExtractionDTO {

    private Long assetId;

    private String assetName;

    private String equipmentClass;

    /** Short-lived signed URL — the image never passes through this API. */
    private String imageUrl;

    /** What local OCR could read, or null when OCR is unavailable. */
    private String ocrText;

    private List<ExpectedField> expectedFields = new ArrayList<>();

    /** JSON Schema for the response, so the model's output is machine-checkable. */
    private Map<String, Object> responseSchema;

    private String instructions;

    @Data
    @NoArgsConstructor
    public static class ExpectedField {
        private String specKey;
        private String specGroup;
        private String label;
        private String unit;
        private String valueType;
        private boolean required;
        /** What is already recorded, so the model can flag a disagreement. */
        private String currentValue;
    }
}
