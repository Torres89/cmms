package com.grash.repository;

import com.grash.model.MeterSource;
import com.grash.model.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeterSourceRepository extends JpaRepository<MeterSource, Long> {

    Optional<MeterSource> findByMeter_Id(Long meterId);

    List<MeterSource> findByCompany_Id(Long companyId);

    List<MeterSource> findBySourceTypeAndEnabledTrue(SourceType sourceType);

    List<MeterSource> findByEnabledTrue();
}
