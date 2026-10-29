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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.web.checkout.model.BillingInfoForm;
import org.broadleafcommerce.core.web.controller.support.RequestStateTestSupport;
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

public class BroadleafBillingInfoControllerTest {

    private BroadleafBillingInfoController controller;
    private CustomerPaymentDao customerPaymentDao;
    private OrderService orderService;
    private AddressService addressService;
    private Order cart;
    private Customer mallory;

    @Before
    public void setUp() {
        Customer alice = new CustomerImpl();
        alice.setId(1L);
        mallory = new CustomerImpl();
        mallory.setId(2L);

        CustomerPayment alicesCard = new CustomerPaymentImpl();
        alicesCard.setId(100L);
        alicesCard.setCustomer(alice);
        alicesCard.setBillingAddress(new AddressImpl());

        customerPaymentDao = EasyMock.createMock(CustomerPaymentDao.class);
        EasyMock.expect(customerPaymentDao.readCustomerPaymentById(100L)).andStubReturn(alicesCard);
        orderService = EasyMock.createMock(OrderService.class);
        addressService = EasyMock.createMock(AddressService.class);
        cart = EasyMock.createNiceMock(Order.class);

        controller = new BroadleafBillingInfoController();
        controller.customerPaymentService = new CustomerPaymentServiceImpl() {
            {
                customerPaymentDao = BroadleafBillingInfoControllerTest.this.customerPaymentDao;
            }
        };
        controller.orderService = orderService;
        controller.addressService = addressService;
    }

    @After
    public void tearDown() {
        RequestStateTestSupport.clear();
    }

    @Test
    public void saveBillingAddressRejectsAnotherCustomersSavedPayment() throws Exception {
        RequestStateTestSupport.bind(mallory, cart);
        EasyMock.replay(customerPaymentDao, orderService, addressService, cart);

        BillingInfoForm form = new BillingInfoForm();
        form.setUseCustomerPayment(true);
        form.setCustomerPaymentId(100L);
        Address mallorysAddress = form.getAddress();

        try {
            controller.saveBillingAddress(null, null, null, form, new BeanPropertyBindingResult(form, "billingInfoForm"));
            fail("Expected SecurityException");
        } catch (SecurityException expected) {
            // expected
        }

        assertSame(mallorysAddress, form.getAddress());
        EasyMock.verify(orderService, addressService);
    }

}
