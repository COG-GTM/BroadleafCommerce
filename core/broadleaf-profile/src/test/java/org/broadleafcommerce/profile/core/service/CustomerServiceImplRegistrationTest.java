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

import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class CustomerServiceImplRegistrationTest extends TestCase {

    private Map<Long, Customer> customersById;
    private CustomerServiceImpl customerService;

    @Override
    protected void setUp() throws Exception {
        customersById = new HashMap<>();
        customerService = new CustomerServiceImpl() {

            @Override
            public Customer readCustomerById(Long id) {
                return customersById.get(id);
            }
        };
    }

    public void testRegistrationOfANewCustomerIsAllowed() throws Exception {
        customerService.checkRegistrationTarget(customer(null, true));
    }

    public void testRegistrationOfAnAnonymousCustomerIsAllowed() throws Exception {
        Customer anonymousCustomer = customer(100L, false);
        customersById.put(anonymousCustomer.getId(), anonymousCustomer);

        customerService.checkRegistrationTarget(customer(anonymousCustomer.getId(), false));
    }

    public void testRegistrationOntoARegisteredCustomerIsRejected() throws Exception {
        Customer registeredCustomer = customer(200L, true);
        customersById.put(registeredCustomer.getId(), registeredCustomer);

        try {
            customerService.checkRegistrationTarget(customer(registeredCustomer.getId(), false));
            fail("Expected the registration of a customer carrying the id of a registered customer to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(String.valueOf(registeredCustomer.getId())));
        }
    }

    private Customer customer(Long id, boolean registered) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        customer.setRegistered(registered);
        return customer;
    }

}
