package com.grash.repository;

import com.grash.model.PreventiveMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface PreventiveMaintenanceRepository extends JpaRepository<PreventiveMaintenance, Long>,
        JpaSpecificationExecutor<PreventiveMaintenance> {
    Collection<PreventiveMaintenance> findByCompany_Id(@Param("x") Long id);

    List<PreventiveMaintenance> findByCreatedAtBeforeAndCompany_Id(Date start, Long companyId);

    void deleteByCompany_IdAndIsDemoTrue(Long companyId);

    Optional<PreventiveMaintenance> findByIdAndCompany_Id(Long id, Long companyId);

    List<PreventiveMaintenance> findByAsset_Id(Long assetId);

    /**
     * Every PM attached to a machine or to anything beneath it in the breakdown
     * structure — a spindle PM is still one of the machine's PMs.
     */
    @Query(value = "WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM asset WHERE id = :assetId"
            + "  UNION ALL"
            + "  SELECT a.id FROM asset a JOIN subtree s ON a.parent_asset_id = s.id"
            + ") "
            + "SELECT p.* FROM preventive_maintenance p "
            + "WHERE p.asset_id IN (SELECT id FROM subtree)", nativeQuery = true)
    List<PreventiveMaintenance> findInAssetSubtree(@Param("assetId") Long assetId);

    @Query("SELECT CASE WHEN COUNT(p) > :threshold THEN true ELSE false END " +
            "FROM PreventiveMaintenance p WHERE p.company.id = :companyId")
    boolean hasMoreThan(@Param("companyId") Long companyId, @Param("threshold") Long threshold);
}
