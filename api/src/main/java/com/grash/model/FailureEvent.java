package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.DetectionStage;
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
 * What actually broke, this time, on this machine.
 * <p>
 * Capturing this well matters more than the schema does. On work-order close
 * the UI shows three dropdowns pre-filtered by equipment class and subunit and
 * ranked by what has been used on this machine before: if it costs fifteen
 * extra seconds it gets filled in, and if it costs two minutes it does not —
 * and the whole reliability story dies with it.
 * <p>
 * Envers-audited alongside {@link ComponentEvent}. Its relations are marked
 * {@code NOT_AUDITED} because we want the history of this record, not a
 * parallel history of every entity it points at.
 */
@Entity
@Data
@NoArgsConstructor
@Audited
@Table(indexes = {
        @Index(name = "idx_failure_event_asset", columnList = "asset_id"),
        @Index(name = "idx_failure_event_mode", columnList = "failure_mode_id")
})
public class FailureEvent extends CompanyAudit {

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private ComponentInstance component;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private FailureMode failureMode;

    /**
     * When the failure actually happened, as opposed to when it was typed in.
     * <p>
     * Without this the two are the same date, so a failure recorded a week
     * later lands today — and MTBF, which is nothing but the spacing between
     * these dates, is wrong in exactly the cases where back-filling matters.
     * Nullable so existing rows keep working; readers fall back to
     * {@code createdAt}.
     */
    private Date occurredAt;

    /** The physical process, where it differed from the catalogue's typical one. */
    @Column(length = 2000)
    private String mechanism;

    @Column(length = 2000)
    private String cause;

    private String detectionMethod;

    @Enumerated(EnumType.STRING)
    private DetectionStage detectedAt;

    /** 1..5 */
    private Integer severity;

    private Integer downtimeMinutes;

    private Double repairCost;

    @Column(length = 4000)
    private String correctiveAction;

    @Column(length = 4000)
    private String preventiveRecommendation;
}
