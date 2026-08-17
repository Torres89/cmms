package com.grash.repository;

import com.grash.model.FailureEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface FailureEventRepository extends JpaRepository<FailureEvent, Long> {

    List<FailureEvent> findByAsset_IdOrderByCreatedAtDesc(Long assetId);

    List<FailureEvent> findByAsset_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long assetId, Date since);

    List<FailureEvent> findByComponent_IdOrderByCreatedAtDesc(Long componentId);

    List<FailureEvent> findByWorkOrder_Id(Long workOrderId);

    /**
     * The Pareto: which failure modes actually cost this machine its uptime.
     */
    @Query("SELECT e.failureMode.code, e.failureMode.nameEn, COUNT(e), "
            + "COALESCE(SUM(e.downtimeMinutes), 0), COALESCE(SUM(e.repairCost), 0) "
            + "FROM FailureEvent e WHERE e.asset.id = :assetId AND e.failureMode IS NOT NULL "
            + "GROUP BY e.failureMode.code, e.failureMode.nameEn "
            + "ORDER BY COALESCE(SUM(e.downtimeMinutes), 0) DESC")
    List<Object[]> summariseByFailureMode(@Param("assetId") Long assetId);

    /**
     * Which failure modes this specific machine has actually seen — used to
     * rank diagnostic candidates by what has really happened here rather than
     * by what a catalogue says is possible.
     */
    @Query("SELECT e.failureMode.id, COUNT(e) FROM FailureEvent e "
            + "WHERE e.asset.id = :assetId AND e.failureMode IS NOT NULL "
            + "GROUP BY e.failureMode.id")
    List<Object[]> countByFailureModeForAsset(@Param("assetId") Long assetId);
}
