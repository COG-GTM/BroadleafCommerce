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
import org.easymock.classextension.EasyMock;

import junit.framework.TestCase;

public class CustomerServiceImplTest extends TestCase {

    private CustomerServiceImpl customerService;
    private CustomerDao customerDao;

    @Override
    protected void setUp() throws Exception {
        customerDao = EasyMock.createMock(CustomerDao.class);
        customerService = new CustomerServiceImpl();
        customerService.setCustomerDao(customerDao);
    }

    public void testRegisterCustomerRejectsIdOfRegisteredCustomer() {
        Customer existing = new CustomerImpl();
        existing.setId(1042L);
        existing.setUsername("alice");
        existing.setRegistered(true);
        EasyMock.expect(customerDao.readCustomerById(1042L)).andReturn(existing);
        EasyMock.replay(customerDao);

        Customer attacker = new CustomerImpl();
        attacker.setId(1042L);
        attacker.setUsername("mallory");

        try {
            customerService.registerCustomer(attacker, "password1", "password1");
            fail("Registration targeting an existing registered customer id must be rejected");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertFalse(attacker.isRegistered());
        assertNull(attacker.getUnencodedPassword());
        EasyMock.verify(customerDao);
    }
}
