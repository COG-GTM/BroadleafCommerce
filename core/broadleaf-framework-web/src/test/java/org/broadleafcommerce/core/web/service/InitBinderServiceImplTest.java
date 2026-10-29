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
package org.broadleafcommerce.core.web.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.broadleafcommerce.core.web.controller.account.CustomerAddressForm;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.PhoneImpl;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.ServletRequestDataBinder;

public class InitBinderServiceImplTest {

    @Test
    public void addressBinderIgnoresEntityIdentifiers() {
        CustomerAddressForm form = new CustomerAddressForm();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(form, "customerAddressForm");
        new InitBinderServiceImpl().configAddressInitBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("address.id", "42");
        values.add("address.phonePrimary.id", "43");
        values.add("address.phonePrimary.phoneNumber", "555-0100");
        values.add("customerAddressId", "44");
        values.add("address.addressLine1", "1 Attacker Way");
        values.add("addressName", "Home");
        binder.bind(values);

        assertNull(form.getAddress().getId());
        assertNull(form.getAddress().getPhonePrimary().getId());
        assertNull(form.getCustomerAddressId());
        assertEquals("555-0100", form.getAddress().getPhonePrimary().getPhoneNumber());
        assertEquals("1 Attacker Way", form.getAddress().getAddressLine1());
        assertEquals("Home", form.getAddressName());
    }

    @Test
    public void addressBinderKeepsExistingDisallowedFields() {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(new CustomerAddressForm(), "customerAddressForm");
        binder.setDisallowedFields("addressName");
        new InitBinderServiceImpl().configAddressInitBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("addressName", "Home");
        values.add("address.id", "42");
        binder.bind(values);

        CustomerAddressForm form = (CustomerAddressForm) binder.getTarget();
        assertNull(form.getAddressName());
        assertNull(((AddressImpl) form.getAddress()).getId());
    }
}
