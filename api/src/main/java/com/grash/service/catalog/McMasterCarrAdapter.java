package com.grash.service.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * McMaster-Carr's Product Information API.
 * <p>
 * The cleanest first integration for a machine shop: REST, and paired with
 * cXML/EDI for ordering. It is gated on being an approved customer with a
 * client certificate, which is exactly why nothing in the product depends on
 * it — this adapter simply reports itself unconfigured when the credentials
 * are absent, and everything carries on working from recorded suppliers.
 * <p>
 * Client-certificate authentication is configured on the JVM's SSL context
 * (see {@code storage} of {@code MCMASTER_CERT_PATH} in the deployment docs);
 * this class only speaks the API.
 */
@Component
@Order(10)
@Slf4j
public class McMasterCarrAdapter implements SupplierCatalogAdapter {

    private static final String DEFAULT_BASE_URL = "https://api.mcmaster.com";

    @Value("${catalog.mcmaster.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;
    @Value("${catalog.mcmaster.username:}")
    private String username;
    @Value("${catalog.mcmaster.password:}")
    private String password;

    private final RestTemplate restTemplate = new RestTemplate();
    private String authToken;

    @Override
    public String key() {
        return "MCMASTER";
    }

    @Override
    public String displayName() {
        return "McMaster-Carr";
    }

    @Override
    public boolean isConfigured() {
        return !username.isBlank() && !password.isBlank();
    }

    @Override
    public Optional<SupplierOffer> lookupByMpn(String manufacturer, String mpn) {
        // McMaster catalogues by its own part number, not by manufacturer MPN.
        // Looking one up by the other needs a cross-reference we don't have, so
        // this returns empty rather than a plausible wrong match.
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<SupplierOffer> lookupBySku(String sku) {
        if (!isConfigured() || sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            if (authenticate()) {
                headers.setBearerAuth(authToken);
            }
            Map<String, Object> response = restTemplate.exchange(
                    baseUrl + "/v1/products/" + sku,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class).getBody();
            if (response == null) {
                return Optional.empty();
            }
            SupplierOffer offer = new SupplierOffer();
            offer.setSupplierKey(key());
            offer.setSupplierName(displayName());
            offer.setSku(sku);
            offer.setDescription(asString(response.get("Description")));
            offer.setProductUrl("https://www.mcmaster.com/" + sku);
            Object price = response.get("Price");
            if (price instanceof Number) {
                offer.setUnitPrice(((Number) price).doubleValue());
                offer.setCurrency("USD");
            }
            return Optional.of(offer);
        } catch (Exception e) {
            // A supplier lookup failing must never break the page it was called
            // from. Recorded suppliers remain the source of truth.
            log.warn("McMaster-Carr lookup for {} failed: {}", sku, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean supportsOrdering() {
        // Ordering goes through cXML/eProcurement, which is a separate
        // integration and a separate conversation with the customer.
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean authenticate() {
        if (authToken != null) {
            return true;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/v1/login",
                    new HttpEntity<>(Map.of("UserName", username, "Password", password), headers),
                    Map.class);
            authToken = response == null ? null : asString(response.get("AuthToken"));
            return authToken != null;
        } catch (Exception e) {
            log.warn("McMaster-Carr authentication failed: {}", e.getMessage());
            return false;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
