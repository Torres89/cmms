package com.grash.repository;

import com.grash.model.AssetBomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssetBomLineRepository extends JpaRepository<AssetBomLine, Long> {

    List<AssetBomLine> findByAsset_IdOrderByPositionCodeAsc(Long assetId);

    List<AssetBomLine> findByAsset_IdAndPositionCode(Long assetId, String positionCode);

    List<AssetBomLine> findByAsset_IdAndConsumableTrue(Long assetId);

    /**
     * Every BOM line on a machine <em>or anything beneath it</em>.
     * <p>
     * A coolant filter belongs to the coolant subunit, but "what parts does this
     * machine take" has to answer with it — asking the top-level asset only
     * would report an empty BOM for a fully documented machine.
     */
    @Query(value = "WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM asset WHERE id = :assetId"
            + "  UNION ALL"
            + "  SELECT a.id FROM asset a JOIN subtree s ON a.parent_asset_id = s.id"
            + ") "
            + "SELECT b.* FROM asset_bom_line b "
            + "WHERE b.asset_id IN (SELECT id FROM subtree) "
            + "ORDER BY b.position_code", nativeQuery = true)
    List<AssetBomLine> findInAssetSubtree(@Param("assetId") Long assetId);

    @Query(value = "WITH RECURSIVE subtree AS ("
            + "  SELECT id FROM asset WHERE id = :assetId"
            + "  UNION ALL"
            + "  SELECT a.id FROM asset a JOIN subtree s ON a.parent_asset_id = s.id"
            + ") "
            + "SELECT b.* FROM asset_bom_line b "
            + "WHERE b.asset_id IN (SELECT id FROM subtree) AND b.consumable = true "
            + "ORDER BY b.position_code", nativeQuery = true)
    List<AssetBomLine> findConsumablesInAssetSubtree(@Param("assetId") Long assetId);

    List<AssetBomLine> findByPart_Id(Long partId);

    void deleteByAsset_Id(Long assetId);
}
