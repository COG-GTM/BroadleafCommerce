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

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.core.web.checkout.model.PaymentInfoForm;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
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
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.context.request.WebRequest;

public class BroadleafPaymentInfoControllerTest {

    private static final Long OWNER_PAYMENT_ID = 10L;
    private static final Long VICTIM_PAYMENT_ID = 20L;

    private BroadleafPaymentInfoController controller;
    private CustomerPaymentService customerPaymentService;
    private AddressService addressService;
    private Customer activeCustomer;
    private Address ownerAddress;
    private Address victimAddress;

    @Before
    public void setUp() {
        activeCustomer = customer(1L);
        ownerAddress = new AddressImpl();
        victimAddress = new AddressImpl();

        customerPaymentService = EasyMock.createMock(CustomerPaymentService.class);
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(OWNER_PAYMENT_ID))
                .andStubReturn(payment(OWNER_PAYMENT_ID, activeCustomer, ownerAddress));
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(VICTIM_PAYMENT_ID))
                .andStubReturn(payment(VICTIM_PAYMENT_ID, customer(2L), victimAddress));
        EasyMock.expect(customerPaymentService.readCustomerPaymentById(EasyMock.anyLong()))
                .andStubReturn(null);

        addressService = EasyMock.createMock(AddressService.class);
        EasyMock.expect(addressService.copyAddress(EasyMock.anyObject(Address.class)))
                .andStubAnswer(() -> (Address) EasyMock.getCurrentArguments()[0]);

        EasyMock.replay(customerPaymentService, addressService);

        controller = new BroadleafPaymentInfoController();
        controller.customerPaymentService = customerPaymentService;
        controller.addressService = addressService;

        setActiveCustomer(activeCustomer);
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void copiesBillingAddressFromOwnedPayment() {
        PaymentInfoForm form = form(OWNER_PAYMENT_ID);

        controller.copyCustomerPaymentAddressToBillingAddress(form);

        Assert.assertSame(ownerAddress, form.getAddress());
    }

    @Test
    public void rejectsBillingAddressCopyFromAnotherCustomersPayment() {
        PaymentInfoForm form = form(VICTIM_PAYMENT_ID);
        Address original = form.getAddress();

        assertSecurityException(() -> controller.copyCustomerPaymentAddressToBillingAddress(form));
        Assert.assertSame(original, form.getAddress());
    }

    @Test
    public void rejectsNonexistentPayment() {
        assertSecurityException(() -> controller.readCustomerOwnedPayment(999L));
        assertSecurityException(() -> controller.readCustomerOwnedPayment(null));
    }

    @Test
    public void rejectsWhenNoActiveCustomer() {
        setActiveCustomer(null);

        assertSecurityException(() -> controller.readCustomerOwnedPayment(OWNER_PAYMENT_ID));
    }

    @Test
    public void savePaymentInfoRejectsAnotherCustomersPayment() {
        PaymentInfoForm form = form(VICTIM_PAYMENT_ID);

        assertSecurityException(() -> controller.savePaymentInfo(null, null, null, form, null));
    }

    private void assertSecurityException(ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        } catch (Exception e) {
            Assert.fail("Expected SecurityException but got " + e);
        }
    }

    private void setActiveCustomer(Customer customer) {
        BroadleafRequestCustomerResolverImpl resolver = new BroadleafRequestCustomerResolverImpl();
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("blRequestCustomerResolver", resolver);
        resolver.setApplicationContext(applicationContext);

        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute(resolver.getCustomerRequestAttributeName(), WebRequest.SCOPE_REQUEST))
                .andStubReturn(customer);
        EasyMock.replay(webRequest);

        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    private static PaymentInfoForm form(Long customerPaymentId) {
        PaymentInfoForm form = new PaymentInfoForm();
        form.setShouldUseShippingAddress(false);
        form.setShouldUseCustomerPayment(true);
        form.setCustomerPaymentId(customerPaymentId);
        return form;
    }

    private static Customer customer(Long id) {
        CustomerImpl customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private static CustomerPayment payment(Long id, Customer owner, Address billingAddress) {
        CustomerPaymentImpl payment = new CustomerPaymentImpl();
        payment.setId(id);
        payment.setCustomer(owner);
        payment.setBillingAddress(billingAddress);
        return payment;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}
