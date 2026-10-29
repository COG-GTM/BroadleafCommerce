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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.domain.OrderPayment;
import org.broadleafcommerce.core.payment.service.OrderPaymentService;
import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.core.web.checkout.validator.CheckoutPaymentInfoFormValidator;
import org.broadleafcommerce.core.web.controller.support.RequestStateTestSupport;
import org.broadleafcommerce.core.web.order.service.CartStateService;
import org.broadleafcommerce.core.web.payment.service.SavedPaymentService;
import org.broadleafcommerce.profile.core.dao.CustomerPaymentDao;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerPaymentServiceImpl;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import jakarta.servlet.http.HttpServletRequest;

public class BroadleafPaymentInfoControllerTest {

    private static final Long ALICES_CARD_ID = 100L;

    private BroadleafPaymentInfoController controller;
    private CustomerPaymentDao customerPaymentDao;
    private OrderService orderService;
    private OrderPaymentService orderPaymentService;
    private SavedPaymentService savedPaymentService;
    private CartStateService cartStateService;
    private AddressService addressService;
    private HttpServletRequest request;

    private Customer alice;
    private Customer mallory;
    private Address alicesBillingAddress;
    private CustomerPayment alicesCard;
    private Order cart;

    @Before
    public void setUp() {
        alice = customer(1L, "alice");
        mallory = customer(2L, "mallory");

        alicesBillingAddress = new AddressImpl();
        alicesBillingAddress.setFullName("Alice Victim");
        alicesBillingAddress.setAddressLine1("1 Alice Street");
        alicesCard = new CustomerPaymentImpl();
        alicesCard.setId(ALICES_CARD_ID);
        alicesCard.setCustomer(alice);
        alicesCard.setPaymentToken("alice-card-token");
        alicesCard.setBillingAddress(alicesBillingAddress);

        customerPaymentDao = EasyMock.createMock(CustomerPaymentDao.class);
        EasyMock.expect(customerPaymentDao.readCustomerPaymentById(ALICES_CARD_ID)).andStubReturn(alicesCard);

        orderService = EasyMock.createMock(OrderService.class);
        orderPaymentService = EasyMock.createMock(OrderPaymentService.class);
        savedPaymentService = EasyMock.createMock(SavedPaymentService.class);
        cartStateService = EasyMock.createMock(CartStateService.class);
        addressService = EasyMock.createNiceMock(AddressService.class);
        request = EasyMock.createNiceMock(HttpServletRequest.class);

        cart = EasyMock.createNiceMock(Order.class);
        EasyMock.expect(cart.getTotalAfterAppliedPayments()).andStubReturn(new Money("25.00"));

        controller = new BroadleafPaymentInfoController();
        controller.customerPaymentService = new CustomerPaymentServiceImpl() {
            {
                customerPaymentDao = BroadleafPaymentInfoControllerTest.this.customerPaymentDao;
            }
        };
        controller.orderService = orderService;
        controller.orderPaymentService = orderPaymentService;
        controller.savedPaymentService = savedPaymentService;
        controller.cartStateService = cartStateService;
        controller.addressService = addressService;
        controller.paymentInfoFormValidator = EasyMock.createNiceMock(CheckoutPaymentInfoFormValidator.class);
        EasyMock.replay(controller.paymentInfoFormValidator);
    }

    @After
    public void tearDown() {
        RequestStateTestSupport.clear();
    }

    @Test
    public void savePaymentInfoRejectsAnotherCustomersSavedPayment() throws Exception {
        RequestStateTestSupport.bind(mallory, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(ALICES_CARD_ID);
        Address mallorysAddress = form.getAddress();

        try {
            controller.savePaymentInfo(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        assertSame("Alice's billing address must not be copied onto Mallory's form", mallorysAddress, form.getAddress());
        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void savePaymentInfoRejectsSavedPaymentForCustomerlessRequest() throws Exception {
        RequestStateTestSupport.bind(null, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(ALICES_CARD_ID);

        try {
            controller.savePaymentInfo(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void savePaymentInfoRejectsSavedPaymentForAnonymousCustomer() throws Exception {
        alice.setAnonymous(true);
        RequestStateTestSupport.bind(alice, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(ALICES_CARD_ID);

        try {
            controller.savePaymentInfo(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void savePaymentInfoRejectsUnknownSavedPayment() throws Exception {
        EasyMock.expect(customerPaymentDao.readCustomerPaymentById(999L)).andStubReturn(null);
        RequestStateTestSupport.bind(alice, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(999L);

        try {
            controller.savePaymentInfo(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void savePaymentInfoLetsOwnerUseSavedPayment() throws Exception {
        Address copiedAddress = new AddressImpl();
        EasyMock.expect(addressService.copyAddress(alicesBillingAddress)).andReturn(copiedAddress);
        EasyMock.expect(cartStateService.cartHasCreditCardPaymentWithSameToken("alice-card-token")).andReturn(false);
        orderService.removeCreditCardPaymentsFromOrder(cart);
        EasyMock.expect(orderPaymentService.createOrderPaymentFromCustomerPayment(cart, alicesCard, new Money("25.00")))
                .andReturn(EasyMock.createNiceMock(OrderPayment.class));
        RequestStateTestSupport.bind(alice, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(ALICES_CARD_ID);

        String view = controller.savePaymentInfo(request, null, null, form, bindingResult(form));

        assertEquals(controller.getCheckoutPageRedirect(), view);
        assertSame(copiedAddress, form.getAddress());
        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void savePaymentInfoRejectsUpdatingAnotherCustomersSavedPayment() throws Exception {
        RequestStateTestSupport.bind(mallory, cart);
        replayAll();
        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldSaveNewPayment(true);
        form.setShouldUseCustomerPayment(false);
        form.setCustomerPaymentId(ALICES_CARD_ID);

        try {
            controller.savePaymentInfo(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    @Test
    public void saveBillingAddressRejectsAnotherCustomersSavedPayment() throws Exception {
        RequestStateTestSupport.bind(mallory, cart);
        replayAll();
        PaymentInfoForm form = useSavedPaymentForm(ALICES_CARD_ID);
        Address mallorysAddress = form.getAddress();

        try {
            controller.saveBillingAddress(request, null, null, form, bindingResult(form));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        assertSame(mallorysAddress, form.getAddress());
        assertNotSame(alicesBillingAddress, form.getAddress());
        EasyMock.verify(orderService, orderPaymentService, savedPaymentService, cartStateService);
    }

    private void replayAll() {
        EasyMock.replay(customerPaymentDao, orderService, orderPaymentService, savedPaymentService, cartStateService,
                addressService, request, cart);
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

    private static Customer customer(Long id, String username) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        customer.setUsername(username);
        return customer;
    }

}
