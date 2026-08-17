package com.grash.repository;

import com.grash.model.FaultEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface FaultEventRepository extends JpaRepository<FaultEvent, Long> {

    List<FaultEvent> findByAsset_IdOrderByOccurredAtDesc(Long assetId);

    List<FaultEvent> findByAsset_IdAndClearedAtIsNull(Long assetId);

    List<FaultEvent> findByAsset_IdAndCodeIgnoreCaseOrderByOccurredAtDesc(Long assetId, String code);

    Optional<FaultEvent> findFirstByAsset_IdAndCodeIgnoreCaseAndClearedAtIsNull(Long assetId, String code);

    List<FaultEvent> findByCompany_IdAndOccurredAtAfterOrderByOccurredAtDesc(Long companyId, Date since);

    /**
     * Which codes this machine throws most — the input to "is this alarm normal
     * for this machine or is something actually wrong".
     */
    @Query("SELECT e.code, COUNT(e), MAX(e.occurredAt) FROM FaultEvent e "
            + "WHERE e.asset.id = :assetId GROUP BY e.code ORDER BY COUNT(e) DESC")
    List<Object[]> frequencyByCode(@Param("assetId") Long assetId);
}
