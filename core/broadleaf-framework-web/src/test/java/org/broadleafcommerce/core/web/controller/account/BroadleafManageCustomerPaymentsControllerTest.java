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

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.CustomerPaymentService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.context.request.WebRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BroadleafManageCustomerPaymentsControllerTest {

    private static final Long OWN_PAYMENT_ID = 10L;
    private static final Long OTHER_PAYMENT_ID = 20L;
    private static final Long MISSING_PAYMENT_ID = 30L;

    private BroadleafManageCustomerPaymentsController controller;
    private CustomerPaymentService customerPaymentService;
    private WebRequest webRequest;
    private Customer alice;
    private Customer mallory;
    private CustomerPayment malloryPayment;
    private CustomerPayment alicePayment;

    @Before
    public void setUp() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("blRequestCustomerResolver", BroadleafRequestCustomerResolverImpl.class);
        applicationContext.refresh();
        new BroadleafRequestCustomerResolverImpl().setApplicationContext(applicationContext);

        webRequest = EasyMock.createNiceMock(WebRequest.class);
        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(context);

        alice = customer(1L);
        mallory = customer(2L);
        malloryPayment = payment(OWN_PAYMENT_ID, mallory);
        alicePayment = payment(OTHER_PAYMENT_ID, alice);

        customerPaymentService = EasyMock.createMock(CustomerPaymentService.class);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(OWN_PAYMENT_ID)).andStubReturn(malloryPayment);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(OTHER_PAYMENT_ID)).andStubReturn(alicePayment);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(MISSING_PAYMENT_ID)).andStubReturn(null);

        controller = new BroadleafManageCustomerPaymentsController();
        controller.customerPaymentService = customerPaymentService;
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void removeOwnPaymentDeletesIt() {
        signIn(mallory);
        customerPaymentService.deleteCustomerPaymentById(OWN_PAYMENT_ID);
        EasyMock.expectLastCall().once();
        EasyMock.replay(customerPaymentService);

        String view = controller.removeCustomerPayment(null, null, OWN_PAYMENT_ID);

        assertEquals(controller.getCustomerPaymentView(), view);
        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeOtherCustomersPaymentIsRejected() {
        signIn(mallory);
        EasyMock.replay(customerPaymentService);

        expectSecurityException(() -> controller.removeCustomerPayment(null, null, OTHER_PAYMENT_ID));

        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeMissingPaymentIsNoOp() {
        signIn(mallory);
        EasyMock.replay(customerPaymentService);

        controller.removeCustomerPayment(null, null, MISSING_PAYMENT_ID);

        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void removeWithoutActiveCustomerIsRejected() {
        signIn(null);
        EasyMock.replay(customerPaymentService);

        expectSecurityException(() -> controller.removeCustomerPayment(null, null, OTHER_PAYMENT_ID));

        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void makeDefaultOwnPaymentSetsDefault() {
        signIn(mallory);
        EasyMock.expect(customerPaymentService.setAsDefaultPayment(malloryPayment)).andReturn(malloryPayment).once();
        EasyMock.replay(customerPaymentService);

        controller.makeDefaultCustomerPayment(null, null, OWN_PAYMENT_ID);

        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void makeDefaultOtherCustomersPaymentIsRejected() {
        signIn(mallory);
        EasyMock.replay(customerPaymentService);

        expectSecurityException(() -> controller.makeDefaultCustomerPayment(null, null, OTHER_PAYMENT_ID));

        EasyMock.verify(customerPaymentService);
    }

    @Test
    public void makeDefaultOrphanPaymentIsRejected() {
        signIn(mallory);
        malloryPayment.setCustomer(null);
        EasyMock.replay(customerPaymentService);

        expectSecurityException(() -> controller.makeDefaultCustomerPayment(null, null, OWN_PAYMENT_ID));

        EasyMock.verify(customerPaymentService);
    }

    private void signIn(Customer customer) {
        EasyMock.expect(webRequest.getAttribute("customer", WebRequest.SCOPE_REQUEST)).andStubReturn(customer);
        EasyMock.replay(webRequest);
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private static CustomerPayment payment(Long id, Customer customer) {
        CustomerPayment payment = new CustomerPaymentImpl();
        payment.setId(id);
        payment.setCustomer(customer);
        return payment;
    }

    private static void expectSecurityException(Runnable action) {
        try {
            action.run();
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
    }

}
