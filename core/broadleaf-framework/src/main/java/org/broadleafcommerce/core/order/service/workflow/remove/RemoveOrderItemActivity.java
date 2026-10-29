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
package org.broadleafcommerce.core.order.service.workflow.remove;

import org.apache.commons.collections4.CollectionUtils;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.OrderItemService;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.workflow.CartOperationRequest;
import org.broadleafcommerce.core.workflow.BaseActivity;
import org.broadleafcommerce.core.workflow.ProcessContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Resource;

/**
 * This class is responsible for determining which OrderItems should be removed from the order, taking into account
 * the fact that removing an OrderItem should also remove all of its child order items.
 *
 * @author Andre Azzolini (apazzolini)
 */
@Component("blRemoveOrderItemActivity")
public class RemoveOrderItemActivity extends BaseActivity<ProcessContext<CartOperationRequest>> {

    public static final int ORDER = 4000;

    @Resource(name = "blOrderService")
    protected OrderService orderService;

    @Resource(name = "blOrderItemService")
    protected OrderItemService orderItemService;

    public RemoveOrderItemActivity() {
        setOrder(ORDER);
    }

    @Override
    public ProcessContext<CartOperationRequest> execute(ProcessContext<CartOperationRequest> context) throws Exception {
        CartOperationRequest request = context.getSeedData();

        OrderItem orderItem = request.getOrderItem();
        List<OrderItem> itemsToDelete = new ArrayList<>();
        removeItemAndChildren(itemsToDelete, orderItem);

        // children attached from another order must not be deleted through this order
        Set<Long> orderItemIdsInOrder = new HashSet<>();
        for (OrderItem oi : request.getOrder().getOrderItems()) {
            orderItemIdsInOrder.add(oi.getId());
        }
        List<OrderItem> foreignItems = new ArrayList<>();
        for (OrderItem itemToDelete : itemsToDelete) {
            if (itemToDelete == orderItem || orderItemIdsInOrder.contains(itemToDelete.getId())) {
                request.getOisToDelete().add(itemToDelete);
            } else {
                foreignItems.add(itemToDelete);
            }
        }

        // detach foreign items from a parent that is about to be deleted so the parent reference does not block deletion
        for (OrderItem foreignItem : foreignItems) {
            OrderItem parent = foreignItem.getParentOrderItem();
            if (parent != null && request.getOisToDelete().contains(parent)) {
                parent.getChildOrderItems().remove(foreignItem);
                foreignItem.setParentOrderItem(null);
                orderItemService.saveOrderItem(foreignItem);
            }
        }

        return context;
    }

    protected void removeItemAndChildren(List<OrderItem> oisToDelete, OrderItem orderItem) {
        if (CollectionUtils.isNotEmpty(orderItem.getChildOrderItems())) {
            for (OrderItem childOrderItem : orderItem.getChildOrderItems()) {
                removeItemAndChildren(oisToDelete, childOrderItem);
            }
        }

        oisToDelete.add(orderItem);
    }

}
