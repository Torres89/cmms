package com.grash.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.utils.Helper;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Data
@NoArgsConstructor
public class CompanySettings {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private GeneralPreferences generalPreferences = new GeneralPreferences(this);

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private WorkOrderConfiguration workOrderConfiguration = new WorkOrderConfiguration(this);

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private WorkOrderRequestConfiguration WorkOrderRequestConfiguration = new WorkOrderRequestConfiguration(this);

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "companySettings", fetch = FetchType.LAZY)
    private UiConfiguration uiConfiguration = new UiConfiguration(this);
    @OneToOne(fetch = FetchType.LAZY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Company company;

//    @OneToOne
//   private AssetFieldsConfiguration assetFieldsConfiguration;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "companySettings", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Role> roleList = new ArrayList<>();

    // ------------------------------------------------------------------
    // AI provider — which door this company uses (see AiConfigController)
    //
    // Door 1 (their own MCP client) needs nothing here. Door 2 is the
    // customer's own key; Door 3 is the managed add-on on ours. The key is
    // stored encrypted and is never returned by a user-facing endpoint — the
    // settings screen only ever sees a masked suffix.
    // ------------------------------------------------------------------

    /** ANTHROPIC | OPENAI | CUSTOM | MANAGED | NONE */
    private String aiProvider = "NONE";

    @JsonIgnore
    private String aiApiKeyEncrypted;

    private String aiModel;

    /** For OpenAI-compatible endpoints that aren't OpenAI. */
    private String aiBaseUrl;

    /** Optional self-imposed guard, in tokens per calendar month. */
    private Long aiMonthlyTokenCap;

    public CompanySettings(Company company) {
        this.company = company;
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "companySettings", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<CostCategory> costCategories = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "companySettings", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<TimeCategory> timeCategories = new ArrayList<>();


    private List<CostCategory> createCostCategories(List<String> costCategories) {
        return costCategories.stream().map(costCategory -> new CostCategory(costCategory, this)).collect(Collectors.toList());
    }

    private List<TimeCategory> createTimeCategories(List<String> timeCategories) {
        return timeCategories.stream().map(timeCategory -> new TimeCategory(timeCategory, this)).collect(Collectors.toList());
    }

}

