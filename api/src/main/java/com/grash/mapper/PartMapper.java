package com.grash.mapper;

import com.grash.dto.PartMiniDTO;
import com.grash.dto.PartPatchDTO;
import com.grash.dto.PartShowDTO;
import com.grash.dto.FileShowDTO;
import com.grash.model.Part;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {CustomerMapper.class, VendorMapper.class, UserMapper.class, TeamMapper.class, FileMapper.class})
public interface PartMapper {
    /**
     * Same reasoning as AssetMapper: a PATCH replaces every mapped field, so
     * the sourcing fields are ignored when absent rather than being wiped by an
     * existing client that knows nothing about them.
     */
    @Mapping(target = "manufacturer", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "mpn", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "preferredSupplierSku",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "leadTimeDaysTypical",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "criticality", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "shelfLifeDays", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "storageConditions",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "reorderPoint", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Part updatePart(@MappingTarget Part entity, PartPatchDTO dto);

    @Mappings({})
    PartPatchDTO toPatchDto(Part model);

    @Mappings({})
    PartMiniDTO toMiniDto(Part model);

    PartShowDTO toShowDto(Part model);
}
