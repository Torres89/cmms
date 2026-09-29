package com.grash.repository;

import com.grash.model.CompanyAssetPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyAssetPackRepository extends JpaRepository<CompanyAssetPack, Long> {

    List<CompanyAssetPack> findByCompanyIdOrderByPackKeyAsc(Long companyId);

    Optional<CompanyAssetPack> findByCompanyIdAndPackKey(Long companyId, String packKey);
}
