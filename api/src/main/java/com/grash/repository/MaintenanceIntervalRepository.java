package com.grash.repository;

import com.grash.model.MaintenanceInterval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceIntervalRepository extends JpaRepository<MaintenanceInterval, Long> {

    List<MaintenanceInterval> findByPreventiveMaintenance_Id(Long preventiveMaintenanceId);

    List<MaintenanceInterval> findByCompany_Id(Long companyId);

    List<MaintenanceInterval> findByMeter_Id(Long meterId);

    void deleteByPreventiveMaintenance_Id(Long preventiveMaintenanceId);
}
