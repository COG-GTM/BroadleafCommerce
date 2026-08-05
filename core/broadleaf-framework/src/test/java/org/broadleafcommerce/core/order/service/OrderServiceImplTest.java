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
package org.broadleafcommerce.core.order.service;

import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderImpl;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.exception.ItemNotFoundException;
import org.broadleafcommerce.core.order.service.exception.RemoveFromCartException;
import org.broadleafcommerce.core.order.service.type.OrderStatus;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests that removing an order item is always scoped to the order that the caller resolved from its own security
 * context, so that an item id belonging to another customer's order (for example a wishlist item) cannot be removed.
 */
public class OrderServiceImplTest extends TestCase {

    private static final Long CART_ID = 1L;
    private static final Long CUSTOMER_WISHLIST_ID = 2L;
    private static final Long VICTIM_WISHLIST_ID = 3L;

    private static final Long CART_ITEM_ID = 10L;
    private static final Long CUSTOMER_WISHLIST_ITEM_ID = 20L;
    private static final Long VICTIM_WISHLIST_ITEM_ID = 30L;

    private Map<Long, Order> ordersById;
    private List<Long> removedItemIds;
    private List<Order> operatedOnOrders;
    private TestOrderServiceImpl orderService;

    @Override
    protected void setUp() {
        Order cart = createOrder(CART_ID, OrderStatus.IN_PROCESS);
        Order customerWishlist = createOrder(CUSTOMER_WISHLIST_ID, OrderStatus.NAMED);
        Order victimWishlist = createOrder(VICTIM_WISHLIST_ID, OrderStatus.NAMED);

        ordersById = new HashMap<>();
        ordersById.put(CART_ID, cart);
        ordersById.put(CUSTOMER_WISHLIST_ID, customerWishlist);
        ordersById.put(VICTIM_WISHLIST_ID, victimWishlist);

        removedItemIds = new ArrayList<>();
        operatedOnOrders = new ArrayList<>();

        OrderItemService orderItemService = EasyMock.createNiceMock(OrderItemService.class);
        EasyMock.expect(orderItemService.readOrderItemById(CART_ITEM_ID))
                .andReturn(createOrderItem(CART_ITEM_ID, cart)).anyTimes();
        EasyMock.expect(orderItemService.readOrderItemById(CUSTOMER_WISHLIST_ITEM_ID))
                .andReturn(createOrderItem(CUSTOMER_WISHLIST_ITEM_ID, customerWishlist)).anyTimes();
        EasyMock.expect(orderItemService.readOrderItemById(VICTIM_WISHLIST_ITEM_ID))
                .andReturn(createOrderItem(VICTIM_WISHLIST_ITEM_ID, victimWishlist)).anyTimes();
        EasyMock.replay(orderItemService);

        orderService = new TestOrderServiceImpl();
        orderService.orderItemService = orderItemService;
    }

    public void testRemoveItemFromOwnCart() throws RemoveFromCartException {
        orderService.removeItem(CART_ID, CART_ITEM_ID, false);

        assertEquals(1, removedItemIds.size());
        assertEquals(CART_ITEM_ID, removedItemIds.get(0));
        assertEquals(CART_ID, operatedOnOrders.get(0).getId());
    }

    public void testRemoveItemFromOwnNamedOrder() throws RemoveFromCartException {
        orderService.removeItem(CUSTOMER_WISHLIST_ID, CUSTOMER_WISHLIST_ITEM_ID, false);

        assertEquals(1, removedItemIds.size());
        assertEquals(CUSTOMER_WISHLIST_ITEM_ID, removedItemIds.get(0));
        assertEquals(CUSTOMER_WISHLIST_ID, operatedOnOrders.get(0).getId());
    }

    public void testRemoveItemBelongingToAnotherCustomersNamedOrder() {
        try {
            orderService.removeItem(CUSTOMER_WISHLIST_ID, VICTIM_WISHLIST_ITEM_ID, false);
            fail("Expected the removal of an item owned by another order to be rejected");
        } catch (RemoveFromCartException e) {
            assertTrue(e.getCause() instanceof ItemNotFoundException);
        }

        assertTrue(removedItemIds.isEmpty());
    }

    public void testRemoveUnknownItem() {
        try {
            orderService.removeItem(CART_ID, 999L, false);
            fail("Expected the removal of an unknown item to be rejected");
        } catch (RemoveFromCartException e) {
            assertTrue(e.getCause() instanceof ItemNotFoundException);
        }

        assertTrue(removedItemIds.isEmpty());
    }

    public void testFindOrderByIdOrByOrderItemIdIgnoresForeignNamedOrder() {
        Order order = orderService.findOrderByIdOrByOrderItemId(CUSTOMER_WISHLIST_ID, VICTIM_WISHLIST_ITEM_ID);

        assertEquals(CUSTOMER_WISHLIST_ID, order.getId());
    }

    public void testFindOrderByIdOrByOrderItemIdResolvesOwnNamedOrder() {
        Order order = orderService.findOrderByIdOrByOrderItemId(CUSTOMER_WISHLIST_ID, CUSTOMER_WISHLIST_ITEM_ID);

        assertEquals(CUSTOMER_WISHLIST_ID, order.getId());
    }

    private Order createOrder(Long orderId, OrderStatus status) {
        Order order = new OrderImpl();
        order.setId(orderId);
        order.setStatus(status);
        return order;
    }

    private OrderItem createOrderItem(Long orderItemId, Order order) {
        OrderItem orderItem = new DiscreteOrderItemImpl();
        orderItem.setId(orderItemId);
        orderItem.setOrder(order);
        order.getOrderItems().add(orderItem);
        return orderItem;
    }

    private class TestOrderServiceImpl extends OrderServiceImpl {

        @Override
        public Order findOrderById(Long orderId) {
            return ordersById.get(orderId);
        }

        @Override
        public void preValidateCartOperation(Order cart) {
            // no extension handlers under test
        }

        @Override
        protected Order removeItemInternal(Long orderId, Long orderItemId, boolean priceOrder) {
            removedItemIds.add(orderItemId);
            Order order = findOrderByIdOrByOrderItemId(orderId, orderItemId);
            operatedOnOrders.add(order);
            return order;
        }
    }

}
