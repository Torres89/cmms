package com.grash.dto;

import com.grash.model.*;
import com.grash.model.enums.AssetLevel;
import com.grash.model.enums.AssetStatus;
import com.grash.model.enums.TrackingClass;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Date;

@Data
@NoArgsConstructor
public class AssetPatchDTO {
    private boolean archived;

    @Schema(implementation = IdDTO.class)
    private File image;

    @Schema(implementation = IdDTO.class)
    private Location location;

    @Schema(implementation = IdDTO.class)
    private Asset parentAsset;

    private String area;

    private String barCode;

    private String nfcId;

    @Schema(implementation = IdDTO.class)
    private AssetCategory category;

    private String name;

    @Schema(implementation = IdDTO.class)
    private OwnUser primaryUser;

    @Schema(implementation = IdDTO.class)
    private Deprecation deprecation;

    private Date warrantyExpirationDate;

    private String additionalInfos;

    private String serialNumber;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<OwnUser> assignedTo;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<Customer> customers;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<Vendor> vendors;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<Team> teams;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<File> files;

    @ArraySchema(schema = @Schema(implementation = IdDTO.class))
    private Collection<Part> parts;

    private AssetStatus status;

    private Double acquisitionCost;

    private String power;

    private String manufacturer;

    private String model;

    private String description;

    private Date inServiceDate;

    private AssetLevel level;

    private String positionCode;

    private String functionalDescription;

    private Integer criticality;

    private Double downtimeCostPerHour;

    private Double replacementCost;

    private TrackingClass trackingClass;

    private String equipmentClass;
}
