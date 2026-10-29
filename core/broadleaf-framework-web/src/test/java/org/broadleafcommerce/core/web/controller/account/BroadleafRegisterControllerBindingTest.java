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

import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class BroadleafRegisterControllerBindingTest {

    @Test
    public void processRegisterBindingIgnoresCustomerId() {
        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(new CustomerImpl());

        WebDataBinder binder = new WebDataBinder(form, "registrationForm");
        new BroadleafRegisterController().initRegisterCustomerFormBinder(binder);

        MutablePropertyValues params = new MutablePropertyValues();
        params.add("customer.id", "1042");
        params.add("customer.registered", "true");
        params.add("customer.emailAddress", "mallory@example.com");
        params.add("customer.firstName", "Mallory");
        params.add("redirectUrl", "/account");
        binder.bind(params);

        assertNull(form.getCustomer().getId());
        assertFalse(form.getCustomer().isRegistered());
        assertEquals("mallory@example.com", form.getCustomer().getEmailAddress());
        assertEquals("Mallory", form.getCustomer().getFirstName());
        assertEquals("/account", form.getRedirectUrl());
    }
}
