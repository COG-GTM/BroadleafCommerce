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

import org.broadleafcommerce.core.order.domain.DiscreteOrderItem
import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl
import org.broadleafcommerce.core.order.domain.OrderItem
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.OrderService
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException
import org.broadleafcommerce.core.order.service.workflow.update.options.UpdateProductOptionsOrderItemActivity
import org.broadleafcommerce.core.spec.order.service.workflow.BaseOrderWorkflowSpec

class UpdateProductOptionsOrderItemActivitySpec extends BaseOrderWorkflowSpec {

    OrderService mockOrderService = Mock()
    OrderItemService mockOrderItemService = Mock()

    DiscreteOrderItem ownItem

    def setup() {
        activity = Spy(UpdateProductOptionsOrderItemActivity).with {
            orderService = mockOrderService
            orderItemService = mockOrderItemService
            it
        }

        ownItem = new DiscreteOrderItemImpl(id: 10)
        context.seedData.order.orderItems = [ownItem] as List<OrderItem>
        context.seedData.itemRequest.itemAttributes = [engraving: 'Happy Birthday Mom']
    }

    def "Product options are updated on the order's own item"() {
        setup:
        context.seedData.itemRequest.orderItemId = 10

        when:
        context = activity.execute(context)

        then:
        1 * mockOrderItemService.updateDiscreteOrderItem({ it.is(ownItem) }, { it.itemAttributes == [engraving: 'Happy Birthday Mom'] })
        1 * mockOrderService.save(context.seedData.order, false) >> context.seedData.order
        0 * mockOrderItemService.readOrderItemById(_)
        context.seedData.orderItem.is(ownItem)
    }

    def "An order item from another order is never loaded, updated or saved"() {
        setup: "Mallory passes her own cart but Alice's order item id"
        DiscreteOrderItem aliceItem = new DiscreteOrderItemImpl(id: 999)
        mockOrderItemService.readOrderItemById(999L) >> aliceItem
        context.seedData.itemRequest.orderItemId = 999

        when:
        activity.execute(context)

        then:
        thrown(ItemNotFoundException)
        0 * mockOrderItemService.updateDiscreteOrderItem(*_)
        0 * mockOrderService.save(*_)
        aliceItem.orderItemAttributes.isEmpty()
    }
}
