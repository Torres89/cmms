package com.grash.repository;

import com.grash.model.PartSupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartSupplierRepository extends JpaRepository<PartSupplier, Long> {

    List<PartSupplier> findByPart_IdOrderByPreferredDescUnitPriceAsc(Long partId);

    Optional<PartSupplier> findByPart_IdAndPreferredTrue(Long partId);

    Optional<PartSupplier> findByPart_IdAndVendor_Id(Long partId, Long vendorId);

    void deleteByPart_Id(Long partId);
}
