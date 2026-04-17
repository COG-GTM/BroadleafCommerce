/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.core.order.dao;

import org.broadleafcommerce.core.order.OrderDataProvider;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.test.TestNGSiteIntegrationSetup;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import jakarta.annotation.Resource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderDaoTest extends TestNGSiteIntegrationSetup {

    String userName = new String();
    Long orderId;

    @Resource
    private OrderDao orderDao;

    @Resource
    private CustomerService customerService;

    @org.junit.jupiter.api.Order(1)


    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.order.OrderDataProvider#provideBasicSalesOrder")
    @Rollback(false)
    @Transactional
    public void createOrder(Order order) {
        userName = "customer1";
        Customer customer = customerService.readCustomerByUsername(userName);
        assert order.getId() == null;
        order.setCustomer(customer);
        order = orderDao.save(order);
        assert order.getId() != null;
        orderId = order.getId();
    }

    @org.junit.jupiter.api.Order(2)


    @Test
    public void readOrderById() {
        Order result = orderDao.readOrderById(orderId);
        assert result != null;
    }

    @org.junit.jupiter.api.Order(3)


    @Test
    @Transactional
    public void readOrdersForCustomer() {
        userName = "customer1";
        Customer user = customerService.readCustomerByUsername(userName);
        List<Order> orders = orderDao.readOrdersForCustomer(user.getId());
        assert orders.size() > 0;
    }

    //FIXME: After the change to cascading the deletion on PaymentResponseItems, this test does not work but for a really
    //strange reason; the list of PaymentResponseItems is getting removed from the Hibernate session for some really weird
    //reason. This only occurs sometimes, so it is probably due to the somewhat random ordering that TestNG puts around tests
//    @Test(groups = {"deleteOrderForCustomer"}, dependsOnGroups = {"readOrder"})
//    @Transactional
//    public void deleteOrderForCustomer(){
//        Order order = orderDao.readOrderById(orderId);
//        assert order != null;
//        assert order.getId() != null;
//        orderDao.delete(order);
//    }
}
