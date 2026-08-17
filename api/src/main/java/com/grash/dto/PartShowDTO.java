package com.grash.dto;

import com.grash.dto.FileShowDTO;
import com.grash.model.PartCategory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@NoArgsConstructor
public class PartShowDTO extends AuditShowDTO {

    private String name;

    private double cost;

    private CategoryMiniDTO category;

    private boolean nonStock;

    private String barcode;

    private String description;

    private double quantity;

    private String additionalInfos;

    private String area;

    private double minQuantity;

    private LocationMiniDTO location;

    private FileShowDTO image;

    private Collection<UserMiniDTO> assignedTo;

    private Collection<FileShowDTO> files;

    private Collection<CustomerMiniDTO> customers;

    private Collection<VendorMiniDTO> vendors;

    private Collection<TeamMiniDTO> teams;

    private String unit;

    // Part identity and sourcing. Without these on the DTO the sourcing view,
    // the restock kit and every API consumer cannot see what a part actually is
    // -- a name and a cost is not an identity.

    private String manufacturer;

    /** The manufacturer part number: the part's real identity. */
    private String mpn;

    private String preferredSupplierSku;

    private Double leadTimeDaysTypical;

    private Integer criticality;

    private Integer shelfLifeDays;

    private String storageConditions;

    private boolean stockRecommended;

    private Double reorderPoint;
}
