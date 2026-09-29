package com.grash.controller;

import com.grash.model.Company;
import com.grash.model.CompanySettings;
import com.grash.model.OwnUser;
import com.grash.model.Role;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.utils.Helper;

/**
 * Plain in-memory tenants and users for the authorization tests. The default
 * roles come from {@link Helper#getDefaultRoles()}, so these tests follow the
 * real permission sets rather than a copy of them.
 */
final class SecurityFixtures {

    private SecurityFixtures() {
    }

    static Company company(long id) {
        Company company = new Company();
        company.setId(id);
        CompanySettings settings = new CompanySettings(company);
        settings.setId(id * 10);
        company.setCompanySettings(settings);
        return company;
    }

    /** One of the shared default roles (company settings null). */
    static Role defaultRole(RoleCode code) {
        Role role = Helper.getDefaultRoles().stream()
                .filter(candidate -> candidate.getCode() == code)
                .findFirst()
                .orElseThrow();
        role.setId((long) code.ordinal() + 1);
        return role;
    }

    /** A custom role belonging to one company. */
    static Role customRole(long id, Company company) {
        Role role = defaultRole(RoleCode.ADMIN);
        role.setId(id);
        role.setCode(RoleCode.USER_CREATED);
        role.setName("Custom " + id);
        role.setCompanySettings(company.getCompanySettings());
        return role;
    }

    static OwnUser user(long id, Company company, Role role) {
        OwnUser user = new OwnUser();
        user.setId(id);
        user.setCompany(company);
        user.setRole(role);
        return user;
    }

    static Role superAdminRole(Company company) {
        Role role = customRole(999L, company);
        role.setRoleType(RoleType.ROLE_SUPER_ADMIN);
        role.setCode(RoleCode.ADMIN);
        return role;
    }
}
