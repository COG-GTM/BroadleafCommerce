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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.broadleafcommerce.profile.core.dao.CustomerPaymentDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.domain.CustomerPayment;
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class CustomerPaymentServiceImplTest {

    private CustomerPaymentServiceImpl service;
    private CustomerPaymentDao dao;
    private Customer alice;
    private Customer mallory;
    private CustomerPayment alicesCard;

    @Before
    public void setUp() {
        dao = EasyMock.createMock(CustomerPaymentDao.class);
        service = new CustomerPaymentServiceImpl();
        service.customerPaymentDao = dao;

        alice = customer(1L);
        mallory = customer(2L);

        alicesCard = new CustomerPaymentImpl();
        alicesCard.setId(100L);
        alicesCard.setCustomer(alice);
    }

    @Test
    public void returnsPaymentOwnedByCustomer() {
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(alicesCard);
        EasyMock.replay(dao);

        assertSame(alicesCard, service.readCustomerPaymentByIdForCustomer(100L, alice));
        EasyMock.verify(dao);
    }

    @Test
    public void ownershipIsComparedById() {
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(alicesCard);
        EasyMock.replay(dao);

        assertSame(alicesCard, service.readCustomerPaymentByIdForCustomer(100L, customer(1L)));
    }

    @Test
    public void rejectsPaymentOwnedByAnotherCustomer() {
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(alicesCard);
        EasyMock.replay(dao);

        assertNull(service.readCustomerPaymentByIdForCustomer(100L, mallory));
    }

    @Test
    public void rejectsMissingPayment() {
        EasyMock.expect(dao.readCustomerPaymentById(999L)).andReturn(null);
        EasyMock.replay(dao);

        assertNull(service.readCustomerPaymentByIdForCustomer(999L, alice));
    }

    @Test
    public void rejectsPaymentWithoutOwner() {
        alicesCard.setCustomer(null);
        EasyMock.expect(dao.readCustomerPaymentById(100L)).andReturn(alicesCard);
        EasyMock.replay(dao);

        assertNull(service.readCustomerPaymentByIdForCustomer(100L, alice));
    }

    @Test
    public void rejectsNullCustomerOrIdsWithoutQuerying() {
        EasyMock.replay(dao);

        assertNull(service.readCustomerPaymentByIdForCustomer(100L, null));
        assertNull(service.readCustomerPaymentByIdForCustomer(null, alice));
        assertNull(service.readCustomerPaymentByIdForCustomer(100L, new CustomerImpl()));
        EasyMock.verify(dao);
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

}
