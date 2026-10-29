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
package org.broadleafcommerce.core.order.service.workflow.update.options;

import org.broadleafcommerce.core.order.domain.BundleOrderItem;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException;

import java.util.Collection;
import java.util.Objects;

/**
 * Resolves the order item targeted by an update-product-options request from the items of the
 * request's {@link Order} (including bundle and child items), so that an item belonging to a
 * different order can never be modified.
 */
public final class ProductOptionsOrderItemLocator {

    private ProductOptionsOrderItemLocator() {
    }

    public static OrderItem findOrderItemInOrder(Order order, Long orderItemId) throws ItemNotFoundException {
        OrderItem orderItem = null;
        if (order != null && orderItemId != null) {
            orderItem = find(order.getOrderItems(), orderItemId);
        }
        if (orderItem == null) {
            throw new ItemNotFoundException("Order Item (" + orderItemId + ") not found in Order ("
                    + (order == null ? null : order.getId()) + ")");
        }
        return orderItem;
    }

    private static OrderItem find(Collection<? extends OrderItem> items, Long orderItemId) {
        if (items == null) {
            return null;
        }
        for (OrderItem item : items) {
            if (item == null) {
                continue;
            }
            if (Objects.equals(item.getId(), orderItemId)) {
                return item;
            }
            OrderItem found = null;
            if (item instanceof BundleOrderItem) {
                found = find(((BundleOrderItem) item).getDiscreteOrderItems(), orderItemId);
            }
            if (found == null) {
                found = find(item.getChildOrderItems(), orderItemId);
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
