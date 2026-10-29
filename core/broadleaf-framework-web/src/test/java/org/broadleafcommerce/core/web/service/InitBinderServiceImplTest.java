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

import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.core.web.checkout.model.ShippingInfoForm;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.ServletRequestDataBinder;

public class InitBinderServiceImplTest {

    @Test
    public void shippingFormIgnoresSubmittedIdentifiers() {
        ShippingInfoForm form = new ShippingInfoForm();
        ServletRequestDataBinder binder = configuredBinder(form, "shippingInfoForm");

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("address.id", "123");
        values.add("address.phonePrimary.id", "124");
        values.add("address.phoneSecondary.id", "125");
        values.add("address.phoneFax.id", "126");
        values.add("address.addressLine1", "1 Attacker Way");
        values.add("address.city", "Austin");
        values.add("personalMessage.id", "127");
        values.add("personalMessage.message", "hello");
        binder.bind(values);

        Assert.assertNull(form.getAddress().getId());
        Assert.assertNull(form.getPersonalMessage().getId());
        Assert.assertEquals("1 Attacker Way", form.getAddress().getAddressLine1());
        Assert.assertEquals("Austin", form.getAddress().getCity());
        Assert.assertEquals("hello", form.getPersonalMessage().getMessage());
        Assert.assertNull(form.getAddress().getPhonePrimary().getId());
        Assert.assertNull(form.getAddress().getPhoneSecondary().getId());
        Assert.assertNull(form.getAddress().getPhoneFax().getId());
    }

    @Test
    public void paymentFormIgnoresSubmittedAddressId() {
        PaymentInfoForm form = new PaymentInfoForm();
        ServletRequestDataBinder binder = configuredBinder(form, "paymentInfoForm");

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("address.id", "123");
        values.add("address.addressLine1", "1 Attacker Way");
        binder.bind(values);

        Assert.assertNull(form.getAddress().getId());
        Assert.assertEquals("1 Attacker Way", form.getAddress().getAddressLine1());
    }

    @Test
    public void identifierBlockIsCaseInsensitiveAndPreservesExistingDisallowedFields() {
        ShippingInfoForm form = new ShippingInfoForm();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(form, "shippingInfoForm");
        binder.setDisallowedFields("addressName");
        new InitBinderServiceImpl().configAddressInitBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("ADDRESS.ID", "123");
        values.add("addressName", "Home");
        binder.bind(values);

        Assert.assertNull(form.getAddress().getId());
        Assert.assertNull(form.getAddressName());
    }

    protected ServletRequestDataBinder configuredBinder(Object target, String name) {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(target, name);
        new InitBinderServiceImpl().configAddressInitBinder(binder);
        return binder;
    }

}
