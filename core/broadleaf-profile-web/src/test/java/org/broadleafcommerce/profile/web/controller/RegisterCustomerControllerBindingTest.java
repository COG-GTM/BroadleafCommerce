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
package org.broadleafcommerce.profile.web.controller;

import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class RegisterCustomerControllerBindingTest {

    @Test
    public void registrationBindingIgnoresCustomerIdentityAndStateFields() {
        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(new CustomerImpl());

        WebDataBinder binder = new WebDataBinder(form, "registerCustomerForm");
        new RegisterCustomerController().initRegisterCustomerFormBinder(binder);

        MutablePropertyValues params = new MutablePropertyValues();
        params.add("customer.id", "1042");
        params.add("customer.registered", "true");
        params.add("customer.deactivated", "true");
        params.add("customer.password", "attacker-hash");
        params.add("customer.externalId", "ext-1");
        params.add("customer.firstName", "Mallory");
        params.add("customer.lastName", "Attacker");
        params.add("customer.emailAddress", "mallory@example.com");
        params.add("password", "secret123");
        params.add("passwordConfirm", "secret123");
        binder.bind(params);

        Customer customer = form.getCustomer();
        assertNull(customer.getId());
        assertFalse(customer.isRegistered());
        assertFalse(customer.isDeactivated());
        assertNull(customer.getPassword());
        assertNull(customer.getExternalId());
        assertEquals("Mallory", customer.getFirstName());
        assertEquals("Attacker", customer.getLastName());
        assertEquals("mallory@example.com", customer.getEmailAddress());
        assertEquals("secret123", form.getPassword());
        assertEquals("secret123", form.getPasswordConfirm());
    }

    @Test
    public void binderRestrictionOnlyAppliesToRegistrationForm() {
        CustomerImpl other = new CustomerImpl();
        WebDataBinder binder = new WebDataBinder(other, "other");
        new RegisterCustomerController().initRegisterCustomerFormBinder(binder);

        MutablePropertyValues params = new MutablePropertyValues();
        params.add("firstName", "Alice");
        binder.bind(params);

        assertEquals("Alice", other.getFirstName());
    }
}
