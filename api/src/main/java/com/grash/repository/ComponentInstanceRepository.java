package com.grash.repository;

import com.grash.model.ComponentInstance;
import com.grash.model.enums.ComponentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComponentInstanceRepository
        extends JpaRepository<ComponentInstance, Long>, JpaSpecificationExecutor<ComponentInstance> {

    List<ComponentInstance> findByCurrentPosition_Id(Long assetId);

    List<ComponentInstance> findByCompany_IdAndStatus(Long companyId, ComponentStatus status);

    Optional<ComponentInstance> findBySerialNumberAndCompany_Id(String serialNumber, Long companyId);

    List<ComponentInstance> findByCompany_Id(Long companyId);

    /**
     * Every component installed at or under an asset — the whole subtree, so a
     * reading on the machine rolls hours into the spindle cartridge inside it.
     */
    @Query(value = "WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM asset WHERE id = :assetId"
            + "  UNION ALL"
            + "  SELECT a.id FROM asset a JOIN subtree s ON a.parent_asset_id = s.id"
            + ") "
            + "SELECT c.* FROM component_instance c "
            + "WHERE c.current_position_id IN (SELECT id FROM subtree) "
            + "AND c.status = 'IN_SERVICE'", nativeQuery = true)
    List<ComponentInstance> findInstalledInSubtree(@Param("assetId") Long assetId);

    /**
     * Life-limited components close to a limit, for the 10 % / 5 % alerts.
     */
    @Query("SELECT c FROM ComponentInstance c WHERE c.company.id = :companyId "
            + "AND c.status = com.grash.model.enums.ComponentStatus.IN_SERVICE "
            + "AND (c.hourLimit IS NOT NULL OR c.cycleLimit IS NOT NULL OR c.calendarLimitMonths IS NOT NULL)")
    List<ComponentInstance> findLifeLimitedInService(@Param("companyId") Long companyId);
}
