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

import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

import junit.framework.TestCase;

public class RegisterCustomerControllerBindingTest extends TestCase {

    public void testSensitiveCustomerFieldsAreNotBindable() {
        RegisterCustomerController controller = new RegisterCustomerController();

        RegisterCustomerForm form = new RegisterCustomerForm();
        form.setCustomer(new CustomerImpl());

        WebDataBinder binder = new WebDataBinder(form, "registerCustomerForm");
        controller.initBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("customer.id", "42");
        values.add("customer.registered", "true");
        values.add("customer.deactivated", "true");
        values.add("customer.password", "attackerHash");
        values.add("customer.passwordChangeRequired", "true");
        values.add("customer.emailAddress", "new.customer@example.com");
        values.add("customer.firstName", "New");
        binder.bind(values);

        assertNull(form.getCustomer().getId());
        assertFalse(form.getCustomer().isRegistered());
        assertFalse(form.getCustomer().isDeactivated());
        assertNull(form.getCustomer().getPassword());
        assertFalse(form.getCustomer().isPasswordChangeRequired());
        assertEquals("new.customer@example.com", form.getCustomer().getEmailAddress());
        assertEquals("New", form.getCustomer().getFirstName());
    }
}
