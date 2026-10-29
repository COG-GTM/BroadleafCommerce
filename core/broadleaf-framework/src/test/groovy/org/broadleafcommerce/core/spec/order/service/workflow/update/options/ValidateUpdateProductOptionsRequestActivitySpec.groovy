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

import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl
import org.broadleafcommerce.core.order.domain.OrderItem
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException
import org.broadleafcommerce.core.order.service.workflow.update.options.ValidateUpdateProductOptionsRequestActivity
import org.broadleafcommerce.core.spec.order.service.workflow.BaseOrderWorkflowSpec

class ValidateUpdateProductOptionsRequestActivitySpec extends BaseOrderWorkflowSpec {

    OrderItemService mockOrderItemService = Mock()

    def setup() {
        activity = new ValidateUpdateProductOptionsRequestActivity().with {
            orderItemService = mockOrderItemService
            it
        }
    }

    def "A request for an order item in the order passes validation"() {
        setup:
        OrderItem ownItem = new DiscreteOrderItemImpl()
        ownItem.id = 10
        context.seedData.order.orderItems.add(ownItem)
        context.seedData.itemRequest.orderItemId = 10

        when:
        context = activity.execute(context)

        then:
        notThrown(Exception)
    }

    def "A request for an order item of another order fails validation"() {
        setup:
        OrderItem ownItem = new DiscreteOrderItemImpl()
        ownItem.id = 10
        context.seedData.order.orderItems.add(ownItem)
        mockOrderItemService.readOrderItemById(99) >> new DiscreteOrderItemImpl(id: 99)
        context.seedData.itemRequest.orderItemId = 99

        when:
        activity.execute(context)

        then:
        thrown(ItemNotFoundException)
    }

    def "A request without an order item id fails validation"() {
        setup:
        context.seedData.itemRequest.orderItemId = null

        when:
        activity.execute(context)

        then:
        thrown(IllegalArgumentException)
    }
}
