package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Input for an entry in a component's ledger.
 * <p>
 * {@code meterValue} matters more than it looks: recording the machine hours at
 * the moment of an install is what makes "how long did the last one last"
 * answerable years later.
 */
@Data
@NoArgsConstructor
public class ComponentActionDTO {

    private Long positionAssetId;

    private Date occurredAt;

    /** Machine hours (or cycles) at the time of the action. */
    private Double meterValue;

    private Long workOrderId;

    private Long vendorId;

    private Double cost;

    private String reason;
}
