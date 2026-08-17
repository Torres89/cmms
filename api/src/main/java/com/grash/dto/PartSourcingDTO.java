package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Everything needed to decide whether, where and how much to buy.
 * <p>
 * The empty case is a first-class answer: no suppliers means no suppliers, not
 * "here's roughly what one might cost".
 */
@Data
@NoArgsConstructor
public class PartSourcingDTO {

    private Long partId;
    private String name;
    private String manufacturer;
    private String mpn;
    private String unit;

    private double onHand;
    private double minQuantity;
    private Double reorderPoint;
    private boolean stockRecommended;
    private Double leadTimeDaysTypical;
    private Integer criticality;

    private List<SupplierOffer> suppliers = new ArrayList<>();
    private List<Alternate> alternates = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class SupplierOffer {
        private Long id;
        private Long vendorId;
        private String vendorName;
        private String supplierSku;
        private String productUrl;
        private Double unitPrice;
        private String currency;
        private Integer moq;
        private Integer leadTimeDays;
        private Date priceCheckedAt;
        private boolean preferred;
    }

    @Data
    @NoArgsConstructor
    public static class Alternate {
        private Long partId;
        private String name;
        private String mpn;
        private String type;
        private String justification;
    }
}
