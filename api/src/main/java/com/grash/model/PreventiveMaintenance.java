package com.grash.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grash.model.abstracts.WorkOrderBase;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.TriggerMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Entity
@Data
@NoArgsConstructor
public class PreventiveMaintenance extends WorkOrderBase {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String customId;

    private String name;

    private boolean isDemo;

    @OneToOne(cascade = CascadeType.ALL)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Schedule schedule = new Schedule(this);

    /**
     * How this PM's {@link MaintenanceInterval}s combine.
     * <p>
     * Manufacturer charts say "every 500 hours or 3 months", so the default is
     * whichever comes first. The {@link Schedule} above still drives the plain
     * calendar case; intervals take over as soon as any exist.
     */
    @Enumerated(EnumType.STRING)
    private TriggerMode triggerMode = TriggerMode.WHICHEVER_FIRST;

    /**
     * Which equipment class this PM was templated from, when it came out of a
     * vertical pack. Lets a pack update find the PMs it created.
     */
    private String equipmentClass;

    /** The pack template key this PM was instantiated from, if any. */
    private String templateKey;

    public boolean canBeEditedBy(OwnUser user) {
        return user.getRole().getEditOtherPermissions().contains(PermissionEntity.PREVENTIVE_MAINTENANCES)
                || this.getCreatedBy().equals(user.getId());
    }

}

