package com.grash.repository;

import com.grash.model.CustomField;
import com.grash.model.enums.CustomFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CustomFieldRepository extends JpaRepository<CustomField, Long> {

    Collection<CustomField> findByEntityTypeAndEntityIdAndCompany_Id(
            CustomFieldEntity entityType, Long entityId, Long companyId);

    Collection<CustomField> findByCompany_Id(Long companyId);

    void deleteByEntityTypeAndEntityId(CustomFieldEntity entityType, Long entityId);
}
