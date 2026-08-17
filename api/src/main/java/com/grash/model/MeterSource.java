package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.SourceType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * How a meter gets its readings.
 * <p>
 * The {@code config} blob holds whatever the adapter needs — an MTConnect agent
 * URL and DataItem id, an OPC UA node, ISO 15143 credentials — because those
 * differ per protocol and hard-coding columns for each would mean a schema
 * change per integration.
 * <p>
 * Absence of a row means {@link SourceType#MANUAL}, which is the case that
 * always works and the one every PM is designed to survive on.
 */
@Entity
@Data
@NoArgsConstructor
public class MeterSource extends CompanyAudit {

    @OneToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meter meter;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType = SourceType.MANUAL;

    /** Adapter-specific settings as JSON. */
    @Column(columnDefinition = "text")
    private String config;

    private Date lastSyncAt;

    @Column(length = 2000)
    private String lastSyncError;

    /** Minutes between polls; adapters that push ignore it. */
    private Integer pollIntervalMinutes;

    private boolean enabled = true;
}
