package com.grash.controller;

import com.grash.dto.CategoryPatchDTO;
import com.grash.mapper.CostCategoryMapper;
import com.grash.mapper.PartMapper;
import com.grash.model.Company;
import com.grash.model.CostCategory;
import com.grash.model.OwnUser;
import com.grash.model.Part;
import com.grash.model.enums.RoleCode;
import com.grash.service.CostCategoryService;
import com.grash.service.PartService;
import com.grash.service.UserService;
import com.grash.service.WorkflowService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static com.grash.controller.RoleControllerTest.assertStatus;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class CategoryAndPartControllerTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CostCategories {
        @Mock
        CostCategoryService costCategoryService;
        @Mock
        UserService userService;
        @Mock
        CostCategoryMapper costCategoryMapper;
        @Mock
        HttpServletRequest req;
        @InjectMocks
        CostCategoryController controller;

        @Test
        void administratorOnASharedDefaultRoleCanPatchOwnCategory() {
            Company companyB = SecurityFixtures.company(2);
            OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
            when(userService.whoami(req)).thenReturn(adminB);
            CostCategory category = new CostCategory();
            category.setCompanySettings(companyB.getCompanySettings());
            when(costCategoryService.findById(5L)).thenReturn(Optional.of(category));
            CategoryPatchDTO patch = new CategoryPatchDTO();

            controller.patch(patch, 5L, req);
            verify(costCategoryService).update(5L, patch);
        }

        @Test
        void anotherCompanysCategoryIsNotFound() {
            Company companyA = SecurityFixtures.company(1);
            Company companyB = SecurityFixtures.company(2);
            OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
            when(userService.whoami(req)).thenReturn(adminB);
            CostCategory category = new CostCategory();
            category.setCompanySettings(companyA.getCompanySettings());
            when(costCategoryService.findById(5L)).thenReturn(Optional.of(category));

            assertStatus(HttpStatus.NOT_FOUND, () -> controller.patch(new CategoryPatchDTO(), 5L, req));
            assertStatus(HttpStatus.NOT_FOUND, () -> controller.delete(5L, req));
            verify(costCategoryService, never()).delete(anyLong());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Parts {
        @Mock
        PartService partService;
        @Mock
        PartMapper partMapper;
        @Mock
        UserService userService;
        @Mock
        WorkflowService workflowService;
        @Mock
        EntityManager em;
        @Mock
        HttpServletRequest req;
        @InjectMocks
        PartController controller;

        @Test
        void aUserWhoseIdMatchesThePartIdCannotDeleteIt() {
            Company companyB = SecurityFixtures.company(2);
            OwnUser technician = SecurityFixtures.user(7, companyB, SecurityFixtures.defaultRole(RoleCode.TECHNICIAN));
            when(userService.whoami(req)).thenReturn(technician);
            Part part = new Part();
            part.setId(7L);
            part.setCreatedBy(99L);
            when(partService.findById(7L)).thenReturn(Optional.of(part));

            assertStatus(HttpStatus.FORBIDDEN, () -> controller.delete(7L, req));
            verify(partService, never()).delete(anyLong());
        }

        @Test
        void theCreatorCanDeleteTheirPart() {
            Company companyB = SecurityFixtures.company(2);
            OwnUser technician = SecurityFixtures.user(7, companyB, SecurityFixtures.defaultRole(RoleCode.TECHNICIAN));
            when(userService.whoami(req)).thenReturn(technician);
            Part part = new Part();
            part.setId(8L);
            part.setCreatedBy(7L);
            when(partService.findById(8L)).thenReturn(Optional.of(part));

            controller.delete(8L, req);
            verify(partService).delete(8L);
        }
    }
}
