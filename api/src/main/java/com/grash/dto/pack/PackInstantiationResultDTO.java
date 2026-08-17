package com.grash.dto.pack;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * What instantiating a pack created — or, on a dry run, what it would create.
 */
@Data
@NoArgsConstructor
public class PackInstantiationResultDTO {

    private String packKey;

    private String packVersion;

    private Long assetId;

    private boolean dryRun;

    private List<String> positions = new ArrayList<>();

    private List<String> specKeys = new ArrayList<>();

    private List<String> meters = new ArrayList<>();

    private List<String> preventiveMaintenances = new ArrayList<>();

    private List<String> failureModes = new ArrayList<>();

    private List<String> consumables = new ArrayList<>();

    public int getTotalCreated() {
        return positions.size() + specKeys.size() + meters.size()
                + preventiveMaintenances.size() + failureModes.size() + consumables.size();
    }

    /**
     * A single line for the commissioning checklist that doubles as the
     * customer handover document.
     */
    public String getSummary() {
        return String.format(
                "%s %d positions, %d spec keys, %d meters, %d PM plans, %d failure modes, %d consumables",
                dryRun ? "Would create" : "Created",
                positions.size(), specKeys.size(), meters.size(),
                preventiveMaintenances.size(), failureModes.size(), consumables.size());
    }
}
