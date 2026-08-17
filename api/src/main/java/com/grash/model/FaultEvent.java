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
 * A machine reported a fault.
 * <p>
 * Distinct from {@link FailureEvent}: a fault event is what the control said
 * ("SV0410", "SPN 100 FMI 1"), a failure event is what a person concluded had
 * actually broken. Keeping them apart is what lets "this alarm came up eleven
 * times and twice it was the real thing" be a question with an answer.
 */
@Entity
@Data
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_fault_event_asset", columnList = "asset_id"),
        @Index(name = "idx_fault_event_code", columnList = "code"),
        @Index(name = "idx_fault_event_occurred", columnList = "occurred_at")
})
public class FaultEvent extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Asset asset;

    /** "SV0410" for a Fanuc alarm, "SPN 100 FMI 1" for J1939. */
    @NotNull
    private String code;

    @Column(length = 2000)
    private String description;

    private String severity;

    @NotNull
    private Date occurredAt;

    /** Null while the fault is still active. */
    private Date clearedAt;

    @Enumerated(EnumType.STRING)
    private SourceType source = SourceType.MANUAL;

    /** The untouched payload, so a mapping bug is recoverable after the fact. */
    @Column(columnDefinition = "text")
    private String rawPayload;

    /** Set when someone turned this fault into work. */
    @ManyToOne(fetch = FetchType.LAZY)
    private WorkOrder workOrder;

    @Transient
    public boolean isActive() {
        return clearedAt == null;
    }
}
