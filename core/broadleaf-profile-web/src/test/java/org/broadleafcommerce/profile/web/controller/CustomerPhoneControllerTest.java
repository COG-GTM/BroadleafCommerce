/*-
 * #%L
 * BroadleafCommerce Profile Web
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
package org.broadleafcommerce.profile.web.controller;

import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPhone;
import org.broadleafcommerce.profile.core.domain.CustomerPhoneImpl;
import org.broadleafcommerce.profile.core.domain.PhoneImpl;
import org.broadleafcommerce.profile.core.service.CustomerPhoneService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CustomerPhoneControllerTest {

    private static final Long ALICE_ID = 1L;
    private static final Long MALLORY_ID = 2L;
    private static final Long ALICE_PHONE_1 = 1042L;
    private static final Long ALICE_PHONE_2 = 1043L;
    private static final Long MALLORY_PHONE = 2001L;

    private InMemoryCustomerPhoneService service;
    private Customer currentCustomer;
    private CustomerPhoneController controller;

    @Before
    public void setUp() {
        service = new InMemoryCustomerPhoneService();
        Customer alice = customer(ALICE_ID);
        Customer mallory = customer(MALLORY_ID);
        service.add(customerPhone(ALICE_PHONE_1, alice, true));
        service.add(customerPhone(ALICE_PHONE_2, alice, false));
        service.add(customerPhone(MALLORY_PHONE, mallory, true));

        controller = new CustomerPhoneController() {
            @Override
            protected Customer getCurrentCustomer(HttpServletRequest request) {
                return currentCustomer;
            }
        };
        controller.setCustomerPhoneService(service);
    }

    @Test
    public void deletePhoneRejectsAnotherCustomersPhone() {
        currentCustomer = customer(MALLORY_ID);

        assertNotFound(() -> controller.deletePhone(ALICE_PHONE_1, null));

        assertTrue(service.phones.containsKey(ALICE_PHONE_1));
        assertTrue(service.deleted.isEmpty());
    }

    @Test
    public void deletePhoneRejectsAnonymousCustomer() {
        currentCustomer = null;

        assertNotFound(() -> controller.deletePhone(ALICE_PHONE_1, null));

        assertTrue(service.phones.containsKey(ALICE_PHONE_1));
    }

    @Test
    public void deletePhoneRejectsUnknownId() {
        currentCustomer = customer(ALICE_ID);

        assertNotFound(() -> controller.deletePhone(9999L, null));

        assertTrue(service.deleted.isEmpty());
    }

    @Test
    public void deletePhoneDeletesOwnPhone() {
        currentCustomer = customer(ALICE_ID);

        controller.deletePhone(ALICE_PHONE_2, new NoOpAttributeRequest().proxy());

        assertFalse(service.phones.containsKey(ALICE_PHONE_2));
        assertEquals(List.of(ALICE_PHONE_2), service.deleted);
    }

    @Test
    public void makePhoneDefaultRejectsAnotherCustomersPhone() {
        currentCustomer = customer(MALLORY_ID);

        assertNotFound(() -> controller.makePhoneDefault(ALICE_PHONE_2, null));

        assertTrue(service.phones.get(ALICE_PHONE_1).getPhone().isDefault());
        assertFalse(service.phones.get(ALICE_PHONE_2).getPhone().isDefault());
        assertTrue(service.madeDefault.isEmpty());
    }

    @Test
    public void makePhoneDefaultUsesCurrentCustomerForOwnPhone() {
        currentCustomer = customer(ALICE_ID);

        controller.makePhoneDefault(ALICE_PHONE_2, new NoOpAttributeRequest().proxy());

        assertEquals(1, service.madeDefault.size());
        assertArrayEquals(new Long[] {ALICE_PHONE_2, ALICE_ID}, service.madeDefault.get(0));
        assertFalse(service.phones.get(ALICE_PHONE_1).getPhone().isDefault());
        assertTrue(service.phones.get(ALICE_PHONE_2).getPhone().isDefault());
    }

    @Test
    public void mutatingHandlersArePostOnly() throws Exception {
        assertPostOnly("deletePhone");
        assertPostOnly("makePhoneDefault");
    }

    private void assertPostOnly(String methodName) throws Exception {
        RequestMapping mapping = CustomerPhoneController.class
                .getMethod(methodName, Long.class, HttpServletRequest.class)
                .getAnnotation(RequestMapping.class);
        assertArrayEquals(new RequestMethod[] {RequestMethod.POST}, mapping.method());
    }

    private static void assertNotFound(Runnable call) {
        try {
            call.run();
            fail("Expected ResponseStatusException(404)");
        } catch (ResponseStatusException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private static CustomerPhone customerPhone(Long id, Customer owner, boolean isDefault) {
        PhoneImpl phone = new PhoneImpl();
        phone.setId(id);
        phone.setDefault(isDefault);
        CustomerPhone customerPhone = new CustomerPhoneImpl();
        customerPhone.setId(id);
        customerPhone.setCustomer(owner);
        customerPhone.setPhone(phone);
        return customerPhone;
    }

    private static class NoOpAttributeRequest {
        HttpServletRequest proxy() {
            return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    (p, m, a) -> null);
        }
    }

    private static class InMemoryCustomerPhoneService implements CustomerPhoneService {

        final Map<Long, CustomerPhone> phones = new HashMap<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Long[]> madeDefault = new ArrayList<>();

        void add(CustomerPhone customerPhone) {
            phones.put(customerPhone.getId(), customerPhone);
        }

        @Override
        public CustomerPhone readCustomerPhoneById(Long customerPhoneId) {
            return phones.get(customerPhoneId);
        }

        @Override
        public void deleteCustomerPhoneById(Long customerPhoneId) {
            deleted.add(customerPhoneId);
            phones.remove(customerPhoneId);
        }

        @Override
        public void makeCustomerPhoneDefault(Long customerPhoneId, Long customerId) {
            madeDefault.add(new Long[] {customerPhoneId, customerId});
            for (CustomerPhone customerPhone : readActiveCustomerPhonesByCustomerId(customerId)) {
                customerPhone.getPhone().setDefault(customerPhone.getId().equals(customerPhoneId));
            }
        }

        @Override
        public List<CustomerPhone> readActiveCustomerPhonesByCustomerId(Long customerId) {
            List<CustomerPhone> result = new ArrayList<>();
            for (CustomerPhone customerPhone : phones.values()) {
                if (customerPhone.getCustomer().getId().equals(customerId)) {
                    result.add(customerPhone);
                }
            }
            return result;
        }

        @Override
        public List<CustomerPhone> readAllCustomerPhonesByCustomerId(Long customerId) {
            return readActiveCustomerPhonesByCustomerId(customerId);
        }

        @Override
        public CustomerPhone saveCustomerPhone(CustomerPhone customerPhone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CustomerPhone findDefaultCustomerPhone(Long customerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CustomerPhone create() {
            throw new UnsupportedOperationException();
        }
    }
}
