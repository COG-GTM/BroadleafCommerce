/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.graphql.resolvers;

import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.type.OrderStatus;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

@Controller
public class CartQueryResolver {

    @Autowired
    @Qualifier("blOrderService")
    protected OrderService orderService;

    @QueryMapping
    public Order cart() {
        Customer customer = CustomerState.getCustomer();
        if (customer == null) {
            return null;
        }
        return orderService.findCartForCustomer(customer);
    }

    @QueryMapping
    public Order order(@Argument Long id) {
        Order order = orderService.findOrderById(id);
        return filterOrderForCurrentCustomer(order);
    }

    @QueryMapping
    public Order orderByNumber(@Argument String orderNumber) {
        Order order = orderService.findOrderByOrderNumber(orderNumber);
        return filterOrderForCurrentCustomer(order);
    }

    protected Order filterOrderForCurrentCustomer(Order order) {
        if (order == null) {
            return null;
        }
        Customer currentCustomer = CustomerState.getCustomer();
        Customer orderCustomer = order.getCustomer();
        if (currentCustomer == null || orderCustomer == null
                || !currentCustomer.equals(orderCustomer)) {
            return null;
        }
        return order;
    }

    @QueryMapping
    public List<Order> orderHistory(@Argument String status) {
        Customer customer = CustomerState.getCustomer();
        if (customer == null) {
            return Collections.emptyList();
        }
        if (status != null) {
            OrderStatus orderStatus = OrderStatus.getInstance(status);
            return orderService.findOrdersForCustomer(customer, orderStatus);
        }
        return orderService.findOrdersForCustomer(customer);
    }

    @QueryMapping
    public Customer customer() {
        return CustomerState.getCustomer();
    }

}
