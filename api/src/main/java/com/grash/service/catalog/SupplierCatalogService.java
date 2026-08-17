package com.grash.service.catalog;

import com.grash.model.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs a part past every configured catalogue adapter.
 * <p>
 * Results are offered to a human, never written straight into the database:
 * an automatically imported price that turns out to be for the wrong part is
 * worse than no price, because nobody knows to doubt it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierCatalogService {

    private final List<SupplierCatalogAdapter> adapters;

    public List<Map<String, Object>> availableAdapters() {
        return adapters.stream().map(adapter -> Map.<String, Object>of(
                "key", adapter.key(),
                "name", adapter.displayName(),
                "configured", adapter.isConfigured(),
                "supportsOrdering", adapter.supportsOrdering()
        )).collect(Collectors.toList());
    }

    /**
     * Look a part up everywhere we can, for a human to choose from.
     */
    public List<SupplierOffer> lookup(Part part) {
        List<SupplierOffer> offers = new ArrayList<>();
        for (SupplierCatalogAdapter adapter : adapters) {
            if (!adapter.isConfigured()) {
                continue;
            }
            try {
                if (part.getMpn() != null && !part.getMpn().isBlank()) {
                    adapter.lookupByMpn(part.getManufacturer(), part.getMpn()).ifPresent(offers::add);
                }
                if (part.getPreferredSupplierSku() != null && !part.getPreferredSupplierSku().isBlank()) {
                    adapter.lookupBySku(part.getPreferredSupplierSku()).ifPresent(offers::add);
                }
            } catch (Exception e) {
                log.warn("Adapter {} failed for part {}: {}", adapter.key(), part.getId(), e.getMessage());
            }
        }
        return offers;
    }
}
