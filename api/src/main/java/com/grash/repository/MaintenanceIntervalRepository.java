package com.grash.repository;

import com.grash.model.MaintenanceInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MaintenanceIntervalRepository extends JpaRepository<MaintenanceInterval, Long> {

    List<MaintenanceInterval> findByPreventiveMaintenance_Id(Long preventiveMaintenanceId);

    List<MaintenanceInterval> findByCompany_Id(Long companyId);

    List<MaintenanceInterval> findByMeter_Id(Long meterId);

    void deleteByPreventiveMaintenance_Id(Long preventiveMaintenanceId);

    /** Every interval-driven PM, across all companies — for the daily evaluation. */
    @Query("SELECT DISTINCT i.preventiveMaintenance.id FROM MaintenanceInterval i")
    List<Long> findDistinctPreventiveMaintenanceIds();
}
