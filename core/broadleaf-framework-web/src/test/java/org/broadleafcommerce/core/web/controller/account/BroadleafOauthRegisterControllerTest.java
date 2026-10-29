/*-
 * #%L
 * BroadleafCommerce Framework Web
 * %%
 * Copyright (C) 2009 - 2026 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.core.web.controller.account;

import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.controller.validator.RegisterCustomerValidator;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.broadleafcommerce.profile.web.core.service.login.LoginService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Registration must never be merged over an existing customer through a request-supplied {@code customer.id}.
 */
public class BroadleafOauthRegisterControllerTest {

    private static final Long EXISTING_CUSTOMER_ID = 100L;

    private final List<Customer> registeredCustomers = new ArrayList<>();
    private final List<OAuth2AuthorizedClient> savedClients = new ArrayList<>();

    private OAuth2AuthorizedClient authorizedClient;
    private TestOauthRegisterController oauthController;
    private BroadleafRegisterController registerController;

    @Before
    public void setUp() throws Exception {
        authorizedClient = new OAuth2AuthorizedClient(
                ClientRegistration.withRegistrationId("google")
                        .clientId("client")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("https://idp.example.com/authorize")
                        .tokenUri("https://idp.example.com/token")
                        .build(),
                "mallory",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(),
                        Instant.now().plusSeconds(60))
        );

        OAuth2AuthorizedClientService authorizedClientService = proxy(OAuth2AuthorizedClientService.class,
                (method, args) -> {
                    if ("saveAuthorizedClient".equals(method)) {
                        savedClients.add((OAuth2AuthorizedClient) args[0]);
                    }
                    return null;
                });
        oauthController = new TestOauthRegisterController(authorizedClientService);
        registerController = new BroadleafRegisterController();
        wire(oauthController);
        wire(registerController);
    }

    @Test
    public void binderIgnoresSubmittedCustomerId() {
        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(new CustomerImpl());
        WebDataBinder binder = new WebDataBinder(form, "registrationForm");
        oauthController.initRegistrationBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("customer.id", EXISTING_CUSTOMER_ID.toString());
        values.add("customer.registered", "false");
        values.add("customer.emailAddress", "mallory@example.com");
        values.add("customer.firstName", "Mallory");
        values.add("password", "MalloryPassword1");
        binder.bind(values);

        assertNull(form.getCustomer().getId());
        assertEquals("mallory@example.com", form.getCustomer().getEmailAddress());
        assertEquals("Mallory", form.getCustomer().getFirstName());
        assertEquals("MalloryPassword1", form.getPassword());
    }

    @Test
    public void oauthRegistrationWithoutOAuthContextRegistersNothing() throws PricingException {
        RegisterCustomerForm form = attackerForm();
        BindingResult errors = new BeanPropertyBindingResult(form, "registrationForm");

        String view = oauthController.processRegister(form, errors, null, null, null, null);

        assertEquals(oauthController.getRegisterView(), view);
        assertTrue(registeredCustomers.isEmpty());
        assertTrue(savedClients.isEmpty());
        assertEquals("oauth.registration.invalid", errors.getGlobalError().getCode());
    }

    @Test
    public void oauthRegistrationDiscardsSubmittedCustomerId() throws PricingException {
        oauthController.clientToLoad = authorizedClient;
        RegisterCustomerForm form = attackerForm();
        BindingResult errors = new BeanPropertyBindingResult(form, "registrationForm");

        String view = oauthController.processRegister(form, errors, null, null, null, null);

        assertEquals(oauthController.getRegisterSuccessView(), view);
        assertEquals(1, registeredCustomers.size());
        assertNull(registeredCustomers.get(0).getId());
        assertEquals(1, savedClients.size());
        assertSame(authorizedClient, savedClients.get(0));
    }

    @Test
    public void registrationDiscardsSubmittedCustomerId() throws PricingException, ServiceException {
        RegisterCustomerForm form = attackerForm();
        BindingResult errors = new BeanPropertyBindingResult(form, "registrationForm");

        String view = registerController.processRegister(form, errors, null, null, null);

        assertEquals(registerController.getRegisterSuccessView(), view);
        assertEquals(1, registeredCustomers.size());
        assertNull(registeredCustomers.get(0).getId());
    }

    private RegisterCustomerForm attackerForm() {
        Customer customer = new CustomerImpl();
        customer.setId(EXISTING_CUSTOMER_ID);
        customer.setFirstName("Mallory");
        customer.setLastName("Attacker");
        customer.setEmailAddress("mallory@example.com");
        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(customer);
        form.setPassword("MalloryPassword1");
        form.setPasswordConfirm("MalloryPassword1");
        return form;
    }

    private void wire(BroadleafRegisterController controller) throws Exception {
        CustomerService customerService = proxy(CustomerService.class, (method, args) -> {
            if ("registerCustomer".equals(method)) {
                registeredCustomers.add((Customer) args[0]);
                return args[0];
            }
            return null;
        });
        RegisterCustomerValidator validator = new RegisterCustomerValidator() {
            @Override
            public String getValidatePasswordExpression() {
                return "[^\\s]{6,}";
            }
        };
        Field validatorCustomerService = RegisterCustomerValidator.class.getDeclaredField("customerService");
        validatorCustomerService.setAccessible(true);
        validatorCustomerService.set(validator, customerService);

        controller.customerService = customerService;
        controller.registerCustomerValidator = validator;
        controller.loginService = proxy(LoginService.class, (method, args) -> null);
        controller.orderService = proxy(OrderService.class, (method, args) -> null);
        controller.setUseEmailForLogin(true);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return "equals".equals(method.getName()) ? p == args[0] : method.invoke(handler, args);
            }
            return handler.handle(method.getName(), args);
        });
    }

    private interface Handler {
        Object handle(String method, Object[] args);
    }

    private static class TestOauthRegisterController extends BroadleafOauthRegisterController {

        private OAuth2AuthorizedClient clientToLoad;

        TestOauthRegisterController(OAuth2AuthorizedClientService authorizedClientService) {
            super(authorizedClientService);
        }

        @Override
        protected OAuth2AuthorizedClient loadAuthorizedClient(RegisterCustomerForm registerCustomerForm) {
            return clientToLoad;
        }
    }

}
