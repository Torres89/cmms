package com.grash.controller;

import com.grash.dto.operator.CustomerCompanyDTO;
import com.grash.dto.operator.CustomerCompanyRequest;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.OwnUser;
import com.grash.model.enums.RoleType;
import com.grash.repository.UserRepository;
import com.grash.service.CompanyService;
import com.grash.service.CustomerProvisioningService;
import com.grash.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer onboarding for the people who run this service.
 * <p>
 * Everything here answers 403 unless the caller's email is in
 * {@code OPERATOR_EMAILS}. It lists companies and creates them; it never reads
 * or changes anything inside one.
 */
@RestController
@RequestMapping("/operator")
@Tag(name = "operator")
@RequiredArgsConstructor
@Slf4j
public class OperatorController {

    private final CustomerProvisioningService customerProvisioningService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/companies")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<CustomerCompanyDTO> companies(HttpServletRequest req) {
        requireOperator(req);
        return companyService.getAll().stream()
                .filter(company -> !isSystemCompany(company))
                .map(this::toDto)
                .sorted(Comparator.comparing(CustomerCompanyDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    @PostMapping("/companies")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCompanyDTO create(@Valid @RequestBody CustomerCompanyRequest request, HttpServletRequest req) {
        OwnUser operator = requireOperator(req);
        OwnUser admin = customerProvisioningService.createCompanyWithAdmin(request.getCompanyName(),
                request.getEmployeesCount(), request.getLanguage(), request.getAdminEmail(),
                request.getAdminPassword(), request.getAdminFirstName(), request.getAdminLastName(),
                request.getAdminPhone());
        log.info("Operator {} created company {} ('{}') with administrator {}", operator.getEmail(),
                admin.getCompany().getId(), admin.getCompany().getName(), admin.getEmail());
        return toDto(admin.getCompany());
    }

    private OwnUser requireOperator(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (!customerProvisioningService.isOperator(user)) {
            throw new CustomException("Operator access only", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /**
     * The installation's own super-admin account lives in a company of its own,
     * with no name. It is not a customer.
     */
    private boolean isSystemCompany(Company company) {
        return userRepository.findByCompany_Id(company.getId()).stream()
                .anyMatch(user -> user.getRole() != null
                        && user.getRole().getRoleType() == RoleType.ROLE_SUPER_ADMIN);
    }

    private CustomerCompanyDTO toDto(Company company) {
        CustomerCompanyDTO dto = new CustomerCompanyDTO();
        dto.setId(company.getId());
        dto.setName(company.getName());
        dto.setCreatedAt(company.getCreatedAt());
        dto.setDemo(company.isDemo());
        Collection<OwnUser> users = userRepository.findByCompany_Id(company.getId());
        dto.setUsersCount(users.stream().filter(OwnUser::isEnabled).count());
        users.stream().filter(OwnUser::isOwnsCompany).findFirst().ifPresent(owner -> {
            dto.setAdminEmail(owner.getEmail());
            dto.setAdminName((owner.getFirstName() + " " + owner.getLastName()).trim());
        });
        return dto;
    }
}
