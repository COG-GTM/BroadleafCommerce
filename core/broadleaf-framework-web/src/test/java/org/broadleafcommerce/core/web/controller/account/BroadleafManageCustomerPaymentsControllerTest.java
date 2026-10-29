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

import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.bindRequestState;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.clearRequestState;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.customer;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.customerPayment;

import org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.InMemoryCustomerPaymentService;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BroadleafManageCustomerPaymentsControllerTest {

    protected Customer alice = customer(1L);
    protected Customer mallory = customer(2L);
    protected CustomerPayment alicePayment = customerPayment(100L, alice);
    protected InMemoryCustomerPaymentService customerPaymentService = new InMemoryCustomerPaymentService(alicePayment);
    protected BroadleafManageCustomerPaymentsController controller = new BroadleafManageCustomerPaymentsController();

    @Before
    public void setUp() {
        controller.customerPaymentService = customerPaymentService;
    }

    @After
    public void tearDown() {
        clearRequestState();
    }

    @Test
    public void cannotRemoveAnotherCustomersSavedPayment() {
        bindRequestState(mallory, null);
        try {
            controller.removeCustomerPayment(null, null, alicePayment.getId());
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertNull(customerPaymentService.deletedId);
    }

    @Test
    public void cannotDefaultAnotherCustomersSavedPayment() {
        bindRequestState(mallory, null);
        try {
            controller.makeDefaultCustomerPayment(null, null, alicePayment.getId());
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
        Assert.assertNull(customerPaymentService.defaultPayment);
    }

    @Test
    public void ownerCanRemoveAndDefaultSavedPayment() {
        bindRequestState(alice, null);
        controller.makeDefaultCustomerPayment(null, null, alicePayment.getId());
        controller.removeCustomerPayment(null, null, alicePayment.getId());
        Assert.assertSame(alicePayment, customerPaymentService.defaultPayment);
        Assert.assertEquals(alicePayment.getId(), customerPaymentService.deletedId);
    }

}
