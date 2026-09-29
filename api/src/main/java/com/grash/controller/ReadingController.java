package com.grash.controller;

import com.grash.dto.ReadingPatchDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.model.*;
import com.grash.model.enums.NotificationType;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.PlanFeatures;
import com.grash.model.enums.RoleType;
import com.grash.model.enums.WorkOrderMeterTriggerCondition;
import com.grash.service.*;
import com.grash.utils.AuditComparator;
import com.grash.utils.Helper;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/readings")
@Tag(name = "reading")
@RequiredArgsConstructor
public class ReadingController {

    private final MeterService meterService;
    private final ReadingService readingService;
    private final UserService userService;
    private final WorkOrderMeterTriggerService workOrderMeterTriggerService;
    private final NotificationService notificationService;
    private final WorkOrderService workOrderService;
    private final MessageSource messageSource;


    @GetMapping("/meter/{id}")
    @PreAuthorize("permitAll()")

    public Collection<Reading> getByMeter(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<Meter> optionalMeter = user.getRole().getRoleType().equals(RoleType.ROLE_SUPER_ADMIN)
                ? meterService.findById(id)
                : meterService.findByIdAndCompany(id, user.getCompany().getId());
        if (optionalMeter.isPresent()) {
            if (user.getRole().getRoleType().equals(RoleType.ROLE_CLIENT)
                    && !user.getRole().getViewPermissions().contains(PermissionEntity.METERS)) {
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            }
            return readingService.findByMeter(id);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    Reading create(@Valid @RequestBody Reading readingReq, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (!user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.METER))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        if (readingReq.getMeter() == null || readingReq.getMeter().getId() == null)
            throw new CustomException("A reading needs a meter", HttpStatus.BAD_REQUEST);
        Optional<Meter> optionalMeter = meterService.findByIdAndCompany(readingReq.getMeter().getId(),
                user.getCompany().getId());
        if (optionalMeter.isPresent()) {
            Meter meter = optionalMeter.get();
            Collection<Reading> readings = readingService.findByMeter(readingReq.getMeter().getId());
            if (!readings.isEmpty()) {
                Reading lastReading = Collections.max(readings, new AuditComparator());
                Date nextReading = Helper.incrementDays(lastReading.getCreatedAt(), meter.getUpdateFrequency());
                if (new Date().before(nextReading)) {
                    throw new CustomException("The update frequency has not been respected", HttpStatus.NOT_ACCEPTABLE);
                }
            }
            Collection<WorkOrderMeterTrigger> meterTriggers = workOrderMeterTriggerService.findByMeter(meter.getId());
            Locale locale = Helper.getLocale(user);
            meterTriggers.forEach(meterTrigger -> {
                boolean error = false;
                StringBuilder message = new StringBuilder();
                String title = messageSource.getMessage("new_wo", null, locale);
                Object[] notificationArgs = new Object[]{meter.getName(), meterTrigger.getValue(), meter.getUnit()};
                if (meterTrigger.getTriggerCondition().equals(WorkOrderMeterTriggerCondition.LESS_THAN)) {
                    if (readingReq.getValue() < meterTrigger.getValue()) {
                        error = true;
                        message.append(messageSource.getMessage("notification_reading_less_than", notificationArgs,
                                locale));
                    }
                } else if (readingReq.getValue() > meterTrigger.getValue()) {
                    error = true;
                    message.append(messageSource.getMessage("notification_reading_more_than", notificationArgs,
                            locale));
                }
                if (error) {
                    notificationService.createMultiple(meter.getUsers().stream().map(user1 ->
                            new Notification(message.toString(), user1, NotificationType.METER, meter.getId())
                    ).collect(Collectors.toList()), true, title);
                    WorkOrder workOrder = workOrderService.getWorkOrderFromWorkOrderBase(meterTrigger);
                    workOrderService.create(workOrder, user.getCompany());
                }
            });
            return readingService.create(readingReq);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public Reading patch(@Valid @RequestBody ReadingPatchDTO reading,
                         @PathVariable("id") Long id,
                         HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Reading savedReading = requireInCompany(id, user);
        if (user.getRole().getEditOtherPermissions().contains(PermissionEntity.METERS)
                || user.getId().equals(savedReading.getCreatedBy())) {
            return readingService.update(id, reading);
        } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")

    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Reading savedReading = requireInCompany(id, user);
        if (user.getRole().getDeleteOtherPermissions().contains(PermissionEntity.METERS)
                || user.getId().equals(savedReading.getCreatedBy())) {
            readingService.delete(id);
            return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"),
                    HttpStatus.OK);
        } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
    }

    /**
     * A reading of one of the caller's company's meters. {@link Reading} is not
     * a {@code CompanyAudit}, so the tenant check has to go through its meter;
     * another company's reading is reported as not found.
     */
    private Reading requireInCompany(Long id, OwnUser user) {
        Reading reading = readingService.findById(id)
                .orElseThrow(() -> new CustomException("Reading not found", HttpStatus.NOT_FOUND));
        // getMeter().getId() reads the proxy's identifier without loading it.
        if (reading.getMeter() == null || meterService.findByIdAndCompany(reading.getMeter().getId(),
                user.getCompany().getId()).isEmpty()) {
            throw new CustomException("Reading not found", HttpStatus.NOT_FOUND);
        }
        return reading;
    }
}


