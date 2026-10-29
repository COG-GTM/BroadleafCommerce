/*-
 * #%L
 * BroadleafCommerce Profile Web
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
package org.broadleafcommerce.profile.web.controller.validator;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.broadleafcommerce.profile.web.core.security.CustomerStateRequestProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegisterCustomerValidatorTest {

    private final Map<Long, Customer> customersById = new HashMap<>();
    private RegisterCustomerValidator validator;
    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        CustomerService customerService = (CustomerService) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {CustomerService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return "toString".equals(method.getName()) ? "stub" : method.invoke(this, args);
                    }
                    if ("readCustomerById".equals(method.getName())) {
                        return customersById.get((Long) args[0]);
                    }
                    if ("readCustomerByUsername".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        validator = new RegisterCustomerValidator() {
            @Override
            public String getValidatePasswordExpression() {
                return "[0-9A-Za-z]{4,15}";
            }
        };
        ReflectionTestUtils.setField(validator, "customerService", customerService);

        request = new MockHttpServletRequest();
        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(new ServletWebRequest(request));
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void newCustomerWithoutIdIsAccepted() {
        Errors errors = validate(form(null));
        assertFalse(errors.hasErrors());
    }

    @Test
    public void existingRegisteredCustomerIdIsRejected() {
        customersById.put(1042L, persisted(1042L, true));

        Errors errors = validate(form(1042L));

        assertTrue(errors.hasGlobalErrors());
        assertEquals("customer.id.invalid", errors.getGlobalError().getCode());
    }

    @Test
    public void anotherSessionsAnonymousCustomerIdIsRejected() {
        customersById.put(2001L, persisted(2001L, false));
        request.getSession().setAttribute(
                CustomerStateRequestProcessor.getAnonymousCustomerIdSessionAttributeName(), 3001L
        );

        Errors errors = validate(form(2001L));

        assertTrue(errors.hasGlobalErrors());
        assertEquals("customer.id.invalid", errors.getGlobalError().getCode());
    }

    @Test
    public void ownPersistedAnonymousCustomerIsAccepted() {
        customersById.put(3001L, persisted(3001L, false));
        request.getSession().setAttribute(
                CustomerStateRequestProcessor.getAnonymousCustomerIdSessionAttributeName(), 3001L
        );

        Errors errors = validate(form(3001L));

        assertFalse(errors.hasErrors());
    }

    @Test
    public void ownSessionIdCannotReRegisterARegisteredCustomer() {
        customersById.put(3001L, persisted(3001L, true));
        request.getSession().setAttribute(
                CustomerStateRequestProcessor.getAnonymousCustomerIdSessionAttributeName(), 3001L
        );

        Errors errors = validate(form(3001L));

        assertTrue(errors.hasGlobalErrors());
    }

    private Errors validate(RegisterCustomerForm form) {
        Errors errors = new BeanPropertyBindingResult(form, "registerCustomerForm");
        validator.validate(form, errors, true);
        return errors;
    }

    private RegisterCustomerForm form(Long customerId) {
        Customer customer = new CustomerImpl();
        customer.setId(customerId);
        customer.setFirstName("Mallory");
        customer.setLastName("Attacker");
        customer.setEmailAddress("mallory@example.com");
        customer.setUsername("mallory@example.com");
        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(customer);
        form.setPassword("secret123");
        form.setPasswordConfirm("secret123");
        return form;
    }

    private Customer persisted(Long id, boolean registered) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        customer.setUsername("victim" + id);
        customer.setRegistered(registered);
        return customer;
    }
}
