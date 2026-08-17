package com.grash.mapper;

import com.grash.dto.AssetMiniDTO;
import com.grash.dto.AssetPatchDTO;
import com.grash.dto.AssetShowDTO;
import com.grash.dto.MeterShowDTO;
import com.grash.model.Asset;
import com.grash.model.Meter;
import com.grash.model.Reading;
import com.grash.service.AssetService;
import com.grash.service.ReadingService;
import com.grash.utils.AuditComparator;
import com.grash.utils.Helper;
import org.mapstruct.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

@Mapper(componentModel = "spring", uses = {CustomerMapper.class, VendorMapper.class, UserMapper.class,
        TeamMapper.class, FileMapper.class, PartMapper.class, FileMapper.class})
public interface AssetMapper {
    /**
     * A PATCH here replaces every mapped field, so a body that omits a field
     * clears it. That is the established contract for the original columns and
     * callers rely on it, but it is wrong for the machine-specialist fields:
     * an existing client PATCHing a name would silently wipe the asset's level
     * out of the equipment breakdown structure, and an asset with a null level
     * then disappears from the asset list. These are ignored when absent.
     */
    @Mapping(target = "level", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "positionCode", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "functionalDescription",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "criticality", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "downtimeCostPerHour",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "replacementCost",
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "trackingClass", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "equipmentClass", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Asset updateAsset(@MappingTarget Asset entity, AssetPatchDTO dto);

    @Mappings({})
    AssetPatchDTO toPatchDto(Asset model);

    AssetShowDTO toShowDto(Asset model, @Context AssetService assetService);

    @Mapping(target = "parentId", source = "parentAsset.id")
    @Mapping(target = "locationId", source = "location.id")
    AssetMiniDTO toMiniDto(Asset model);

    @AfterMapping
    default AssetShowDTO toShowDto(Asset model, @MappingTarget AssetShowDTO target,
                                   @Context AssetService assetService) {
        target.setHasChildren(assetService.hasChildren(model.getId()));
        return target;
    }
}
