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

import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.broadleafcommerce.profile.web.core.security.CustomerStateRequestProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;

public class BroadleafRegisterControllerTest {

    private static final Long VICTIM_ID = 100L;

    private BroadleafRegisterController controller;

    @Before
    public void setUp() {
        // No services are wired: any attempt to validate, register or log in fails the test with an NPE
        controller = new BroadleafRegisterController();
        controller.setUseEmailForLogin(true);
    }

    @Test
    public void binderIgnoresCustomerIdFromRequest() {
        RegisterCustomerForm form = newForm(null);
        WebDataBinder binder = new WebDataBinder(form, "registrationForm");
        controller.initRegistrationBinder(binder);

        MutablePropertyValues params = new MutablePropertyValues();
        params.add("customer.id", "100");
        params.add("customer.registered", "true");
        params.add("customer.emailAddress", "mallory@example.com");
        params.add("customer.firstName", "Mallory");
        params.add("password", "Secret123!");
        params.add("passwordConfirm", "Secret123!");
        binder.bind(params);

        Assert.assertNull(form.getCustomer().getId());
        Assert.assertFalse(form.getCustomer().isRegistered());
        Assert.assertEquals("mallory@example.com", form.getCustomer().getEmailAddress());
        Assert.assertEquals("Mallory", form.getCustomer().getFirstName());
        Assert.assertEquals("Secret123!", form.getPassword());
        Assert.assertTrue(binder.getBindingResult().getSuppressedFields().length > 0);
    }

    @Test
    public void processRegisterRejectsForeignCustomerId() throws Exception {
        RegisterCustomerForm form = newForm(VICTIM_ID);
        BindingResult errors = new BeanPropertyBindingResult(form, "registrationForm");

        String view = controller.processRegister(form, errors, new MockHttpServletRequest(),
                new MockHttpServletResponse(), new ExtendedModelMap());

        Assert.assertEquals(controller.getRegisterView(), view);
        Assert.assertEquals("registration.invalidCustomer", errors.getGlobalError().getCode());
    }

    @Test
    public void processRegisterRejectsIdThatDoesNotMatchSessionAnonymousCustomer() throws Exception {
        RegisterCustomerForm form = newForm(VICTIM_ID);
        BindingResult errors = new BeanPropertyBindingResult(form, "registrationForm");
        MockHttpServletRequest request = requestWithAnonymousCustomerId(7L);

        String view = controller.processRegister(form, errors, request, new MockHttpServletResponse(),
                new ExtendedModelMap());

        Assert.assertEquals(controller.getRegisterView(), view);
        Assert.assertTrue(errors.hasGlobalErrors());
    }

    @Test
    public void newOrSessionAnonymousCustomerIsRegistrable() {
        Assert.assertTrue(controller.isRegistrableCustomer(newCustomer(null), new MockHttpServletRequest()));
        Assert.assertTrue(controller.isRegistrableCustomer(newCustomer(7L), requestWithAnonymousCustomerId(7L)));
        Assert.assertFalse(controller.isRegistrableCustomer(newCustomer(VICTIM_ID), new MockHttpServletRequest()));
        Assert.assertFalse(controller.isRegistrableCustomer(newCustomer(VICTIM_ID), requestWithAnonymousCustomerId(7L)));
        Assert.assertFalse(controller.isRegistrableCustomer(null, new MockHttpServletRequest()));
    }

    private static MockHttpServletRequest requestWithAnonymousCustomerId(Long id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(
                CustomerStateRequestProcessor.getAnonymousCustomerIdSessionAttributeName(), id);
        return request;
    }

    private static RegisterCustomerForm newForm(Long customerId) {
        RegisterCustomerForm form = new RegisterCustomerForm();
        Customer customer = newCustomer(customerId);
        customer.setEmailAddress("mallory@example.com");
        form.setCustomer(customer);
        form.setPassword("Secret123!");
        form.setPasswordConfirm("Secret123!");
        return form;
    }

    private static Customer newCustomer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

}
