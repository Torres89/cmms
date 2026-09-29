package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * "Order what's due": the consumables coming up on a machine, what they cost,
 * and which of them are already too late to order comfortably.
 */
@Data
@NoArgsConstructor
public class RestockKitDTO {

    private Long assetId;

    private String assetName;

    private int horizonDays;

    /** Measured usage rate, so the dates are the machine's, not a guess. */
    private double hoursPerDay;

    private double estimatedTotal;

    private List<KitLine> lines = new ArrayList<>();

    private String note;

    @Data
    @NoArgsConstructor
    public static class KitLine {
        private Long partId;
        private String name;
        private String mpn;
        private String positionCode;
        private String unit;
        private double quantity;
        private double onHand;
        /** How many more are needed than are on the shelf. */
        private double shortfall;
        /** Negative when already overdue. */
        private Integer daysUntilDue;
        /**
         * The last completed work order that used this part on the machine, or
         * when the line was documented if it has never been replaced here.
         */
        private Date lastReplacedAt;
        private String supplierName;
        private Double unitPrice;
        private String currency;
        private Integer leadTimeDays;
        private String productUrl;
        /** Due sooner than the lead time — order it today. */
        private boolean urgent;
    }
}
