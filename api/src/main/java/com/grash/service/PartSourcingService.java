package com.grash.service;

import com.grash.dto.PartSourcingDTO;
import com.grash.model.Part;
import com.grash.model.PartCrossReference;
import com.grash.model.PartSupplier;
import com.grash.repository.PartCrossReferenceRepository;
import com.grash.repository.PartSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * "Where do I buy this, how much is it, and how long will it take?"
 * <p>
 * Suppliers are recorded, never inferred. An empty supplier list is a truthful
 * answer; an invented price is not.
 */
@Service
@RequiredArgsConstructor
public class PartSourcingService {

    private final PartSupplierRepository partSupplierRepository;
    private final PartCrossReferenceRepository partCrossReferenceRepository;

    public List<PartSupplier> findSuppliers(Long partId) {
        return partSupplierRepository.findByPart_IdOrderByPreferredDescUnitPriceAsc(partId);
    }

    public Optional<PartSupplier> findPreferredSupplier(Long partId) {
        return partSupplierRepository.findByPart_IdAndPreferredTrue(partId);
    }

    public List<PartCrossReference> findAlternates(Long partId) {
        return partCrossReferenceRepository.findByPart_Id(partId);
    }

    public Optional<PartSupplier> findSupplierById(Long id) {
        return partSupplierRepository.findById(id);
    }

    public PartSupplier saveSupplier(PartSupplier supplier) {
        // Exactly one preferred supplier per part, or "preferred" means nothing.
        if (supplier.isPreferred() && supplier.getPart() != null) {
            partSupplierRepository.findByPart_IdAndPreferredTrue(supplier.getPart().getId())
                    .filter(existing -> !existing.getId().equals(supplier.getId()))
                    .ifPresent(existing -> {
                        existing.setPreferred(false);
                        partSupplierRepository.save(existing);
                    });
        }
        return partSupplierRepository.save(supplier);
    }

    public void deleteSupplier(Long id) {
        partSupplierRepository.deleteById(id);
    }

    public PartCrossReference saveCrossReference(PartCrossReference crossReference) {
        return partCrossReferenceRepository.save(crossReference);
    }

    public void deleteCrossReference(Long id) {
        partCrossReferenceRepository.deleteById(id);
    }

    public Optional<PartCrossReference> findCrossReferenceById(Long id) {
        return partCrossReferenceRepository.findById(id);
    }

    /**
     * Everything needed to make a purchasing decision about one part.
     */
    public PartSourcingDTO sourcingFor(Part part) {
        PartSourcingDTO dto = new PartSourcingDTO();
        dto.setPartId(part.getId());
        dto.setName(part.getName());
        dto.setManufacturer(part.getManufacturer());
        dto.setMpn(part.getMpn());
        dto.setOnHand(part.getQuantity());
        dto.setMinQuantity(part.getMinQuantity());
        dto.setReorderPoint(part.getReorderPoint());
        dto.setStockRecommended(part.isStockRecommended());
        dto.setLeadTimeDaysTypical(part.getLeadTimeDaysTypical());
        dto.setCriticality(part.getCriticality());
        dto.setUnit(part.getUnit());

        dto.setSuppliers(findSuppliers(part.getId()).stream().map(supplier -> {
            PartSourcingDTO.SupplierOffer offer = new PartSourcingDTO.SupplierOffer();
            offer.setId(supplier.getId());
            offer.setVendorName(supplier.getVendor() == null ? null : supplier.getVendor().getName());
            offer.setVendorId(supplier.getVendor() == null ? null : supplier.getVendor().getId());
            offer.setSupplierSku(supplier.getSupplierSku());
            offer.setProductUrl(supplier.getProductUrl());
            offer.setUnitPrice(supplier.getUnitPrice());
            offer.setCurrency(supplier.getCurrency());
            offer.setMoq(supplier.getMoq());
            offer.setLeadTimeDays(supplier.getLeadTimeDays());
            offer.setPriceCheckedAt(supplier.getPriceCheckedAt());
            offer.setPreferred(supplier.isPreferred());
            return offer;
        }).collect(Collectors.toList()));

        dto.setAlternates(findAlternates(part.getId()).stream().map(reference -> {
            PartSourcingDTO.Alternate alternate = new PartSourcingDTO.Alternate();
            alternate.setPartId(reference.getAlternate() == null ? null : reference.getAlternate().getId());
            alternate.setName(reference.getAlternate() == null ? null : reference.getAlternate().getName());
            alternate.setMpn(reference.getAlternate() == null ? null : reference.getAlternate().getMpn());
            alternate.setType(reference.getType() == null ? null : reference.getType().name());
            alternate.setJustification(reference.getJustification());
            return alternate;
        }).collect(Collectors.toList()));

        return dto;
    }

    /**
     * Reorder point from usage rate and lead time, with a safety margin.
     * <p>
     * Returns null rather than a number when there is nothing real to compute
     * from — a made-up reorder point causes worse decisions than no reorder
     * point.
     */
    public Double suggestReorderPoint(double annualUsage, Integer leadTimeDays, Integer criticality) {
        if (annualUsage <= 0 || leadTimeDays == null || leadTimeDays <= 0) {
            return null;
        }
        double dailyUsage = annualUsage / 365.0;
        double duringLeadTime = dailyUsage * leadTimeDays;
        // Safety stock scales with how badly it hurts to be without the part.
        double safetyFactor = criticality == null ? 0.5 : 0.25 * criticality;
        return Math.ceil(duringLeadTime * (1 + safetyFactor));
    }
}
