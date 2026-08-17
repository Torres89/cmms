package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.ComponentEventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * One entry in a component's back-to-birth ledger. Append-only: entries are
 * never edited, because the whole value of a ledger is that it is the record.
 * <p>
 * Envers-audited — this is one of the two compliance-relevant tables. Its
 * relations point at entities that are not themselves audited, so each carries
 * {@code NOT_AUDITED}: we want the history of the ledger entry, not a parallel
 * history of every asset it references.
 */
@Entity
@Data
@NoArgsConstructor
@Audited
@Table(indexes = {
        @Index(name = "idx_component_event_component", columnList = "component_id"),
        @Index(name = "idx_component_event_occurred", columnList = "occurred_at")
})
public class ComponentEvent extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private ComponentInstance component;

    @Enumerated(EnumType.STRING)
    @NotNull
    private ComponentEventType type;

    /** The asset position involved, for installs and removals. */
    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Asset position;

    @NotNull
    private Date occurredAt;

    /** Machine hours at the moment of the event. */
    private Double positionMeterValue;

    /** The component's own accumulated hours at the moment of the event. */
    private Double componentHours;

    private Double componentCycles;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private OwnUser performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Vendor vendor;

    private Double cost;

    @Column(length = 2000)
    private String reason;

    /** Certificate, RMA number, teardown report reference. */
    private String documentReference;
}
