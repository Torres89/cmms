package com.grash.dto;

import com.grash.model.AssetCategory;
import com.grash.model.Deprecation;
import com.grash.dto.FileShowDTO;
import com.grash.model.enums.AssetLevel;
import com.grash.model.enums.AssetStatus;
import com.grash.model.enums.TrackingClass;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class AssetShowDTO extends AuditShowDTO {

    private boolean archived;

    private boolean hasChildren;

    private String description;

    private FileShowDTO image;

    private LocationMiniDTO location;

    private AssetMiniDTO parentAsset;

    private String area;

    private String barCode;

    private String nfcId;

    private CategoryMiniDTO category;

    private String name;

    private UserMiniDTO primaryUser;

    private List<UserMiniDTO> assignedTo = new ArrayList<>();

    private List<TeamMiniDTO> teams = new ArrayList<>();

    private List<VendorMiniDTO> vendors = new ArrayList<>();

    private List<CustomerMiniDTO> customers = new ArrayList<>();

    private Deprecation deprecation;

    private Date warrantyExpirationDate;

    private Date inServiceDate;

    private String additionalInfos;

    private String serialNumber;

    private String model;

    private AssetStatus status = AssetStatus.OPERATIONAL;

    private Double acquisitionCost;

    private List<FileMiniDTO> files = new ArrayList<>();

    private List<PartMiniDTO> parts = new ArrayList<>();

    private String power;

    private String manufacturer;

    private String customId;

    // The machine-specialist layer. Without these on the DTO the dossier page,
    // the pack tooling and every API consumer are blind to the structure.

    private AssetLevel level;

    private String positionCode;

    private String functionalDescription;

    private Integer criticality;

    private Double downtimeCostPerHour;

    private Double replacementCost;

    private TrackingClass trackingClass;

    private String equipmentClass;
}
