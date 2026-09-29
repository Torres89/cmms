package com.grash.controller;

import com.grash.model.OwnUser;
import com.grash.model.abstracts.CategoryAbstract;
import com.grash.model.enums.RoleType;

/**
 * Tenant check for the category controllers.
 * <p>
 * Categories extend {@code Audit}, not {@code CompanyAudit}, so loading one
 * does not reject another company's row: they belong to a company through
 * their company settings, and every by-id endpoint has to check that itself.
 * Another company's category is reported as not found.
 */
final class CategoryAccess {

    private CategoryAccess() {
    }

    static boolean isAccessible(CategoryAbstract category, OwnUser user) {
        if (RoleType.ROLE_SUPER_ADMIN.equals(user.getRole().getRoleType())) {
            return true;
        }
        return category.getCompanySettings() != null
                && user.getCompany() != null
                && user.getCompany().getCompanySettings() != null
                && category.getCompanySettings().getId().equals(user.getCompany().getCompanySettings().getId());
    }
}
