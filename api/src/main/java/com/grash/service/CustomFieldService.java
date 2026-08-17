package com.grash.service;

import com.grash.dto.CustomFieldPatchDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.CustomFieldMapper;
import com.grash.model.CustomField;
import com.grash.model.enums.CustomFieldEntity;
import com.grash.repository.CustomFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomFieldService {
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldMapper customFieldMapper;

    public CustomField create(CustomField customField) {
        return customFieldRepository.save(customField);
    }

    public CustomField update(Long id, CustomFieldPatchDTO customField) {
        if (customFieldRepository.existsById(id)) {
            CustomField savedCustomField = customFieldRepository.findById(id).get();
            return customFieldRepository.save(customFieldMapper.updateCustomField(savedCustomField, customField));
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public Collection<CustomField> getAll() {
        return customFieldRepository.findAll();
    }

    public void delete(Long id) {
        customFieldRepository.deleteById(id);
    }

    public Optional<CustomField> findById(Long id) {
        return customFieldRepository.findById(id);
    }

    /**
     * Every custom field on one entity, scoped to the caller's company.
     */
    public Collection<CustomField> findForEntity(CustomFieldEntity entityType, Long entityId, Long companyId) {
        return customFieldRepository.findByEntityTypeAndEntityIdAndCompany_Id(entityType, entityId, companyId);
    }

    @Transactional
    public void deleteForEntity(CustomFieldEntity entityType, Long entityId) {
        customFieldRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
    }
}
