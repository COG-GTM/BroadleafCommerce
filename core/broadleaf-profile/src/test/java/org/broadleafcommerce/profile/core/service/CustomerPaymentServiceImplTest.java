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

import org.broadleafcommerce.profile.core.dao.CustomerPaymentDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CustomerPaymentServiceImplTest {

    private CustomerPaymentServiceImpl service;
    private CustomerPaymentDao dao;
    private Customer owner;
    private Customer otherCustomer;
    private CustomerPayment ownersPayment;

    @Before
    public void setUp() {
        dao = EasyMock.createMock(CustomerPaymentDao.class);
        service = new CustomerPaymentServiceImpl();
        service.customerPaymentDao = dao;

        owner = customer(1L);
        otherCustomer = customer(2L);

        ownersPayment = new CustomerPaymentImpl();
        ownersPayment.setId(100L);
        ownersPayment.setCustomer(owner);
    }

    @Test
    public void returnsPaymentOwnedByCustomer() {
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(ownersPayment);
        EasyMock.replay(dao);

        Assert.assertSame(ownersPayment, service.readCustomerPaymentByIdAndCustomer(100L, customer(1L)));
        EasyMock.verify(dao);
    }

    @Test
    public void returnsNullForPaymentOwnedByAnotherCustomer() {
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(ownersPayment);
        EasyMock.replay(dao);

        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(100L, otherCustomer));
        EasyMock.verify(dao);
    }

    @Test
    public void returnsNullForMissingPayment() {
        EasyMock.expect(dao.readCustomerPaymentById(404L)).andReturn(null);
        EasyMock.replay(dao);

        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(404L, owner));
        EasyMock.verify(dao);
    }

    @Test
    public void returnsNullForPaymentWithoutCustomer() {
        ownersPayment.setCustomer(null);
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(ownersPayment);
        EasyMock.replay(dao);

        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(100L, owner));
        EasyMock.verify(dao);
    }

    @Test
    public void returnsNullWithoutCustomerOrIdWithoutLookup() {
        EasyMock.replay(dao);

        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(100L, null));
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(100L, new CustomerImpl()));
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(null, owner));
        EasyMock.verify(dao);
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

}
