package com.grash.service.catalog;

import com.grash.repository.PartSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The default adapter: whatever a human already recorded.
 * <p>
 * This exists so the rest of the system never has to branch on "do we have a
 * catalogue integration". Every shop has this one, and for most of them it is
 * the only one they will ever have.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class ManualAdapter implements SupplierCatalogAdapter {

    private final PartSupplierRepository partSupplierRepository;

    @Override
    public String key() {
        return "MANUAL";
    }

    @Override
    public String displayName() {
        return "Recorded suppliers";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Optional<SupplierOffer> lookupByMpn(String manufacturer, String mpn) {
        if (mpn == null || mpn.isBlank()) {
            return Optional.empty();
        }
        return partSupplierRepository.findAll().stream()
                .filter(supplier -> supplier.getPart() != null
                        && mpn.equalsIgnoreCase(supplier.getPart().getMpn()))
                .filter(supplier -> manufacturer == null
                        || manufacturer.equalsIgnoreCase(supplier.getPart().getManufacturer()))
                .findFirst()
                .map(this::toOffer);
    }

    @Override
    public Optional<SupplierOffer> lookupBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return partSupplierRepository.findAll().stream()
                .filter(supplier -> sku.equalsIgnoreCase(supplier.getSupplierSku()))
                .findFirst()
                .map(this::toOffer);
    }

    @Override
    public boolean supportsOrdering() {
        return false;
    }

    private SupplierOffer toOffer(com.grash.model.PartSupplier supplier) {
        SupplierOffer offer = new SupplierOffer();
        offer.setSupplierKey(key());
        offer.setSupplierName(supplier.getVendor() == null ? null : supplier.getVendor().getName());
        offer.setSku(supplier.getSupplierSku());
        offer.setUnitPrice(supplier.getUnitPrice());
        offer.setCurrency(supplier.getCurrency());
        offer.setMoq(supplier.getMoq());
        offer.setLeadTimeDays(supplier.getLeadTimeDays());
        offer.setProductUrl(supplier.getProductUrl());
        if (supplier.getPart() != null) {
            offer.setManufacturer(supplier.getPart().getManufacturer());
            offer.setMpn(supplier.getPart().getMpn());
            offer.setDescription(supplier.getPart().getName());
        }
        offer.setRetrievedAt(supplier.getPriceCheckedAt());
        return offer;
    }
}
