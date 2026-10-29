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
package org.broadleafcommerce.core.spec.order.service.workflow.remove

import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl
import org.broadleafcommerce.core.order.domain.OrderItem
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.workflow.remove.RemoveOrderItemActivity
import org.broadleafcommerce.core.spec.order.service.workflow.BaseOrderWorkflowSpec

class RemoveOrderItemActivitySpec extends BaseOrderWorkflowSpec {

    OrderItemService mockOrderItemService = Mock()

    def setup() {
        activity = new RemoveOrderItemActivity().with {
            orderItemService = mockOrderItemService
            it
        }
    }

    def "Children attached from another order are not deleted along with the parent"() {
        setup: "A parent with one child in the order and one child from another order"
        OrderItem parent = new DiscreteOrderItemImpl(id: 10)
        OrderItem ownChild = new DiscreteOrderItemImpl(id: 11)
        OrderItem foreignChild = new DiscreteOrderItemImpl(id: 12)
        parent.childOrderItems = [ownChild, foreignChild]
        ownChild.parentOrderItem = parent
        foreignChild.parentOrderItem = parent
        context.seedData.order.orderItems = [parent, ownChild]
        context.seedData.orderItem = parent

        when: "The activity is executed"
        activity.execute(context)

        then: "Only the parent and the child from this order are deleted"
        context.seedData.oisToDelete as Set == [parent, ownChild] as Set

        and: "The foreign child is detached from the deleted parent and saved"
        foreignChild.parentOrderItem == null
        !parent.childOrderItems.contains(foreignChild)
        1 * mockOrderItemService.saveOrderItem(foreignChild)
    }
}
