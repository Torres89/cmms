package com.grash.utils;

import com.grash.repository.UserRepository;
import com.grash.service.CustomerProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CustomerProvisioningService customerProvisioningService;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${admin.company-name:Default Company}")
    private String adminCompanyName;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isEmpty() || adminPassword == null || adminPassword.isEmpty()) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            System.out.println("Admin account already exists for " + adminEmail + ", skipping seed.");
            return;
        }
        try {
            customerProvisioningService.createCompanyWithAdmin(adminCompanyName, 5, null,
                    adminEmail, adminPassword, "Admin", "User", null);
            System.out.println("Admin account seeded for " + adminEmail);
        } catch (Exception e) {
            System.err.println("Failed to seed admin account: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
