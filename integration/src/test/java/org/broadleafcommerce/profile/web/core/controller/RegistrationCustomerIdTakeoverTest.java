/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.profile.web.core.controller;

import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.core.web.controller.account.BroadleafRegisterController;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.controller.RegisterCustomerController;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.broadleafcommerce.test.TestNGSiteIntegrationSetup;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A registration request carrying {@code customer.id} of an existing customer must create a new customer and leave
 * the existing one untouched.
 */
public class RegistrationCustomerIdTakeoverTest extends TestNGSiteIntegrationSetup {

    private static final String VICTIM_PASSWORD = "VictimPassword1";

    @Resource
    private RegisterCustomerController registerCustomerController;

    @Resource
    private CustomerService customerService;

    @AfterMethod
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void deprecatedRegisterCustomerControllerIgnoresSubmittedCustomerId() throws Exception {
        Customer victim = registerVictim("victim-legacy@example.com");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(registerCustomerController).build();

        mockMvc.perform(post("/registerCustomer/registerCustomer")
                .param("customer.id", String.valueOf(victim.getId()))
                .param("customer.username", "mallory-legacy")
                .param("customer.emailAddress", "mallory-legacy@example.com")
                .param("customer.firstName", "Mallory")
                .param("customer.lastName", "Attacker")
                .param("password", "MalloryPassword1")
                .param("passwordConfirm", "MalloryPassword1"));

        assertVictimUnchanged(victim, "victim-legacy@example.com");
        Customer attacker = customerService.readCustomerByUsername("mallory-legacy");
        assert attacker != null : "registration should still create a new customer";
        assert !attacker.getId().equals(victim.getId());
    }

    @Test
    public void broadleafRegisterControllerIgnoresSubmittedCustomerId() throws Exception {
        Customer victim = registerVictim("victim-site@example.com");
        TestRegisterController controller = new TestRegisterController();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(controller);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/register")
                .param("customer.id", String.valueOf(victim.getId()))
                .param("customer.registered", "false")
                .param("customer.emailAddress", "mallory-site@example.com")
                .param("customer.firstName", "Mallory")
                .param("customer.lastName", "Attacker")
                .param("password", "MalloryPassword1")
                .param("passwordConfirm", "MalloryPassword1"));

        assertVictimUnchanged(victim, "victim-site@example.com");
        Customer attacker = customerService.readCustomerByUsername("mallory-site@example.com");
        assert attacker != null : "registration should still create a new customer";
        assert !attacker.getId().equals(victim.getId());
    }

    @Test
    public void registerCustomerRejectsIdOfRegisteredCustomer() {
        Customer victim = registerVictim("victim-service@example.com");
        Customer payload = customerService.createCustomerWithNullId();
        payload.setId(victim.getId());
        payload.setUsername("mallory-service@example.com");
        payload.setEmailAddress("mallory-service@example.com");

        boolean rejected = false;
        try {
            customerService.registerCustomer(payload, "MalloryPassword1", "MalloryPassword1");
        } catch (IllegalArgumentException e) {
            rejected = true;
        }

        assert rejected : "registering over an existing registered customer must be rejected";
        assertVictimUnchanged(victim, "victim-service@example.com");
    }

    private Customer registerVictim(String email) {
        Customer customer = customerService.createCustomerWithNullId();
        customer.setUsername(email);
        customer.setEmailAddress(email);
        customer.setFirstName("Alice");
        customer.setLastName("Shopper");
        return customerService.registerCustomer(customer, VICTIM_PASSWORD, VICTIM_PASSWORD);
    }

    private void assertVictimUnchanged(Customer victim, String email) {
        Customer reloaded = customerService.readCustomerById(victim.getId());
        assert reloaded != null;
        assert email.equals(reloaded.getUsername()) : "username was overwritten: " + reloaded.getUsername();
        assert email.equals(reloaded.getEmailAddress()) : "email was overwritten: " + reloaded.getEmailAddress();
        assert "Alice".equals(reloaded.getFirstName()) : "first name was overwritten: " + reloaded.getFirstName();
        assert victim.getPassword().equals(reloaded.getPassword()) : "password was overwritten";
    }

    @RequestMapping("/register")
    public static class TestRegisterController extends BroadleafRegisterController {

        @ModelAttribute("registrationForm")
        public RegisterCustomerForm registrationForm() {
            return initCustomerRegistrationForm();
        }

        @PostMapping
        public String doRegister(
                @ModelAttribute("registrationForm") RegisterCustomerForm registerCustomerForm,
                BindingResult errors,
                HttpServletRequest request,
                HttpServletResponse response,
                Model model
        ) throws ServiceException, PricingException {
            return processRegister(registerCustomerForm, errors, request, response, model);
        }
    }

}
