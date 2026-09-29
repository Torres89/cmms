package com.grash.controller;

import com.grash.dto.ReadingPatchDTO;
import com.grash.model.Company;
import com.grash.model.Meter;
import com.grash.model.OwnUser;
import com.grash.model.Reading;
import com.grash.model.enums.RoleCode;
import com.grash.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static com.grash.controller.RoleControllerTest.assertStatus;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadingControllerTest {

    @Mock
    MeterService meterService;
    @Mock
    ReadingService readingService;
    @Mock
    UserService userService;
    @Mock
    WorkOrderMeterTriggerService workOrderMeterTriggerService;
    @Mock
    NotificationService notificationService;
    @Mock
    WorkOrderService workOrderService;
    @Mock
    MessageSource messageSource;
    @Mock
    HttpServletRequest req;
    @InjectMocks
    ReadingController controller;

    Company companyB;
    Reading reading;

    @BeforeEach
    void setUp() {
        companyB = SecurityFixtures.company(2);
        Meter meter = new Meter();
        meter.setId(300L);
        reading = new Reading();
        reading.setId(400L);
        reading.setMeter(meter);
        reading.setCreatedBy(1L);
        when(readingService.findById(400L)).thenReturn(Optional.of(reading));
    }

    @Test
    void anotherCompanysReadingIsNotFound() {
        OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
        when(userService.whoami(req)).thenReturn(adminB);
        when(meterService.findByIdAndCompany(300L, 2L)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.patch(new ReadingPatchDTO(), 400L, req));
        assertStatus(HttpStatus.NOT_FOUND, () -> controller.delete(400L, req));
        verify(readingService, never()).update(anyLong(), any());
        verify(readingService, never()).delete(anyLong());
    }

    @Test
    void technicianCannotChangeSomeoneElsesReading() {
        OwnUser technician = SecurityFixtures.user(21, companyB, SecurityFixtures.defaultRole(RoleCode.TECHNICIAN));
        when(userService.whoami(req)).thenReturn(technician);
        when(meterService.findByIdAndCompany(300L, 2L)).thenReturn(Optional.of(new Meter()));

        assertStatus(HttpStatus.FORBIDDEN, () -> controller.patch(new ReadingPatchDTO(), 400L, req));
        assertStatus(HttpStatus.FORBIDDEN, () -> controller.delete(400L, req));
        verify(readingService, never()).delete(anyLong());
    }

    @Test
    void adminOfTheSameCompanyCanDelete() {
        OwnUser adminB = SecurityFixtures.user(20, companyB, SecurityFixtures.defaultRole(RoleCode.ADMIN));
        when(userService.whoami(req)).thenReturn(adminB);
        when(meterService.findByIdAndCompany(300L, 2L)).thenReturn(Optional.of(new Meter()));

        controller.delete(400L, req);
        verify(readingService).delete(400L);
    }
}
