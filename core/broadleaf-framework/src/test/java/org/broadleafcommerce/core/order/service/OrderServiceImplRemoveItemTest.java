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
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

public class OrderServiceImplRemoveItemTest extends TestCase {

    private static final Long ALICE_CART_ID = 1L;
    private static final Long ALICE_WISHLIST_ID = 2L;
    private static final Long MALLORY_CART_ID = 3L;

    private static final Long ALICE_CART_ITEM_ID = 10L;
    private static final Long ALICE_WISHLIST_ITEM_ID = 20L;
    private static final Long MALLORY_CART_ITEM_ID = 30L;

    private Map<Long, Order> ordersById;
    private List<Long> removedItemIds;
    private List<Order> removedFromOrders;
    private Order aliceWishlist;
    private RecordingOrderServiceImpl orderService;

    @Override
    protected void setUp() {
        Customer alice = createCustomer(100L);
        Customer mallory = createCustomer(200L);

        Order aliceCart = createOrder(ALICE_CART_ID, OrderStatus.IN_PROCESS, alice);
        aliceWishlist = createOrder(ALICE_WISHLIST_ID, OrderStatus.NAMED, alice);
        Order malloryCart = createOrder(MALLORY_CART_ID, OrderStatus.IN_PROCESS, mallory);

        ordersById = new HashMap<>();
        ordersById.put(ALICE_CART_ID, aliceCart);
        ordersById.put(ALICE_WISHLIST_ID, aliceWishlist);
        ordersById.put(MALLORY_CART_ID, malloryCart);

        OrderItemService orderItemService = EasyMock.createNiceMock(OrderItemService.class);
        EasyMock.expect(orderItemService.readOrderItemById(ALICE_CART_ITEM_ID))
                .andReturn(createOrderItem(ALICE_CART_ITEM_ID, aliceCart)).anyTimes();
        EasyMock.expect(orderItemService.readOrderItemById(ALICE_WISHLIST_ITEM_ID))
                .andReturn(createOrderItem(ALICE_WISHLIST_ITEM_ID, aliceWishlist)).anyTimes();
        EasyMock.expect(orderItemService.readOrderItemById(MALLORY_CART_ITEM_ID))
                .andReturn(createOrderItem(MALLORY_CART_ITEM_ID, malloryCart)).anyTimes();
        EasyMock.replay(orderItemService);

        removedItemIds = new ArrayList<>();
        removedFromOrders = new ArrayList<>();
        orderService = new RecordingOrderServiceImpl();
        orderService.orderItemService = orderItemService;
    }

    public void testRemoveItemFromAnotherCustomersWishlistIsRejected() {
        assertRemoveRejected(MALLORY_CART_ID, ALICE_WISHLIST_ITEM_ID);
        assertEquals(1, aliceWishlist.getOrderItems().size());
    }

    public void testUpdateQuantityToZeroOnAnotherCustomersWishlistItemIsRejected() throws Exception {
        OrderItemRequestDTO request = new OrderItemRequestDTO();
        request.setOrderItemId(ALICE_WISHLIST_ITEM_ID);
        request.setQuantity(0);
        try {
            orderService.updateItemQuantity(MALLORY_CART_ID, request, false);
            fail("Expected removal of another customer's wishlist item to be rejected");
        } catch (RemoveFromCartException e) {
            assertTrue(e.getCause() instanceof ItemNotFoundException);
        }
        assertTrue(removedItemIds.isEmpty());
    }

    public void testRemoveItemFromAnotherCustomersCartIsRejected() {
        assertRemoveRejected(MALLORY_CART_ID, ALICE_CART_ITEM_ID);
    }

    public void testRemoveUnknownItemIsRejected() {
        assertRemoveRejected(MALLORY_CART_ID, 999L);
    }

    public void testRemoveItemFromOwnCart() throws RemoveFromCartException {
        orderService.removeItem(MALLORY_CART_ID, MALLORY_CART_ITEM_ID, false);
        assertRemovedFrom(MALLORY_CART_ITEM_ID, MALLORY_CART_ID);
    }

    public void testRemoveItemFromOwnWishlist() throws RemoveFromCartException {
        orderService.removeItem(ALICE_WISHLIST_ID, ALICE_WISHLIST_ITEM_ID, false);
        assertRemovedFrom(ALICE_WISHLIST_ITEM_ID, ALICE_WISHLIST_ID);
    }

    public void testRemoveOwnWishlistItemViaOwnCartResolvesWishlist() throws RemoveFromCartException {
        orderService.removeItem(ALICE_CART_ID, ALICE_WISHLIST_ITEM_ID, false);
        assertRemovedFrom(ALICE_WISHLIST_ITEM_ID, ALICE_WISHLIST_ID);
    }

    public void testFindOrderByIdOrByOrderItemIdIgnoresAnotherCustomersWishlist() {
        assertEquals(MALLORY_CART_ID,
                orderService.findOrderByIdOrByOrderItemId(MALLORY_CART_ID, ALICE_WISHLIST_ITEM_ID).getId());
    }

    public void testFindOrderByIdOrByOrderItemIdIgnoresWishlistWhenCustomersUnknown() {
        ordersById.get(MALLORY_CART_ID).setCustomer(null);
        aliceWishlist.setCustomer(null);
        assertEquals(MALLORY_CART_ID,
                orderService.findOrderByIdOrByOrderItemId(MALLORY_CART_ID, ALICE_WISHLIST_ITEM_ID).getId());
    }

    private void assertRemoveRejected(Long orderId, Long orderItemId) {
        try {
            orderService.removeItem(orderId, orderItemId, false);
            fail("Expected removal of item " + orderItemId + " via order " + orderId + " to be rejected");
        } catch (RemoveFromCartException e) {
            assertTrue(e.getCause() instanceof ItemNotFoundException);
        }
        assertTrue(removedItemIds.isEmpty());
    }

    private void assertRemovedFrom(Long orderItemId, Long orderId) {
        assertEquals(1, removedItemIds.size());
        assertEquals(orderItemId, removedItemIds.get(0));
        assertEquals(orderId, removedFromOrders.get(0).getId());
    }

    private Customer createCustomer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    private Order createOrder(Long id, OrderStatus status, Customer customer) {
        Order order = new OrderImpl();
        order.setId(id);
        order.setStatus(status);
        order.setCustomer(customer);
        return order;
    }

    private OrderItem createOrderItem(Long id, Order order) {
        OrderItem orderItem = new DiscreteOrderItemImpl();
        orderItem.setId(id);
        orderItem.setOrder(order);
        order.getOrderItems().add(orderItem);
        return orderItem;
    }

    private class RecordingOrderServiceImpl extends OrderServiceImpl {

        @Override
        public Order findOrderById(Long orderId) {
            return ordersById.get(orderId);
        }

        @Override
        public void preValidateCartOperation(Order cart) {
        }

        @Override
        public void preValidateUpdateQuantityOperation(Order cart, OrderItemRequestDTO dto) {
        }

        @Override
        protected Order removeItemInternal(Long orderId, Long orderItemId, boolean priceOrder) {
            Order order = findOrderByIdOrByOrderItemId(orderId, orderItemId);
            removedItemIds.add(orderItemId);
            removedFromOrders.add(order);
            return order;
        }
    }

}
