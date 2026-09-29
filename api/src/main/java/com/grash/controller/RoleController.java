package com.grash.controller;

import com.grash.dto.RolePatchDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.OwnUser;
import com.grash.model.Role;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.PlanFeatures;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.service.RoleService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.Optional;

/**
 * Roles.
 * <p>
 * {@link Role} is not a {@code CompanyAudit}, so nothing below the controller
 * stops a tenant from reaching another tenant's roles: every check has to be
 * made here. Two kinds of role are visible to a tenant:
 * <ul>
 *     <li>its own custom roles ({@code companySettings} is the caller's), which it may edit and delete;</li>
 *     <li>the shared default roles ({@code companySettings} is null), which every company uses and
 *     which are therefore read-only for tenants.</li>
 * </ul>
 * Everything else, including the super-admin role, is reported as not found.
 */
@RestController
@RequestMapping("/roles")
@Tag(name = "role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("permitAll()")

    public Collection<Role> getAll(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (user.getRole().getRoleType().equals(RoleType.ROLE_CLIENT)) {
            if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
                return roleService.findByCompany(user.getCompany().getId());
            } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        } else return roleService.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")

    public Role getById(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<Role> optionalRole = roleService.findById(id);
        if (optionalRole.isPresent() && isVisibleTo(optionalRole.get(), user)) {
            Role savedRole = optionalRole.get();
            if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
                return savedRole;
            } else throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    Role create(@Valid @RequestBody Role roleReq, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)
                && user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.ROLE)) {
            // Never take ownership or kind from the body: a tenant creating a
            // ROLE_SUPER_ADMIN role, or a role in another company, and then
            // assigning it to a user is a platform takeover.
            roleReq.setId(null);
            roleReq.setRoleType(RoleType.ROLE_CLIENT);
            roleReq.setCode(RoleCode.USER_CREATED);
            roleReq.setCompanySettings(user.getCompany().getCompanySettings());
            roleReq.setPaid(true);
            return roleService.create(roleReq);
        } else throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public Role patch(@Valid @RequestBody RolePatchDTO role,
                      @PathVariable("id") Long id,
                      HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Role savedRole = requireEditable(id, user);
        if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
            return roleService.update(savedRole.getId(), role);
        } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Role savedRole = requireEditable(id, user);
        if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
            roleService.delete(savedRole.getId());
            return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"),
                    HttpStatus.OK);
        } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
    }

    /**
     * A role the caller may modify: one of its own company's roles. Shared
     * default roles are visible but read-only; anything else does not exist as
     * far as the caller is concerned.
     */
    private Role requireEditable(Long id, OwnUser user) {
        Role role = roleService.findById(id)
                .filter(found -> isVisibleTo(found, user))
                .orElseThrow(() -> new CustomException("Role not found", HttpStatus.NOT_FOUND));
        if (!role.belongsOnlyToCompany(user.getCompany())) {
            throw new CustomException("Default roles are shared by every company and cannot be modified",
                    HttpStatus.FORBIDDEN);
        }
        return role;
    }

    /**
     * Whether the caller may see this role at all: its own company's client
     * roles and the shared defaults. Never another company's roles, and never
     * a super-admin role.
     */
    static boolean isVisibleTo(Role role, OwnUser user) {
        if (RoleType.ROLE_SUPER_ADMIN.equals(user.getRole().getRoleType())) {
            return true;
        }
        return RoleType.ROLE_CLIENT.equals(role.getRoleType())
                && role.belongsToCompany(user.getCompany());
    }
}
