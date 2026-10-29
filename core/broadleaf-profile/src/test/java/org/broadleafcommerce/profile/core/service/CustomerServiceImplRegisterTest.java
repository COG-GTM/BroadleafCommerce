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
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class CustomerServiceImplRegisterTest {

    @Test
    public void registerCustomerRefusesToOverwriteRegisteredCustomer() {
        Customer victim = customer(100L, "alice@example.com", true);
        RecordingCustomerService service = new RecordingCustomerService(victim);

        Customer attacker = customer(100L, "mallory@example.com", false);
        try {
            service.registerCustomer(attacker, "Secret123!", "Secret123!");
            Assert.fail("Expected registration over a registered customer to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        Assert.assertNull(service.saved);
        Assert.assertFalse(attacker.isRegistered());
        Assert.assertEquals("alice@example.com", victim.getEmailAddress());
    }

    @Test
    public void registerCustomerAllowsExistingAnonymousCustomer() {
        Customer anonymous = customer(7L, null, false);
        RecordingCustomerService service = new RecordingCustomerService(anonymous);

        Customer registering = customer(7L, "bob@example.com", false);
        try {
            service.registerCustomer(registering, "Secret123!", "Secret123!");
        } catch (ReachedSaveException expected) {
            // expected
        }

        Assert.assertSame(registering, service.saved);
        Assert.assertTrue(registering.isRegistered());
    }

    @Test
    public void registerCustomerAllowsNewCustomer() {
        RecordingCustomerService service = new RecordingCustomerService();

        Customer registering = customer(null, "carol@example.com", false);
        try {
            service.registerCustomer(registering, "Secret123!", "Secret123!");
        } catch (ReachedSaveException expected) {
            // expected
        }

        Assert.assertSame(registering, service.saved);
    }

    private static Customer customer(Long id, String email, boolean registered) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        customer.setEmailAddress(email);
        customer.setRegistered(registered);
        return customer;
    }

    private static class ReachedSaveException extends RuntimeException {
    }

    private static class RecordingCustomerService extends CustomerServiceImpl {

        private final Map<Long, Customer> existing = new HashMap<>();
        private Customer saved;

        RecordingCustomerService(Customer... customers) {
            for (Customer customer : customers) {
                existing.put(customer.getId(), customer);
            }
        }

        @Override
        public Customer readCustomerById(Long id) {
            return existing.get(id);
        }

        @Override
        public Customer saveCustomer(Customer customer) {
            saved = customer;
            throw new ReachedSaveException();
        }
    }

}
