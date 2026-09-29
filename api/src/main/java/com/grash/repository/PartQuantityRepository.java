package com.grash.repository;

import com.grash.model.PartQuantity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.Optional;

public interface PartQuantityRepository extends JpaRepository<PartQuantity, Long> {
    Collection<PartQuantity> findByCompany_Id(Long id);

    Collection<PartQuantity> findByWorkOrder_Id(Long id);

    Collection<PartQuantity> findByPart_Id(Long id);

    Collection<PartQuantity> findByPurchaseOrder_Id(Long id);

    void deleteByCompany_IdAndIsDemoTrue(Long companyId);

    /**
     * When this part was last used on a completed work order against any of
     * these assets — for a consumable, the last time it was replaced.
     */
    @Query("SELECT MAX(pq.workOrder.completedOn) FROM PartQuantity pq " +
            "WHERE pq.part.id = :partId AND pq.workOrder.asset.id IN :assetIds " +
            "AND pq.workOrder.status = com.grash.model.enums.Status.COMPLETE")
    Date findLastCompletedUse(@Param("partId") Long partId, @Param("assetIds") Collection<Long> assetIds);
}
