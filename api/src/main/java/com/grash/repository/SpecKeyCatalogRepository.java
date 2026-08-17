package com.grash.repository;

import com.grash.model.SpecKeyCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecKeyCatalogRepository extends JpaRepository<SpecKeyCatalog, Long> {

    List<SpecKeyCatalog> findByEquipmentClassAndCompany_IdOrderByDisplayOrderAscSpecGroupAscSpecKeyAsc(
            String equipmentClass, Long companyId);

    Optional<SpecKeyCatalog> findByEquipmentClassAndSpecKeyAndCompany_Id(
            String equipmentClass, String specKey, Long companyId);

    long countByEquipmentClassAndCompany_Id(String equipmentClass, Long companyId);

    List<SpecKeyCatalog> findByCompany_Id(Long companyId);
}
