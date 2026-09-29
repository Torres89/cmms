package com.grash.dto;

import com.grash.model.abstracts.BasicInfos;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VendorPatchDTO extends BasicInfos {

    // The edit form sends it; without it here a rename returned 200 and changed nothing.
    private String companyName;

    private String vendorType;

    private String description;

    private long rate;
}
