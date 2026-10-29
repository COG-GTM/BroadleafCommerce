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

import static org.junit.Assert.fail;

import org.broadleafcommerce.core.web.controller.support.RequestStateTestSupport;
import org.broadleafcommerce.profile.core.dao.CustomerPaymentDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.CustomerPaymentServiceImpl;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class BroadleafManageCustomerPaymentsControllerTest {

    private BroadleafManageCustomerPaymentsController controller;
    private CustomerPaymentDao customerPaymentDao;
    private Customer alice;
    private Customer mallory;
    private CustomerPayment alicesCard;

    @Before
    public void setUp() {
        alice = new CustomerImpl();
        alice.setId(1L);
        mallory = new CustomerImpl();
        mallory.setId(2L);

        alicesCard = new CustomerPaymentImpl();
        alicesCard.setId(100L);
        alicesCard.setCustomer(alice);

        customerPaymentDao = EasyMock.createMock(CustomerPaymentDao.class);
        EasyMock.expect(customerPaymentDao.readCustomerPaymentById(100L)).andStubReturn(alicesCard);
        EasyMock.expect(customerPaymentDao.readCustomerPaymentById(999L)).andStubReturn(null);

        controller = new BroadleafManageCustomerPaymentsController();
        controller.customerPaymentService = new CustomerPaymentServiceImpl() {
            {
                customerPaymentDao = BroadleafManageCustomerPaymentsControllerTest.this.customerPaymentDao;
            }
        };
    }

    @After
    public void tearDown() {
        RequestStateTestSupport.clear();
    }

    @Test
    public void removeRejectsAnotherCustomersSavedPayment() {
        RequestStateTestSupport.bind(mallory, null);
        EasyMock.replay(customerPaymentDao);

        try {
            controller.removeCustomerPayment(null, null, 100L);
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(customerPaymentDao);
    }

    @Test
    public void removeDeletesOwnSavedPayment() {
        customerPaymentDao.deleteCustomerPaymentById(100L);
        RequestStateTestSupport.bind(alice, null);
        EasyMock.replay(customerPaymentDao);

        controller.removeCustomerPayment(null, null, 100L);

        EasyMock.verify(customerPaymentDao);
    }

    @Test
    public void removeIgnoresUnknownSavedPayment() {
        RequestStateTestSupport.bind(alice, null);
        EasyMock.replay(customerPaymentDao);

        controller.removeCustomerPayment(null, null, 999L);

        EasyMock.verify(customerPaymentDao);
    }

    @Test
    public void makeDefaultRejectsAnotherCustomersSavedPayment() {
        RequestStateTestSupport.bind(mallory, null);
        EasyMock.replay(customerPaymentDao);

        try {
            controller.makeDefaultCustomerPayment(null, null, 100L);
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(customerPaymentDao);
    }

    @Test
    public void makeDefaultUpdatesOwnSavedPayment() {
        EasyMock.expect(customerPaymentDao.readCustomerPaymentsByCustomerId(1L))
                .andReturn(Collections.<CustomerPayment>emptyList());
        EasyMock.expect(customerPaymentDao.save(alicesCard)).andReturn(alicesCard);
        RequestStateTestSupport.bind(alice, null);
        EasyMock.replay(customerPaymentDao);

        controller.makeDefaultCustomerPayment(null, null, 100L);

        EasyMock.verify(customerPaymentDao);
    }

}
