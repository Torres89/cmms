package com.grash.repository;

import com.grash.model.AssetSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetSpecRepository extends JpaRepository<AssetSpec, Long> {

    List<AssetSpec> findByAsset_IdOrderBySpecGroupAscSpecKeyAsc(Long assetId);

    Collection<AssetSpec> findByAsset_IdAndSpecGroup(Long assetId, String specGroup);

    Optional<AssetSpec> findByAsset_IdAndSpecKey(Long assetId, String specKey);

    void deleteByAsset_Id(Long assetId);

    /**
     * Values still waiting for a human to confirm them. Drives the review queue
     * that makes commissioning approve-all-then-correct rather than
     * confirm-each.
     */
    @Query("SELECT s FROM AssetSpec s WHERE s.company.id = :companyId "
            + "AND s.verifiedBy IS NULL AND s.source <> com.grash.model.enums.SpecSource.MANUAL_ENTRY")
    List<AssetSpec> findUnverified(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(s) FROM AssetSpec s WHERE s.asset.id = :assetId "
            + "AND (s.valueText IS NOT NULL OR s.valueNum IS NOT NULL)")
    long countCaptured(@Param("assetId") Long assetId);
}
