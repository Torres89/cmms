package com.grash.repository;

import com.grash.model.ComponentEvent;
import com.grash.model.enums.ComponentEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ComponentEventRepository extends JpaRepository<ComponentEvent, Long> {

    List<ComponentEvent> findByComponent_IdOrderByOccurredAtDesc(Long componentId);

    List<ComponentEvent> findByPosition_IdOrderByOccurredAtDesc(Long assetId);

    /**
     * Every component event at or under an asset.
     * <p>
     * Components are fitted to positions, and positions are children of the
     * machine — so asking a machine for its own events returns almost nothing,
     * and the timeline that promises component swaps shows none. The subtree is
     * what a person means by "what happened to this machine".
     */
    @Query(value = "WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM asset WHERE id = :assetId"
            + "  UNION ALL"
            + "  SELECT a.id FROM asset a JOIN subtree s ON a.parent_asset_id = s.id"
            + ") "
            + "SELECT e.* FROM component_event e "
            + "WHERE e.position_id IN (SELECT id FROM subtree) "
            + "ORDER BY e.occurred_at DESC", nativeQuery = true)
    List<ComponentEvent> findInAssetSubtree(@Param("assetId") Long assetId);

    List<ComponentEvent> findByCompany_IdAndOccurredAtAfterOrderByOccurredAtDesc(Long companyId, Date since);

    Optional<ComponentEvent> findFirstByComponent_IdAndTypeOrderByOccurredAtDesc(
            Long componentId, ComponentEventType type);

    /**
     * The open install at a position, if any — used to close it out before a
     * new component goes in.
     */
    Optional<ComponentEvent> findFirstByPosition_IdAndTypeOrderByOccurredAtDesc(
            Long assetId, ComponentEventType type);
}
