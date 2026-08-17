package com.grash.repository;

import com.grash.model.PartCrossReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartCrossReferenceRepository extends JpaRepository<PartCrossReference, Long> {

    List<PartCrossReference> findByPart_Id(Long partId);

    List<PartCrossReference> findByAlternate_Id(Long partId);

    void deleteByPart_Id(Long partId);
}
