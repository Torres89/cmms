package com.grash.repository;

import com.grash.model.FaultCodeDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FaultCodeDictionaryRepository extends JpaRepository<FaultCodeDictionary, Long> {

    /**
     * Look a code up for a tenant.
     * <p>
     * The customer's own entry wins over shared reference data — their manual is
     * more authoritative about their machine than a generic list is.
     */
    @Query("SELECT f FROM FaultCodeDictionary f "
            + "WHERE UPPER(f.code) = UPPER(:code) "
            + "AND (f.companyId IS NULL OR f.companyId = :companyId) "
            + "AND (:equipmentClass IS NULL OR f.equipmentClass IS NULL "
            + "     OR f.equipmentClass = :equipmentClass) "
            + "ORDER BY CASE WHEN f.companyId IS NULL THEN 1 ELSE 0 END")
    List<FaultCodeDictionary> lookup(@Param("code") String code,
                                     @Param("companyId") Long companyId,
                                     @Param("equipmentClass") String equipmentClass);

    Optional<FaultCodeDictionary> findFirstByCodeIgnoreCaseAndCompanyIdAndEquipmentClass(
            String code, Long companyId, String equipmentClass);

    List<FaultCodeDictionary> findByCompanyIdOrCompanyIdIsNull(Long companyId);

    List<FaultCodeDictionary> findByEquipmentClassAndCompanyIdOrCompanyIdIsNull(
            String equipmentClass, Long companyId);
}
