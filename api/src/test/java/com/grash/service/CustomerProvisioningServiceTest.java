package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.OwnUser;
import com.grash.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private SubscriptionPlanService subscriptionPlanService;
    @Mock
    private CurrencyService currencyService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerProvisioningService service;

    private static OwnUser user(String email) {
        OwnUser user = new OwnUser();
        user.setEmail(email);
        return user;
    }

    @Test
    void operatorsAreMatchedByEmailIgnoringCaseAndSpaces() {
        ReflectionTestUtils.setField(service, "operatorEmails", new String[]{" Ops@Shop.test ", "other@shop.test"});

        assertTrue(service.isOperator(user("ops@shop.test")));
        assertTrue(service.isOperator(user("OTHER@shop.test")));
        assertFalse(service.isOperator(user("customer@shop.test")));
    }

    @Test
    void nobodyIsAnOperatorWhenTheListIsEmpty() {
        // ${OPERATOR_EMAILS:} binds to one empty string, not an empty array.
        ReflectionTestUtils.setField(service, "operatorEmails", new String[]{""});

        assertFalse(service.isOperator(user("")));
        assertFalse(service.isOperator(user("anyone@shop.test")));
        assertFalse(service.isOperator(null));
    }

    @Test
    void anEmailAlreadyInUseIsRefusedBeforeAnythingIsCreated() {
        when(userRepository.existsByEmailIgnoreCase("taken@shop.test")).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () ->
                service.createCompanyWithAdmin("Shop", 5, null, " Taken@Shop.test ", "password1",
                        "Ana", "Diaz", null));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getHttpStatus());
        verifyNoInteractions(subscriptionService, companyService);
        verify(userRepository, never()).save(any());
    }
}
