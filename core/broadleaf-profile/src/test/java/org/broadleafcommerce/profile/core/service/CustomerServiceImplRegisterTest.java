/*-
 * #%L
 * BroadleafCommerce Profile
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
package org.broadleafcommerce.profile.core.service;

import org.broadleafcommerce.profile.core.dao.CustomerDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CustomerServiceImplRegisterTest {

    private static final Long CUSTOMER_ID = 100L;

    private final List<Customer> savedCustomers = new ArrayList<>();
    private Customer existingCustomer;
    private CustomerServiceImpl customerService;

    @Before
    public void setUp() {
        customerService = new CustomerServiceImpl();
        customerService.customerDao = (CustomerDao) Proxy.newProxyInstance(
                CustomerDao.class.getClassLoader(),
                new Class<?>[]{CustomerDao.class},
                (proxy, method, args) -> {
                    if ("readCustomerById".equals(method.getName())) {
                        return existingCustomer != null && existingCustomer.getId().equals(args[0])
                                ? existingCustomer
                                : null;
                    }
                    if ("save".equals(method.getName())) {
                        savedCustomers.add((Customer) args[0]);
                        return args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void registerCustomerRejectsIdOfRegisteredCustomer() {
        existingCustomer = customer(CUSTOMER_ID, true);
        Customer payload = customer(CUSTOMER_ID, false);
        payload.setEmailAddress("mallory@example.com");
        payload.setUsername("mallory@example.com");

        try {
            customerService.registerCustomer(payload, "MalloryPassword1", "MalloryPassword1");
            fail("registering over an existing registered customer must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertTrue(savedCustomers.isEmpty());
    }

    @Test
    public void anonymousCustomerCanBeRegistered() {
        existingCustomer = customer(CUSTOMER_ID, false);

        customerService.checkCustomerCanBeRegistered(customer(CUSTOMER_ID, false));
    }

    @Test
    public void newCustomerCanBeRegistered() {
        existingCustomer = customer(CUSTOMER_ID, true);

        customerService.checkCustomerCanBeRegistered(customer(null, false));
        customerService.checkCustomerCanBeRegistered(customer(CUSTOMER_ID + 1, false));
    }

    private static Customer customer(Long id, boolean registered) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        customer.setRegistered(registered);
        return customer;
    }

}
