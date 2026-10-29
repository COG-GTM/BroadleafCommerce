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
import org.easymock.EasyMock;

import junit.framework.TestCase;

public class CustomerServiceImplRegisterTest extends TestCase {

    public void testRegisterRejectsIdOfExistingRegisteredCustomer() {
        Customer existing = new CustomerImpl();
        existing.setId(100L);
        existing.setRegistered(true);
        existing.setEmailAddress("alice@example.com");

        CustomerDao customerDao = EasyMock.createMock(CustomerDao.class);
        EasyMock.expect(customerDao.readCustomerById(100L)).andReturn(existing);
        EasyMock.replay(customerDao);

        CustomerServiceImpl service = new CustomerServiceImpl();
        service.customerDao = customerDao;

        Customer attacker = new CustomerImpl();
        attacker.setId(100L);
        attacker.setEmailAddress("mallory@example.com");
        attacker.setUsername("mallory@example.com");

        try {
            service.registerCustomer(attacker, "password", "password");
            fail("Expected registration with an existing registered customer's id to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertFalse(attacker.isRegistered());
        assertEquals("alice@example.com", existing.getEmailAddress());
        EasyMock.verify(customerDao);
    }

}
