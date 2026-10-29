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
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.service.OrderPaymentService;
import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.core.web.checkout.validator.CheckoutPaymentInfoFormValidator;
import org.broadleafcommerce.core.web.controller.CustomerOwnedPaymentTestUtils.InMemoryCustomerPaymentService;
import org.broadleafcommerce.core.web.order.service.CartStateService;
import org.broadleafcommerce.core.web.payment.service.SavedPaymentService;
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

public class BroadleafPaymentInfoControllerTest {

    protected Customer alice = customer(1L);
    protected Customer mallory = customer(2L);
    protected CustomerPayment alicePayment = customerPayment(100L, alice);
    protected Order cart;
    protected BroadleafPaymentInfoController controller = new BroadleafPaymentInfoController();

    @Before
    public void setUp() {
        alicePayment.setBillingAddress(new AddressImpl());
        cart = EasyMock.createNiceMock(Order.class);
        controller.customerPaymentService = new InMemoryCustomerPaymentService(alicePayment);
        controller.paymentInfoFormValidator = EasyMock.createNiceMock(CheckoutPaymentInfoFormValidator.class);
        controller.addressService = EasyMock.createNiceMock(AddressService.class);
        controller.cartStateService = EasyMock.createNiceMock(CartStateService.class);
        controller.orderService = EasyMock.createNiceMock(OrderService.class);
        EasyMock.replay(cart, controller.paymentInfoFormValidator, controller.addressService,
                controller.cartStateService, controller.orderService);
    }

    @After
    public void tearDown() {
        clearRequestState();
    }

    @Test
    public void doesNotCreateOrderPaymentFromAnotherCustomersSavedPayment() throws Exception {
        bindRequestState(mallory, cart);
        controller.orderPaymentService = EasyMock.createMock(OrderPaymentService.class);
        controller.savedPaymentService = EasyMock.createMock(SavedPaymentService.class);
        EasyMock.replay(controller.orderPaymentService, controller.savedPaymentService);

        PaymentInfoForm form = form(alicePayment.getId(), true, false);
        assertRejected(form);

        EasyMock.verify(controller.orderPaymentService, controller.savedPaymentService);
    }

    @Test
    public void doesNotUpdateAnotherCustomersSavedPayment() throws Exception {
        bindRequestState(mallory, cart);
        controller.orderPaymentService = EasyMock.createMock(OrderPaymentService.class);
        controller.savedPaymentService = EasyMock.createMock(SavedPaymentService.class);
        EasyMock.replay(controller.orderPaymentService, controller.savedPaymentService);

        PaymentInfoForm form = form(alicePayment.getId(), false, true);
        assertRejected(form);

        EasyMock.verify(controller.orderPaymentService, controller.savedPaymentService);
    }

    @Test
    public void createsOrderPaymentFromOwnSavedPayment() throws Exception {
        bindRequestState(alice, cart);
        controller.orderPaymentService = EasyMock.createMock(OrderPaymentService.class);
        EasyMock.expect(controller.orderPaymentService.createOrderPaymentFromCustomerPayment(
                EasyMock.same(cart), EasyMock.same(alicePayment), EasyMock.anyObject()
        )).andReturn(null);
        EasyMock.replay(controller.orderPaymentService);

        PaymentInfoForm form = form(alicePayment.getId(), true, false);
        String view = controller.savePaymentInfo(request(), null, new ExtendedModelMap(), form, result(form));

        Assert.assertEquals(controller.getCheckoutPageRedirect(), view);
        EasyMock.verify(controller.orderPaymentService);
    }

    protected void assertRejected(PaymentInfoForm form) throws Exception {
        try {
            controller.savePaymentInfo(request(), null, new ExtendedModelMap(), form, result(form));
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
    }

    protected PaymentInfoForm form(Long customerPaymentId, boolean useCustomerPayment, boolean saveNewPayment) {
        PaymentInfoForm form = new PaymentInfoForm();
        form.setCustomerPaymentId(customerPaymentId);
        form.setShouldUseCustomerPayment(useCustomerPayment);
        form.setShouldSaveNewPayment(saveNewPayment);
        return form;
    }

    protected BeanPropertyBindingResult result(PaymentInfoForm form) {
        return new BeanPropertyBindingResult(form, "paymentInfoForm");
    }

    protected HttpServletRequest request() {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.replay(request);
        return request;
    }

}
