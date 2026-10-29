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

import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.payment.service.OrderPaymentService;
import org.broadleafcommerce.core.web.checkout.model.BillingInfoForm;
import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.core.web.payment.service.SavedPaymentService;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerPaymentService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

public class CheckoutCustomerPaymentOwnershipTest {

    private static final Long ALICE_PAYMENT_ID = 100L;

    private Customer alice;
    private Customer mallory;
    private Customer activeCustomer;
    private Address aliceAddress;
    private CustomerPayment alicePayment;

    private CustomerPaymentService customerPaymentService;
    private AddressService addressService;
    private OrderService orderService;
    private OrderPaymentService orderPaymentService;
    private SavedPaymentService savedPaymentService;

    private BroadleafBillingInfoController billingInfoController;
    private BroadleafPaymentInfoController paymentInfoController;

    @Before
    public void setUp() {
        alice = customer(1L);
        mallory = customer(2L);

        aliceAddress = new AddressImpl();
        aliceAddress.setAddressLine1("1 Alice Street");
        alicePayment = new CustomerPaymentImpl();
        alicePayment.setId(ALICE_PAYMENT_ID);
        alicePayment.setCustomer(alice);
        alicePayment.setBillingAddress(aliceAddress);

        customerPaymentService = EasyMock.createMock(CustomerPaymentService.class);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(ALICE_PAYMENT_ID))
                .andReturn(alicePayment).anyTimes();
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(EasyMock.not(EasyMock.eq(ALICE_PAYMENT_ID))))
                .andReturn(null).anyTimes();

        addressService = EasyMock.createStrictMock(AddressService.class);
        orderService = EasyMock.createStrictMock(OrderService.class);
        orderPaymentService = EasyMock.createStrictMock(OrderPaymentService.class);
        savedPaymentService = EasyMock.createStrictMock(SavedPaymentService.class);

        billingInfoController = new BroadleafBillingInfoController() {
            @Override
            protected Customer getActiveCustomer() {
                return activeCustomer;
            }
        };
        paymentInfoController = new BroadleafPaymentInfoController() {
            @Override
            protected Customer getActiveCustomer() {
                return activeCustomer;
            }
        };
        for (AbstractCheckoutController controller : new AbstractCheckoutController[] {billingInfoController, paymentInfoController}) {
            controller.customerPaymentService = customerPaymentService;
            controller.addressService = addressService;
            controller.orderService = orderService;
            controller.orderPaymentService = orderPaymentService;
        }
        paymentInfoController.savedPaymentService = savedPaymentService;
    }

    @Test
    public void readActiveCustomerPaymentReturnsPaymentOwnedByActiveCustomer() {
        activeCustomer = alice;
        replayAll();

        Assert.assertSame(alicePayment, billingInfoController.readActiveCustomerPayment(ALICE_PAYMENT_ID));
    }

    @Test
    public void readActiveCustomerPaymentIgnoresOtherCustomersPayment() {
        activeCustomer = mallory;
        replayAll();

        Assert.assertNull(billingInfoController.readActiveCustomerPayment(ALICE_PAYMENT_ID));
    }

    @Test
    public void readActiveCustomerPaymentIgnoresUnknownPaymentAndMissingCustomer() {
        activeCustomer = alice;
        replayAll();
        Assert.assertNull(billingInfoController.readActiveCustomerPayment(999L));
        Assert.assertNull(billingInfoController.readActiveCustomerPayment(null));

        activeCustomer = null;
        Assert.assertNull(billingInfoController.readActiveCustomerPayment(ALICE_PAYMENT_ID));
    }

    @Test
    public void billingStepRejectsOtherCustomersSavedPayment() throws Exception {
        activeCustomer = mallory;
        replayAll();

        BillingInfoForm form = new BillingInfoForm();
        Address submittedAddress = form.getAddress();
        form.setUseCustomerPayment(true);
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);
        BindingResult result = bindingResult(form);

        String view = billingInfoController.saveBillingAddress(null, null, null, form, result);

        Assert.assertEquals(billingInfoController.getCheckoutView(), view);
        assertRejected(result);
        Assert.assertSame(submittedAddress, form.getAddress());
        Assert.assertNull(form.getAddress().getAddressLine1());
        verifyAll();
    }

    @Test
    public void savePaymentInfoRejectsUsingOtherCustomersSavedPayment() throws Exception {
        activeCustomer = mallory;
        replayAll();

        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldUseCustomerPayment(true);
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);
        BindingResult result = bindingResult(form);

        String view = paymentInfoController.savePaymentInfo(null, null, null, form, result);

        Assert.assertEquals(paymentInfoController.getCheckoutView(), view);
        assertRejected(result);
        Assert.assertNull(form.getAddress().getAddressLine1());
        verifyAll();
    }

    @Test
    public void savePaymentInfoRejectsUpdatingOtherCustomersSavedPayment() throws Exception {
        activeCustomer = mallory;
        replayAll();

        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldSaveNewPayment(true);
        form.setShouldUseCustomerPayment(false);
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);
        BindingResult result = bindingResult(form);

        String view = paymentInfoController.savePaymentInfo(null, null, null, form, result);

        Assert.assertEquals(paymentInfoController.getCheckoutView(), view);
        assertRejected(result);
        verifyAll();
    }

    @Test
    public void paymentStepBillingAddressRejectsOtherCustomersSavedPayment() throws Exception {
        activeCustomer = mallory;
        replayAll();

        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldUseCustomerPayment(true);
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);
        BindingResult result = bindingResult(form);

        String view = paymentInfoController.saveBillingAddress(null, null, null, form, result);

        Assert.assertEquals(paymentInfoController.getCheckoutView(), view);
        assertRejected(result);
        Assert.assertNull(form.getAddress().getAddressLine1());
        verifyAll();
    }

    @Test
    public void copyCustomerPaymentAddressIgnoresOtherCustomersSavedPayment() {
        activeCustomer = mallory;
        replayAll();

        PaymentInfoForm form = new PaymentInfoForm();
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);

        paymentInfoController.copyCustomerPaymentAddressToBillingAddress(form);

        Assert.assertNull(form.getAddress().getAddressLine1());
        verifyAll();
    }

    @Test
    public void copyCustomerPaymentAddressCopiesOwnSavedPayment() {
        activeCustomer = alice;
        Address copy = new AddressImpl();
        copy.setAddressLine1("1 Alice Street");
        EasyMock.expect(addressService.copyAddress(aliceAddress)).andReturn(copy);
        replayAll();

        PaymentInfoForm form = new PaymentInfoForm();
        form.setCustomerPaymentId(ALICE_PAYMENT_ID);

        paymentInfoController.copyCustomerPaymentAddressToBillingAddress(form);

        Assert.assertSame(copy, form.getAddress());
        verifyAll();
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private static BindingResult bindingResult(Object form) {
        return new BeanPropertyBindingResult(form, "form");
    }

    private static void assertRejected(BindingResult result) {
        Assert.assertTrue(result.hasGlobalErrors());
        Assert.assertEquals(AbstractCheckoutController.INVALID_CUSTOMER_PAYMENT_ERROR,
                result.getGlobalError().getCode());
    }

    private void replayAll() {
        EasyMock.replay(customerPaymentService, addressService, orderService, orderPaymentService, savedPaymentService);
    }

    private void verifyAll() {
        EasyMock.verify(addressService, orderService, orderPaymentService, savedPaymentService);
    }
}
