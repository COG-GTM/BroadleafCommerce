/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.spec.order.service.workflow.update.options

import org.broadleafcommerce.core.order.domain.BundleOrderItemImpl
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem
import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl
import org.broadleafcommerce.core.order.domain.OrderItem
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException
import org.broadleafcommerce.core.order.service.workflow.update.options.ValidateUpdateProductOptionsRequestActivity
import org.broadleafcommerce.core.spec.order.service.workflow.BaseOrderWorkflowSpec

class ValidateUpdateProductOptionsRequestActivitySpec extends BaseOrderWorkflowSpec {

    OrderItemService mockOrderItemService = Mock()

    DiscreteOrderItem ownItem
    DiscreteOrderItem bundledItem
    DiscreteOrderItem childItem

    def setup() {
        activity = Spy(ValidateUpdateProductOptionsRequestActivity).with {
            orderItemService = mockOrderItemService
            it
        }

        ownItem = new DiscreteOrderItemImpl(id: 10)
        childItem = new DiscreteOrderItemImpl(id: 12, parentOrderItem: ownItem)
        ownItem.childOrderItems = [childItem] as List<OrderItem>
        bundledItem = new DiscreteOrderItemImpl(id: 11)
        BundleOrderItemImpl bundle = new BundleOrderItemImpl(id: 20)
        bundle.discreteOrderItems = [bundledItem]
        context.seedData.order.orderItems = [ownItem, bundle] as List<OrderItem>
    }

    def "An order item belonging to the order is resolved and stored on the request"() {
        setup:
        context.seedData.itemRequest.orderItemId = 10

        when:
        context = activity.execute(context)

        then:
        context.seedData.orderItem.is(ownItem)
        0 * mockOrderItemService.readOrderItemById(_)
    }

    def "Bundle members and child items of the order can be targeted"() {
        setup:
        context.seedData.itemRequest.orderItemId = itemId

        when:
        context = activity.execute(context)

        then:
        context.seedData.orderItem.id == itemId

        where:
        itemId << [11L, 12L]
    }

    def "An order item id from another order is rejected without loading it by id"() {
        setup: "Mallory passes her own cart but Alice's order item id"
        context.seedData.itemRequest.orderItemId = 999

        when:
        activity.execute(context)

        then:
        ItemNotFoundException e = thrown()
        e.message.contains("999")
        context.seedData.orderItem == null
        0 * mockOrderItemService.readOrderItemById(_)
    }

    def "A null order item id is rejected"() {
        setup:
        context.seedData.itemRequest.orderItemId = null

        when:
        activity.execute(context)

        then:
        thrown(IllegalArgumentException)
    }
}
