package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * Where to buy a part — literally, including the URL.
 * <p>
 * Deliberately fillable by hand, from documents, or from a pasted link. A shop
 * with no supplier API access still gets almost all of the value; catalogue
 * adapters are an optional accelerator, never a prerequisite.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_part_supplier_part", columnList = "part_id")
})
public class PartSupplier extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Part part;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    private Vendor vendor;

    private String supplierSku;

    /** The "where to buy" link. */
    @Column(length = 1000)
    private String productUrl;

    private Double unitPrice;

    private String currency;

    /** Minimum order quantity. */
    private Integer moq;

    private Integer leadTimeDays;

    /** When the price was last confirmed — a stale price is worse than none. */
    private Date priceCheckedAt;

    private boolean preferred;

    @Column(length = 1000)
    private String notes;
}
