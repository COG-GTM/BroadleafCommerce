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
package org.broadleafcommerce.core.web.controller.checkout;

import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.bindRequestState;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.clearRequestState;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.customer;
import static org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.customerPayment;

import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderImpl;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.domain.OrderPaymentImpl;
import org.broadleafcommerce.core.payment.service.OrderPaymentService;
import org.broadleafcommerce.core.web.checkout.model.BillingInfoForm;
import org.broadleafcommerce.core.web.checkout.validator.BillingInfoFormValidator;
import org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.InMemoryCustomerPaymentService;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import jakarta.servlet.http.HttpServletRequest;

public class BroadleafBillingInfoControllerTest {

    protected Customer alice = customer(1L);
    protected Customer mallory = customer(2L);
    protected Address aliceAddress = new AddressImpl();
    protected CustomerPayment alicePayment = customerPayment(100L, alice);
    protected Order cart = new OrderImpl();
    protected BroadleafBillingInfoController controller = new BroadleafBillingInfoController();

    @Before
    public void setUp() {
        aliceAddress.setAddressLine1("1 Alice Street");
        alicePayment.setBillingAddress(aliceAddress);
        controller.customerPaymentService = new InMemoryCustomerPaymentService(alicePayment);
        controller.billingInfoFormValidator = EasyMock.createNiceMock(BillingInfoFormValidator.class);
        controller.orderService = EasyMock.createNiceMock(OrderService.class);
        controller.orderPaymentService = EasyMock.createNiceMock(OrderPaymentService.class);
        EasyMock.expect(controller.orderPaymentService.create()).andReturn(new OrderPaymentImpl()).anyTimes();
        EasyMock.replay(controller.billingInfoFormValidator, controller.orderService, controller.orderPaymentService);
    }

    @After
    public void tearDown() {
        clearRequestState();
    }

    @Test
    public void rejectsSavedPaymentOwnedByAnotherCustomer() throws Exception {
        bindRequestState(mallory, cart);
        controller.addressService = EasyMock.createMock(AddressService.class);
        EasyMock.replay(controller.addressService);

        BillingInfoForm form = savedPaymentForm(alicePayment.getId());
        try {
            controller.saveBillingAddress(request(), null, new ExtendedModelMap(), form, result(form));
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        Assert.assertNotSame(aliceAddress, form.getAddress());
        Assert.assertNull(form.getAddress().getAddressLine1());
        Assert.assertTrue(cart.getPayments().isEmpty());
        EasyMock.verify(controller.addressService);
    }

    @Test
    public void rejectsSavedPaymentWhenNoActiveCustomer() throws Exception {
        bindRequestState(null, cart);
        controller.addressService = EasyMock.createMock(AddressService.class);
        EasyMock.replay(controller.addressService);

        BillingInfoForm form = savedPaymentForm(alicePayment.getId());
        try {
            controller.saveBillingAddress(request(), null, new ExtendedModelMap(), form, result(form));
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        Assert.assertTrue(cart.getPayments().isEmpty());
    }

    @Test
    public void copiesBillingAddressFromOwnSavedPayment() throws Exception {
        bindRequestState(alice, cart);
        Address copy = new AddressImpl();
        controller.addressService = EasyMock.createNiceMock(AddressService.class);
        EasyMock.expect(controller.addressService.copyAddress(aliceAddress)).andReturn(copy);
        EasyMock.replay(controller.addressService);

        BillingInfoForm form = savedPaymentForm(alicePayment.getId());
        String view = controller.saveBillingAddress(request(), null, new ExtendedModelMap(), form, result(form));

        Assert.assertEquals(controller.getCheckoutPageRedirect(), view);
        Assert.assertEquals(1, cart.getPayments().size());
        Assert.assertSame(copy, cart.getPayments().get(0).getBillingAddress());
        EasyMock.verify(controller.addressService);
    }

    protected BillingInfoForm savedPaymentForm(Long customerPaymentId) {
        BillingInfoForm form = new BillingInfoForm();
        form.setUseCustomerPayment(true);
        form.setCustomerPaymentId(customerPaymentId);
        return form;
    }

    protected BeanPropertyBindingResult result(BillingInfoForm form) {
        return new BeanPropertyBindingResult(form, "billingInfoForm");
    }

    protected HttpServletRequest request() {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.replay(request);
        return request;
    }

}
