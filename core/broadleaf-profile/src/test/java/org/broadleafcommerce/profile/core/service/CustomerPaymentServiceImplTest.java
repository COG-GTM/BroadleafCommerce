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

    protected CustomerPaymentServiceImpl service;
    protected CustomerPayment payment;

    @Before
    public void setUp() {
        payment = new CustomerPaymentImpl();
        payment.setId(10L);
        payment.setCustomer(customer(1L));

        CustomerPaymentDao dao = EasyMock.createNiceMock(CustomerPaymentDao.class);
        EasyMock.expect(dao.readCustomerPaymentById(10L)).andReturn(payment).anyTimes();
        EasyMock.replay(dao);

        service = new CustomerPaymentServiceImpl();
        service.customerPaymentDao = dao;
    }

    @Test
    public void returnsPaymentOwnedByCustomer() {
        Assert.assertSame(payment, service.readCustomerPaymentByIdAndCustomer(10L, customer(1L)));
    }

    @Test
    public void hidesPaymentOwnedByAnotherCustomer() {
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(10L, customer(2L)));
    }

    @Test
    public void hidesPaymentWhenNoActiveCustomer() {
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(10L, null));
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(10L, new CustomerImpl()));
    }

    @Test
    public void hidesPaymentWithoutOwner() {
        payment.setCustomer(null);
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(10L, customer(1L)));
    }

    @Test
    public void returnsNullForUnknownOrMissingId() {
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(99L, customer(1L)));
        Assert.assertNull(service.readCustomerPaymentByIdAndCustomer(null, customer(1L)));
    }

    protected Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

}
