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

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.service.OrderPaymentService;
import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.core.web.checkout.validator.CheckoutPaymentInfoFormValidator;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.core.web.order.service.CartStateService;
import org.broadleafcommerce.core.web.payment.service.SavedPaymentService;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerPaymentService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Field;

import jakarta.servlet.http.HttpServletRequest;

public class BroadleafPaymentInfoControllerTest {

    private static final Long VICTIM_PAYMENT_ID = 200L;
    private static final Long OWN_PAYMENT_ID = 100L;

    private BroadleafPaymentInfoController controller;
    private CustomerPaymentService customerPaymentService;
    private SavedPaymentService savedPaymentService;
    private OrderPaymentService orderPaymentService;
    private OrderService orderService;
    private CartStateService cartStateService;
    private Order cart;
    private Customer activeCustomer;
    private CustomerPayment ownPayment;
    private CustomerPayment victimPayment;
    private ApplicationContext originalResolverContext;

    @Before
    public void setUp() throws Exception {
        activeCustomer = customer(1L);
        ownPayment = payment(OWN_PAYMENT_ID, activeCustomer, "own-token");
        victimPayment = payment(VICTIM_PAYMENT_ID, customer(2L), "victim-token");

        cart = EasyMock.createNiceMock(Order.class);
        EasyMock.expect(cart.getTotalAfterAppliedPayments()).andReturn(new Money("10.00")).anyTimes();
        EasyMock.replay(cart);

        installRequestState(activeCustomer, cart);

        customerPaymentService = EasyMock.createMock(CustomerPaymentService.class);
        savedPaymentService = EasyMock.createMock(SavedPaymentService.class);
        orderPaymentService = EasyMock.createMock(OrderPaymentService.class);
        orderService = EasyMock.createNiceMock(OrderService.class);
        cartStateService = EasyMock.createNiceMock(CartStateService.class);

        controller = new BroadleafPaymentInfoController();
        controller.customerPaymentService = customerPaymentService;
        controller.savedPaymentService = savedPaymentService;
        controller.orderPaymentService = orderPaymentService;
        controller.orderService = orderService;
        controller.cartStateService = cartStateService;
        controller.addressService = EasyMock.createNiceMock(AddressService.class);
        controller.paymentInfoFormValidator = EasyMock.createNiceMock(CheckoutPaymentInfoFormValidator.class);
        EasyMock.replay(controller.addressService, controller.paymentInfoFormValidator);
    }

    @After
    public void tearDown() throws Exception {
        BroadleafRequestContext.setBroadleafRequestContext(null);
        resolverContextField().set(null, originalResolverContext);
    }

    @Test
    public void rejectsAnotherCustomersSavedPaymentAtCheckout() throws Exception {
        expectOwnedLookup(VICTIM_PAYMENT_ID, null);
        EasyMock.replay(customerPaymentService, savedPaymentService, orderPaymentService, orderService, cartStateService);

        PaymentInfoForm form = useSavedPaymentForm(VICTIM_PAYMENT_ID);
        try {
            controller.savePaymentInfo(request(), null, new ExtendedModelMap(), form, bindingResult(form));
            Assert.fail("Expected SecurityException for a saved payment owned by another customer");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(customerPaymentService, savedPaymentService, orderPaymentService);
    }

    @Test
    public void rejectsUpdatingAnotherCustomersSavedPayment() throws Exception {
        expectOwnedLookup(VICTIM_PAYMENT_ID, null);
        EasyMock.replay(customerPaymentService, savedPaymentService, orderPaymentService, orderService, cartStateService);

        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldSaveNewPayment(true);
        form.setShouldUseCustomerPayment(false);
        form.setCustomerPaymentId(VICTIM_PAYMENT_ID);
        try {
            controller.savePaymentInfo(request(), null, new ExtendedModelMap(), form, bindingResult(form));
            Assert.fail("Expected SecurityException for updating a saved payment owned by another customer");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(customerPaymentService, savedPaymentService, orderPaymentService);
    }

    @Test
    public void appliesActiveCustomersOwnSavedPayment() throws Exception {
        expectOwnedLookup(OWN_PAYMENT_ID, ownPayment);
        EasyMock.expect(orderPaymentService.createOrderPaymentFromCustomerPayment(
                EasyMock.same(cart), EasyMock.same(ownPayment), EasyMock.anyObject(Money.class)
        )).andReturn(null);
        EasyMock.replay(customerPaymentService, savedPaymentService, orderPaymentService, orderService, cartStateService);

        PaymentInfoForm form = useSavedPaymentForm(OWN_PAYMENT_ID);
        String view = controller.savePaymentInfo(request(), null, new ExtendedModelMap(), form, bindingResult(form));

        Assert.assertEquals(controller.getCheckoutPageRedirect(), view);
        EasyMock.verify(customerPaymentService, savedPaymentService, orderPaymentService);
    }

    @Test
    public void ownershipLookupUsesActiveCustomer() {
        EasyMock.expect(customerPaymentService.readCustomerPaymentByIdAndCustomer(
                EasyMock.eq(VICTIM_PAYMENT_ID), EasyMock.same(activeCustomer)
        )).andReturn(null);
        EasyMock.replay(customerPaymentService);

        try {
            controller.readActiveCustomerPayment(VICTIM_PAYMENT_ID);
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }
        EasyMock.verify(customerPaymentService);
    }

    private void expectOwnedLookup(Long customerPaymentId, CustomerPayment result) {
        EasyMock.expect(customerPaymentService.readCustomerPaymentByIdAndCustomer(
                EasyMock.eq(customerPaymentId), EasyMock.same(activeCustomer)
        )).andReturn(result).atLeastOnce();
    }

    private void installRequestState(Customer customer, Order order) throws Exception {
        Field field = resolverContextField();
        originalResolverContext = (ApplicationContext) field.get(null);

        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("blRequestCustomerResolver", BroadleafRequestCustomerResolverImpl.class);
        context.refresh();
        new BroadleafRequestCustomerResolverImpl().setApplicationContext(context);

        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute("customer", WebRequest.SCOPE_REQUEST)).andReturn(customer).anyTimes();
        EasyMock.expect(webRequest.getAttribute(
                CartStateRequestProcessor.getCartRequestAttributeName(), WebRequest.SCOPE_REQUEST
        )).andReturn(order).anyTimes();
        EasyMock.replay(webRequest);

        BroadleafRequestContext requestContext = new BroadleafRequestContext();
        requestContext.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(requestContext);
    }

    private static Field resolverContextField() throws NoSuchFieldException {
        Field field = BroadleafRequestCustomerResolverImpl.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        return field;
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.replay(request);
        return request;
    }

    private static PaymentInfoForm useSavedPaymentForm(Long customerPaymentId) {
        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldSaveNewPayment(false);
        form.setShouldUseCustomerPayment(true);
        form.setCustomerPaymentId(customerPaymentId);
        return form;
    }

    private static BindingResult bindingResult(PaymentInfoForm form) {
        return new BeanPropertyBindingResult(form, "paymentInfoForm");
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private static CustomerPayment payment(Long id, Customer customer, String token) {
        CustomerPayment payment = new CustomerPaymentImpl();
        payment.setId(id);
        payment.setCustomer(customer);
        payment.setPaymentToken(token);
        return payment;
    }

}
