package com.grash.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Embeds a search query by asking the ingest worker.
 * <p>
 * The model (EmbeddingGemma-300M, 768 dimensions, multilingual) lives in the
 * Python worker where the document pipeline already loads it — there is no
 * sense in a second copy in the JVM. This is the half of "AI" we own: CPU-only,
 * free per call, and where the token volume actually lives.
 * <p>
 * Multilingual matters concretely: the UI is English/Spanish, and a technician
 * asking <em>"¿cada cuánto se cambia el aceite de la caja?"</em> has to retrieve
 * English manual text. One model, no translation step.
 */
@Service
@Slf4j
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final String embedUrl;
    private final boolean enabled;

    public EmbeddingClient(@Value("${knowledge.embedding.url:}") String embeddingUrl,
                           @Value("${knowledge.embedding.timeout-seconds:15}") int timeoutSeconds) {
        this.embedUrl = embeddingUrl == null ? "" : embeddingUrl.replaceAll("/+$", "");
        this.enabled = !this.embedUrl.isEmpty();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        this.restTemplate = new RestTemplate(factory);
        if (!enabled) {
            log.info("No embedding service configured; retrieval will run lexical-only");
        }
    }

    /**
     * @return the query vector, or null when embeddings are unavailable — in
     * which case retrieval degrades to lexical search rather than failing.
     */
    @SuppressWarnings("unchecked")
    public float[] embedQuery(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // The model is trained with asymmetric prefixes; a query embedded as
            // a document retrieves noticeably worse.
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("texts", Collections.singletonList(text), "kind", "query"), headers);
            Map<String, Object> response = restTemplate.postForObject(
                    embedUrl + "/embed", request, Map.class);
            if (response == null) {
                return null;
            }
            List<List<Number>> embeddings = (List<List<Number>>) response.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                return null;
            }
            List<Number> vector = embeddings.get(0);
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = vector.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not embed query: {}", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
