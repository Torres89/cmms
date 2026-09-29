package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.OwnUser;
import com.grash.model.Subscription;
import com.grash.model.enums.Language;
import com.grash.repository.UserRepository;
import com.grash.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;

/**
 * Creating a customer company, the one thing public signup used to be for.
 * <p>
 * The hosted service has no self-serve signup: customers are commissioned in
 * person. With public registration disabled the only way a company came into
 * being was the one-time {@code ADMIN_EMAIL} bootstrap, so a second customer
 * on the same box was impossible. This is the single path both now use.
 * <p>
 * Operators are named by email in {@code OPERATOR_EMAILS}, the same way
 * {@code ALLOWED_ORGANIZATION_ADMINS} names who may register. Being an operator
 * grants nothing inside any customer's data - only the right to create and
 * list companies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerProvisioningService {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final CurrencyService currencyService;
    private final PasswordEncoder passwordEncoder;

    @Value("${operator.emails:}")
    private String[] operatorEmails;

    public boolean isOperator(OwnUser user) {
        if (user == null || user.getEmail() == null || operatorEmails == null) {
            return false;
        }
        return Arrays.stream(operatorEmails)
                .map(String::trim)
                .anyMatch(email -> !email.isEmpty() && email.equalsIgnoreCase(user.getEmail()));
    }

    /**
     * A new company on the BUSINESS plan with no end date, and its first
     * administrator, who owns it and can sign in immediately.
     */
    @Transactional
    public OwnUser createCompanyWithAdmin(String companyName, int employeesCount, Language language,
                                          String email, String rawPassword,
                                          String firstName, String lastName, String phone) {
        String normalisedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new CustomException("Email is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Subscription subscription = Subscription.builder()
                .usersCount(100)
                .monthly(false)
                .startsOn(new Date())
                .endsOn(null)
                .subscriptionPlan(subscriptionPlanService.findByCode("BUSINESS").orElseThrow())
                .build();
        subscriptionService.create(subscription);

        Company company = new Company(companyName.trim(), employeesCount, subscription);
        company.getCompanySettings().getGeneralPreferences().setCurrency(
                currencyService.findByCode("$").orElseThrow());
        if (language != null) {
            company.getCompanySettings().getGeneralPreferences().setLanguage(language);
        }
        Company savedCompany = companyService.create(company);

        OwnUser admin = new OwnUser();
        admin.setEmail(normalisedEmail);
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setPhone(phone == null || phone.isBlank() ? "0000000000" : phone);
        admin.setUsername(new Utils().generateStringId());
        admin.setOwnsCompany(true);
        admin.setCompany(savedCompany);
        admin.setEnabled(true);
        admin.setRole(savedCompany.getCompanySettings().getRoleList().stream()
                .filter(role -> role.getName().equals("Administrator"))
                .findFirst()
                .orElseThrow());
        return userRepository.save(admin);
    }
}
