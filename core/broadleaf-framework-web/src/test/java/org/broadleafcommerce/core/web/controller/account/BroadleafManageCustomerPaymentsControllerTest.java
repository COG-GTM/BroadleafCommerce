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
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.service.CustomerPaymentService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BroadleafManageCustomerPaymentsControllerTest {

    private static final Long ALICE_ID = 1L;
    private static final Long MALLORY_ID = 2L;
    private static final Long ALICE_PAYMENT_ID = 100L;

    private CustomerPaymentService customerPaymentService;
    private CustomerPayment alicePayment;
    private Customer activeCustomer;
    private BroadleafManageCustomerPaymentsController controller;

    @Before
    public void setUp() {
        customerPaymentService = EasyMock.createStrictMock(CustomerPaymentService.class);
        alicePayment = EasyMock.createNiceMock(CustomerPayment.class);
        EasyMock.expect(alicePayment.getId()).andReturn(ALICE_PAYMENT_ID).anyTimes();
        EasyMock.expect(alicePayment.getCustomer()).andReturn(customer(ALICE_ID)).anyTimes();
        EasyMock.replay(alicePayment);

        controller = new BroadleafManageCustomerPaymentsController() {
            @Override
            protected Customer getActiveCustomer() {
                return activeCustomer;
            }
        };
        controller.customerPaymentService = customerPaymentService;
    }

    @Test
    public void removeRejectsPaymentOwnedByAnotherCustomer() {
        activeCustomer = customer(MALLORY_ID);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID)).andReturn(alicePayment);
        EasyMock.replay(customerPaymentService);

        assertSecurityException(() -> controller.removeCustomerPayment(null, null, ALICE_PAYMENT_ID));
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void makeDefaultRejectsPaymentOwnedByAnotherCustomer() {
        activeCustomer = customer(MALLORY_ID);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID)).andReturn(alicePayment);
        EasyMock.replay(customerPaymentService);

        assertSecurityException(() -> controller.makeDefaultCustomerPayment(null, null, ALICE_PAYMENT_ID));
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeAndMakeDefaultRejectAnonymousCaller() {
        activeCustomer = null;
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID)).andReturn(alicePayment).times(2);
        EasyMock.replay(customerPaymentService);

        assertSecurityException(() -> controller.removeCustomerPayment(null, null, ALICE_PAYMENT_ID));
        assertSecurityException(() -> controller.makeDefaultCustomerPayment(null, null, ALICE_PAYMENT_ID));
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeDeletesPaymentOwnedByActiveCustomer() {
        activeCustomer = customer(ALICE_ID);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID)).andReturn(alicePayment);
        customerPaymentService.deleteCustomerPaymentById(ALICE_PAYMENT_ID);
        EasyMock.expectLastCall();
        EasyMock.replay(customerPaymentService);

        controller.removeCustomerPayment(null, null, ALICE_PAYMENT_ID);
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeIgnoresMissingPayment() {
        activeCustomer = customer(ALICE_ID);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(999L)).andReturn(null);
        EasyMock.replay(customerPaymentService);

        controller.removeCustomerPayment(null, null, 999L);
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void makeDefaultUpdatesPaymentOwnedByActiveCustomer() {
        activeCustomer = customer(ALICE_ID);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID)).andReturn(alicePayment);
        EasyMock.expect(customerPaymentService.setAsDefaultPayment(alicePayment)).andReturn(alicePayment);
        EasyMock.replay(customerPaymentService);

        controller.makeDefaultCustomerPayment(null, null, ALICE_PAYMENT_ID);
        EasyMock.verify(customerPaymentService);
    }

    private static Customer customer(Long id) {
        Customer customer = EasyMock.createNiceMock(Customer.class);
        EasyMock.expect(customer.getId()).andReturn(id).anyTimes();
        EasyMock.replay(customer);
        return customer;
    }

    private static void assertSecurityException(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
    }
}
