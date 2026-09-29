package com.grash.controller;

import com.grash.dto.CustomFieldPatchDTO;
import com.grash.model.Company;
import com.grash.model.CustomField;
import com.grash.model.OwnUser;
import com.grash.model.enums.CustomFieldEntity;
import com.grash.model.enums.RoleCode;
import com.grash.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static com.grash.controller.RoleControllerTest.assertStatus;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldControllerTest {

    @Mock
    CustomFieldService customFieldService;
    @Mock
    UserService userService;
    @Mock
    AssetService assetService;
    @Mock
    PartService partService;
    @Mock
    WorkOrderService workOrderService;
    @Mock
    LocationService locationService;
    @Mock
    VendorService vendorService;
    @Mock
    ComponentService componentService;
    @Mock
    HttpServletRequest req;
    @InjectMocks
    CustomFieldController controller;

    Company companyA;
    Company companyB;

    @BeforeEach
    void setUp() {
        companyA = SecurityFixtures.company(1);
        companyB = SecurityFixtures.company(2);
    }

    @Test
    void cannotAttachAFieldToAnotherCompanysAsset() {
        OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
        when(userService.whoami(req)).thenReturn(adminB);
        when(assetService.findByIdAndCompany(5L, 2L)).thenReturn(Optional.empty());
        CustomField body = new CustomField("Serial", "X", CustomFieldEntity.ASSET, 5L);

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.create(body, req));
        verify(customFieldService, never()).create(any());
    }

    @Test
    void anotherCompanysFieldIsNotFound() {
        OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
        when(userService.whoami(req)).thenReturn(adminB);
        CustomField field = new CustomField("Serial", "X", CustomFieldEntity.ASSET, 5L);
        field.setId(9L);
        field.setCompany(companyA);
        when(customFieldService.findById(9L)).thenReturn(Optional.of(field));

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.patch(new CustomFieldPatchDTO(), 9L, req));
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.delete(9L, req));
        verify(customFieldService, never()).delete(anyLong());
    }

    @Test
    void technicianCanEditButNotDeleteAnAssetField() {
        OwnUser technician = SecurityFixtures.user(21, companyB, SecurityFixtures.defaultRole(RoleCode.TECHNICIAN));
        when(userService.whoami(req)).thenReturn(technician);
        CustomField field = new CustomField("Serial", "X", CustomFieldEntity.ASSET, 5L);
        field.setId(9L);
        field.setCompany(companyB);
        when(customFieldService.findById(9L)).thenReturn(Optional.of(field));
        CustomFieldPatchDTO patch = new CustomFieldPatchDTO();

        controller.patch(patch, 9L, req);
        verify(customFieldService).update(9L, patch);
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.delete(9L, req));
        verify(customFieldService, never()).delete(anyLong());
    }

    @Test
    void requesterCannotEditAnAssetField() {
        OwnUser requester = SecurityFixtures.user(22, companyB, SecurityFixtures.defaultRole(RoleCode.REQUESTER));
        when(userService.whoami(req)).thenReturn(requester);
        CustomField field = new CustomField("Serial", "X", CustomFieldEntity.ASSET, 5L);
        field.setId(9L);
        field.setCompany(companyB);
        when(customFieldService.findById(9L)).thenReturn(Optional.of(field));

        assertStatus(HttpStatus.FORBIDDEN, () -> controller.patch(new CustomFieldPatchDTO(), 9L, req));
        verify(customFieldService, never()).update(anyLong(), any());
    }
}
