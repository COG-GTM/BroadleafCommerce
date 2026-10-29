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
package org.broadleafcommerce.core.web.controller;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.broadleafcommerce.profile.core.service.CustomerPaymentServiceImpl;
import org.easymock.EasyMock;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds an active customer and cart to the current thread the same way the storefront request filters do, and
 * provides an in-memory {@link CustomerPaymentServiceImpl} so the real ownership check is exercised.
 */
public final class CustomerOwnedPaymentTestUtils {

    private CustomerOwnedPaymentTestUtils() {
    }

    public static void bindRequestState(Customer customer, Order cart) {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("blRequestCustomerResolver", BroadleafRequestCustomerResolverImpl.class);
        applicationContext.refresh();
        new BroadleafRequestCustomerResolverImpl().setApplicationContext(applicationContext);

        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute("customer", WebRequest.SCOPE_REQUEST)).andReturn(customer).anyTimes();
        EasyMock.expect(webRequest.getAttribute(
                CartStateRequestProcessor.getCartRequestAttributeName(), WebRequest.SCOPE_REQUEST
        )).andReturn(cart).anyTimes();
        EasyMock.replay(webRequest);

        BroadleafRequestContext requestContext = new BroadleafRequestContext();
        requestContext.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(requestContext);
    }

    public static void clearRequestState() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    public static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    public static CustomerPayment customerPayment(Long id, Customer owner) {
        CustomerPayment customerPayment = new CustomerPaymentImpl();
        customerPayment.setId(id);
        customerPayment.setCustomer(owner);
        customerPayment.setPaymentToken("token-" + id);
        return customerPayment;
    }

    public static class InMemoryCustomerPaymentService extends CustomerPaymentServiceImpl {

        public final Map<Long, CustomerPayment> payments = new HashMap<>();
        public Long deletedId;
        public CustomerPayment defaultPayment;

        public InMemoryCustomerPaymentService(CustomerPayment... customerPayments) {
            for (CustomerPayment customerPayment : customerPayments) {
                payments.put(customerPayment.getId(), customerPayment);
            }
        }

        @Override
        public CustomerPayment readCustomerPaymentById(Long customerPaymentId) {
            return payments.get(customerPaymentId);
        }

        @Override
        public void deleteCustomerPaymentById(Long customerPaymentId) {
            deletedId = customerPaymentId;
        }

        @Override
        public CustomerPayment setAsDefaultPayment(CustomerPayment payment) {
            defaultPayment = payment;
            return payment;
        }
    }

}
