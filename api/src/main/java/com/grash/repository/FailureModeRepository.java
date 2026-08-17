package com.grash.repository;

import com.grash.model.FailureMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FailureModeRepository extends JpaRepository<FailureMode, Long> {

    List<FailureMode> findByEquipmentClassAndCompany_Id(String equipmentClass, Long companyId);

    List<FailureMode> findByEquipmentClassAndSubunitAndCompany_Id(
            String equipmentClass, String subunit, Long companyId);

    Optional<FailureMode> findByCodeAndCompany_Id(String code, Long companyId);

    List<FailureMode> findByCompany_Id(Long companyId);
}
