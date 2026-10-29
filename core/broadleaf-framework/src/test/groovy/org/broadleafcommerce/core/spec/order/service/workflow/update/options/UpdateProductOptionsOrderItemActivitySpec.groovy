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
import org.broadleafcommerce.core.order.domain.Order
import org.broadleafcommerce.core.order.domain.OrderItem
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.OrderService
import org.broadleafcommerce.core.order.service.call.DiscreteOrderItemRequest
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException
import org.broadleafcommerce.core.order.service.workflow.update.options.UpdateProductOptionsOrderItemActivity
import org.broadleafcommerce.core.spec.order.service.workflow.BaseOrderWorkflowSpec

class UpdateProductOptionsOrderItemActivitySpec extends BaseOrderWorkflowSpec {

    OrderService mockOrderService = Mock()
    OrderItemService mockOrderItemService = Mock()

    def setup() {
        activity = new UpdateProductOptionsOrderItemActivity().with {
            orderService = mockOrderService
            orderItemService = mockOrderItemService
            it
        }
    }

    def "An order item in the order gets its product options updated"() {
        setup:
        DiscreteOrderItem ownItem = new DiscreteOrderItemImpl()
        ownItem.id = 10
        context.seedData.order.orderItems.add(ownItem)
        context.seedData.itemRequest.orderItemId = 10
        context.seedData.itemRequest.itemAttributes = [engraving: 'Alice']

        when:
        context = activity.execute(context)

        then:
        1 * mockOrderItemService.updateDiscreteOrderItem(ownItem, { DiscreteOrderItemRequest r ->
            r.itemAttributes == [engraving: 'Alice']
        })
        1 * mockOrderService.save(_ as Order, false) >> { Order o, Boolean p -> o }
        0 * mockOrderItemService.readOrderItemById(_)
    }

    def "A discrete item nested in a bundle of the order can be updated"() {
        setup:
        DiscreteOrderItem nestedItem = new DiscreteOrderItemImpl()
        nestedItem.id = 11
        BundleOrderItemImpl bundle = new BundleOrderItemImpl()
        bundle.id = 12
        bundle.discreteOrderItems.add(nestedItem)
        context.seedData.order.orderItems.add(bundle)
        context.seedData.itemRequest.orderItemId = 11
        context.seedData.itemRequest.itemAttributes = [size: 'L']

        when:
        context = activity.execute(context)

        then:
        1 * mockOrderItemService.updateDiscreteOrderItem(nestedItem, _ as DiscreteOrderItemRequest)
        1 * mockOrderService.save(_ as Order, false) >> { Order o, Boolean p -> o }
    }

    def "An order item that belongs to another order is rejected and never modified"() {
        setup:
        DiscreteOrderItem ownItem = new DiscreteOrderItemImpl()
        ownItem.id = 10
        context.seedData.order.orderItems.add(ownItem)
        OrderItem foreignItem = new DiscreteOrderItemImpl()
        foreignItem.id = 99
        mockOrderItemService.readOrderItemById(99) >> foreignItem
        context.seedData.itemRequest.orderItemId = 99
        context.seedData.itemRequest.itemAttributes = [engraving: 'Mallory']

        when:
        activity.execute(context)

        then:
        thrown(ItemNotFoundException)
        0 * mockOrderItemService.updateDiscreteOrderItem(*_)
        0 * mockOrderService.save(*_)
    }
}
