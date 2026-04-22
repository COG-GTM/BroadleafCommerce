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

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.type.OrderStatus;
import org.broadleafcommerce.core.payment.domain.OrderPayment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class OrderFieldResolver {

    @Autowired
    @Qualifier("blOrderService")
    protected OrderService orderService;

    @SchemaMapping(typeName = "Order", field = "orderItems")
    public List<OrderItem> orderItems(Order order) {
        return order.getOrderItems();
    }

    @SchemaMapping(typeName = "Order", field = "fulfillmentGroups")
    public List<FulfillmentGroup> fulfillmentGroups(Order order) {
        return order.getFulfillmentGroups();
    }

    @SchemaMapping(typeName = "Order", field = "payments")
    public List<OrderPayment> payments(Order order) {
        return orderService.findPaymentsForOrder(order);
    }

    @SchemaMapping(typeName = "Order", field = "subTotal")
    public Money subTotal(Order order) {
        return order.getSubTotal();
    }

    @SchemaMapping(typeName = "Order", field = "total")
    public Money total(Order order) {
        return order.getTotal();
    }

    @SchemaMapping(typeName = "Order", field = "status")
    public String status(Order order) {
        OrderStatus status = order.getStatus();
        return status != null ? status.getType() : null;
    }

}
