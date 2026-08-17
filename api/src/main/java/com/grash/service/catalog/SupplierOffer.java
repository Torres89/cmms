package com.grash.service.catalog;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * One supplier's answer for one part.
 */
@Data
@NoArgsConstructor
public class SupplierOffer {

    private String supplierKey;

    private String supplierName;

    private String sku;

    private String manufacturer;

    private String mpn;

    private String description;

    private Double unitPrice;

    private String currency;

    private Integer moq;

    private Integer leadTimeDays;

    private Boolean inStock;

    private String productUrl;

    private String imageUrl;

    /** When this offer was retrieved — a price with no date is not a price. */
    private Date retrievedAt = new Date();
}
