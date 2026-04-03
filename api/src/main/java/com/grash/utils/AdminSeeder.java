package com.grash.utils;

import com.grash.model.Company;
import com.grash.model.OwnUser;
import com.grash.model.Subscription;
import com.grash.repository.UserRepository;
import com.grash.service.CompanyService;
import com.grash.service.CurrencyService;
import com.grash.service.SubscriptionPlanService;
import com.grash.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final CurrencyService currencyService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${admin.company-name:Default Company}")
    private String adminCompanyName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isEmpty() || adminPassword == null || adminPassword.isEmpty()) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            System.out.println("Admin account already exists for " + adminEmail + ", skipping seed.");
            return;
        }
        try {
            Subscription subscription = Subscription.builder()
                    .usersCount(100)
                    .monthly(false)
                    .startsOn(new Date())
                    .endsOn(null)
                    .subscriptionPlan(subscriptionPlanService.findByCode("BUSINESS").orElseThrow())
                    .build();
            subscriptionService.create(subscription);

            Company company = new Company(adminCompanyName, 5, subscription);
            company.getCompanySettings().getGeneralPreferences().setCurrency(
                    currencyService.findByCode("$").orElseThrow());
            Company savedCompany = companyService.create(company);

            OwnUser admin = new OwnUser();
            admin.setEmail(adminEmail.toLowerCase());
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setFirstName("Admin");
            admin.setLastName("User");
            admin.setPhone("0000000000");
            admin.setUsername(new Utils().generateStringId());
            admin.setOwnsCompany(true);
            admin.setCompany(savedCompany);
            admin.setEnabled(true);
            admin.setRole(savedCompany.getCompanySettings().getRoleList().stream()
                    .filter(role -> role.getName().equals("Administrator"))
                    .findFirst()
                    .orElseThrow());

            userRepository.save(admin);
            System.out.println("Admin account seeded for " + adminEmail);
        } catch (Exception e) {
            System.err.println("Failed to seed admin account: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
