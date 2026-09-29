package com.grash.model;

import com.grash.model.abstracts.DateAudit;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A vertical pack registered by one customer at runtime.
 * <p>
 * Shipped packs live on the classpath and are the same for everyone. A pack
 * registered through the API belongs to the company that registered it and is
 * only ever visible to that company. The pack itself is stored as the JSON it
 * was registered with, so the pack format can grow without a migration.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Table(name = "company_asset_pack",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_asset_pack_key",
                columnNames = {"company_id", "pack_key"}))
public class CompanyAssetPack extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "pack_key", nullable = false, length = 128)
    private String packKey;

    @Column(name = "version", length = 64)
    private String version;

    @NotNull
    @Column(name = "pack_json", nullable = false, columnDefinition = "text")
    private String packJson;
}
