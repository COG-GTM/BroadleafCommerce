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
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CustomerServiceImplRegisterTest {

    @Test
    public void registeringOverAnExistingRegisteredCustomerIdIsRejected() {
        Customer victim = new CustomerImpl();
        victim.setId(1042L);
        victim.setUsername("alice@example.com");
        victim.setRegistered(true);

        List<String> calls = new ArrayList<>();
        CustomerDao dao = (CustomerDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {CustomerDao.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return "toString".equals(method.getName()) ? "stub" : method.invoke(this, args);
                    }
                    calls.add(method.getName());
                    if ("readCustomerById".equals(method.getName())) {
                        return Long.valueOf(1042L).equals(args[0]) ? victim : null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        CustomerServiceImpl service = new CustomerServiceImpl();
        service.setCustomerDao(dao);

        Customer attacker = new CustomerImpl();
        attacker.setId(1042L);
        attacker.setUsername("mallory@example.com");

        try {
            service.registerCustomer(attacker, "secret123", "secret123");
            fail("Expected registration over an existing registered customer to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("1042"));
        }
        assertEquals(List.of("readCustomerById"), calls);
        assertEquals("alice@example.com", victim.getUsername());
    }
}
