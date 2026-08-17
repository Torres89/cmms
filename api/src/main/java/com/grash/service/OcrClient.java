package com.grash.service;

import com.grash.factory.StorageServiceFactory;
import com.grash.model.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Local OCR, via the ingest worker.
 * <p>
 * The cheap half of the work — turning pixels into characters — runs on CPU
 * here for free. Interpreting those characters into "max spindle speed is
 * 12,000 rpm" is the expensive half, and that goes to the customer's own model.
 * <p>
 * Returns null rather than throwing when OCR is unavailable: a nameplate
 * capture with no OCR text is still perfectly workable, because the image
 * itself is what the vision model reads.
 */
@Service
@Slf4j
public class OcrClient {

    private final RestTemplate restTemplate;
    private final StorageServiceFactory storageServiceFactory;
    private final String ocrUrl;
    private final boolean enabled;

    public OcrClient(StorageServiceFactory storageServiceFactory,
                     @Value("${knowledge.embedding.url:}") String workerUrl) {
        this.storageServiceFactory = storageServiceFactory;
        this.ocrUrl = workerUrl == null ? "" : workerUrl.replaceAll("/+$", "");
        this.enabled = !this.ocrUrl.isEmpty();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);  // OCR on a full page is not instant
        this.restTemplate = new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    public String read(File file) {
        if (!enabled || file == null) {
            return null;
        }
        try {
            // The worker fetches the object itself; the bytes never come through
            // this JVM.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> response = restTemplate.postForObject(
                    ocrUrl + "/ocr",
                    new HttpEntity<>(Map.of("path", file.getPath()), headers),
                    Map.class);
            if (response == null) {
                return null;
            }
            Object text = response.get("text");
            return text == null ? null : String.valueOf(text);
        } catch (Exception e) {
            log.debug("OCR unavailable for {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
