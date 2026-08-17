package com.grash.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Which AI door a company is on.
 * <p>
 * {@code apiKey} is write-only: it can be sent in, and is never sent back.
 * Reads see {@code apiKeyMasked} instead.
 */
@Data
@NoArgsConstructor
public class AiConfigDTO {

    /** ANTHROPIC | OPENAI | CUSTOM | MANAGED | NONE */
    private String provider;

    private String model;

    private String baseUrl;

    private Long monthlyTokenCap;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String apiKeyMasked;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean keyConfigured;
}
