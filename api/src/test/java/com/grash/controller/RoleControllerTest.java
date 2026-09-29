package com.grash.controller;

import com.grash.dto.RolePatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.OwnUser;
import com.grash.model.Role;
import com.grash.model.Subscription;
import com.grash.model.SubscriptionPlan;
import com.grash.model.enums.PlanFeatures;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.service.RoleService;
import com.grash.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    RoleService roleService;
    @Mock
    UserService userService;
    @Mock
    HttpServletRequest req;
    @InjectMocks
    RoleController controller;

    Company companyA;
    Company companyB;
    OwnUser adminB;

    @BeforeEach
    void setUp() {
        companyA = SecurityFixtures.company(1);
        companyB = SecurityFixtures.company(2);
        adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
        lenient().when(userService.whoami(req)).thenReturn(adminB);
    }

    @Test
    void anotherCompanysCustomRoleIsNotFound() {
        Role roleOfA = SecurityFixtures.customRole(50, companyA);
        when(roleService.findById(50L)).thenReturn(Optional.of(roleOfA));

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.getById(50L, req));
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.patch(new RolePatchDTO(), 50L, req));
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.delete(50L, req));
        verify(roleService, never()).update(anyLong(), any());
        verify(roleService, never()).delete(anyLong());
    }

    @Test
    void superAdminRoleIsNotFound() {
        Role superAdmin = SecurityFixtures.superAdminRole(companyA);
        superAdmin.setCompanySettings(null);
        when(roleService.findById(1L)).thenReturn(Optional.of(superAdmin));

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.getById(1L, req));
    }

    @Test
    void sharedDefaultRoleIsReadableButReadOnly() {
        Role technician = SecurityFixtures.defaultRole(RoleCode.TECHNICIAN);
        when(roleService.findById(technician.getId())).thenReturn(Optional.of(technician));

        assertSame(technician, controller.getById(technician.getId(), req));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.patch(new RolePatchDTO(), technician.getId(), req));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.delete(technician.getId(), req));
        verify(roleService, never()).update(anyLong(), any());
        verify(roleService, never()).delete(anyLong());
    }

    @Test
    void ownCustomRoleCanBeEditedAndDeleted() {
        Role roleOfB = SecurityFixtures.customRole(60, companyB);
        when(roleService.findById(60L)).thenReturn(Optional.of(roleOfB));
        RolePatchDTO patch = new RolePatchDTO();
        when(roleService.update(60L, patch)).thenReturn(roleOfB);

        assertSame(roleOfB, controller.patch(patch, 60L, req));
        controller.delete(60L, req);
        verify(roleService).delete(60L);
    }

    @Test
    void createIgnoresOwnershipAndKindFromTheBody() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setFeatures(Set.of(PlanFeatures.ROLE));
        Subscription subscription = new Subscription();
        subscription.setSubscriptionPlan(plan);
        companyB.setSubscription(subscription);

        Role body = SecurityFixtures.customRole(77, companyA);
        body.setRoleType(RoleType.ROLE_SUPER_ADMIN);
        body.setCode(RoleCode.ADMIN);
        when(roleService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.create(body, req);

        ArgumentCaptor<Role> created = ArgumentCaptor.forClass(Role.class);
        verify(roleService).create(created.capture());
        assertNull(created.getValue().getId());
        assertEquals(RoleType.ROLE_CLIENT, created.getValue().getRoleType());
        assertEquals(RoleCode.USER_CREATED, created.getValue().getCode());
        assertSame(companyB.getCompanySettings(), created.getValue().getCompanySettings());
    }

    static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable call) {
        CustomException e = assertThrows(CustomException.class, call);
        assertEquals(expected, e.getHttpStatus());
    }
}
