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

import org.broadleafcommerce.core.order.dao.OrderDao;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderServiceImplRemoveItemTest {

    protected RecordingOrderService orderService;
    protected Map<Long, Order> orders = new HashMap<>();
    protected Map<Long, OrderItem> orderItems = new HashMap<>();

    protected Customer alice;
    protected Customer mallory;
    protected Order aliceCart;
    protected Order aliceWishlist;
    protected Order aliceOtherWishlist;
    protected Order malloryCart;
    protected OrderItem aliceCartItem;
    protected OrderItem aliceWishlistItem;
    protected OrderItem aliceOtherWishlistItem;
    protected OrderItem malloryCartItem;

    @Before
    public void setUp() {
        alice = customer(1L);
        mallory = customer(2L);

        aliceCart = order(10L, alice, OrderStatus.IN_PROCESS);
        aliceWishlist = order(11L, alice, OrderStatus.NAMED);
        aliceOtherWishlist = order(12L, alice, OrderStatus.NAMED);
        malloryCart = order(20L, mallory, OrderStatus.IN_PROCESS);

        aliceCartItem = item(100L, aliceCart);
        aliceWishlistItem = item(110L, aliceWishlist);
        aliceOtherWishlistItem = item(120L, aliceOtherWishlist);
        malloryCartItem = item(200L, malloryCart);

        OrderDao orderDao = EasyMock.createMock(OrderDao.class);
        EasyMock.expect(orderDao.readOrderById(EasyMock.anyLong()))
                .andAnswer(() -> orders.get((Long) EasyMock.getCurrentArguments()[0])).anyTimes();
        OrderItemService orderItemService = EasyMock.createMock(OrderItemService.class);
        EasyMock.expect(orderItemService.readOrderItemById(EasyMock.anyLong()))
                .andAnswer(() -> orderItems.get((Long) EasyMock.getCurrentArguments()[0])).anyTimes();
        EasyMock.replay(orderDao, orderItemService);

        orderService = new RecordingOrderService();
        orderService.orderDao = orderDao;
        orderService.orderItemService = orderItemService;
    }

    @Test
    public void testCannotRemoveItemFromAnotherCustomersWishlist() {
        assertItemNotFound(malloryCart.getId(), aliceWishlistItem.getId());
        Assert.assertTrue(orderService.resolvedOrders.isEmpty());
        Assert.assertTrue(aliceWishlist.getOrderItems().contains(aliceWishlistItem));
    }

    @Test
    public void testCannotRemoveItemFromAnotherCustomersCart() {
        assertItemNotFound(malloryCart.getId(), aliceCartItem.getId());
        Assert.assertTrue(orderService.resolvedOrders.isEmpty());
    }

    @Test
    public void testCannotRemoveItemFromAnotherWishlistUsingWishlistId() {
        assertItemNotFound(aliceWishlist.getId(), aliceOtherWishlistItem.getId());
        Assert.assertTrue(orderService.resolvedOrders.isEmpty());
    }

    @Test
    public void testUnknownItemIsNotFound() {
        assertItemNotFound(aliceCart.getId(), 999L);
    }

    @Test
    public void testRemovesItemFromOwnCart() throws RemoveFromCartException {
        orderService.removeItem(aliceCart.getId(), aliceCartItem.getId(), false);
        Assert.assertEquals(1, orderService.resolvedOrders.size());
        Assert.assertSame(aliceCart, orderService.resolvedOrders.get(0));
    }

    @Test
    public void testRemovesItemFromOwnWishlist() throws RemoveFromCartException {
        orderService.removeItem(aliceWishlist.getId(), aliceWishlistItem.getId(), false);
        Assert.assertSame(aliceWishlist, orderService.resolvedOrders.get(0));
    }

    @Test
    public void testCartCanRemoveItemFromSameCustomersWishlist() throws RemoveFromCartException {
        orderService.removeItem(aliceCart.getId(), aliceWishlistItem.getId(), false);
        Assert.assertSame(aliceWishlist, orderService.resolvedOrders.get(0));
    }

    @Test
    public void testResolvedOrderStaysOnRequestedOrderForAnotherCustomersWishlistItem() {
        Assert.assertSame(malloryCart,
                orderService.findOrderByIdOrByOrderItemId(malloryCart.getId(), aliceWishlistItem.getId()));
        Assert.assertSame(aliceWishlist,
                orderService.findOrderByIdOrByOrderItemId(aliceWishlist.getId(), aliceOtherWishlistItem.getId()));
        Assert.assertSame(malloryCart,
                orderService.findOrderByIdOrByOrderItemId(malloryCart.getId(), malloryCartItem.getId()));
    }

    @Test
    public void testAnonymousCartCannotReachWishlistOfCustomerWithoutId() {
        Order anonymousCart = order(30L, new CustomerImpl(), OrderStatus.IN_PROCESS);
        Order orphanWishlist = order(31L, new CustomerImpl(), OrderStatus.NAMED);
        OrderItem orphanItem = item(310L, orphanWishlist);
        assertItemNotFound(anonymousCart.getId(), orphanItem.getId());
    }

    protected void assertItemNotFound(Long orderId, Long orderItemId) {
        try {
            orderService.removeItem(orderId, orderItemId, false);
            Assert.fail("Expected RemoveFromCartException");
        } catch (RemoveFromCartException e) {
            Assert.assertTrue(e.getCause() instanceof ItemNotFoundException);
        }
    }

    protected Customer customer(Long id) {
        Customer customer = new CustomerImpl();
        customer.setId(id);
        return customer;
    }

    protected Order order(Long id, Customer customer, OrderStatus status) {
        Order order = new OrderImpl();
        order.setId(id);
        order.setCustomer(customer);
        order.setStatus(status);
        orders.put(id, order);
        return order;
    }

    protected OrderItem item(Long id, Order order) {
        OrderItem item = new DiscreteOrderItemImpl();
        item.setId(id);
        item.setOrder(order);
        order.getOrderItems().add(item);
        orderItems.put(id, item);
        return item;
    }

    protected static class RecordingOrderService extends OrderServiceImpl {

        protected List<Order> resolvedOrders = new ArrayList<>();

        @Override
        public void preValidateCartOperation(Order cart) {
        }

        @Override
        protected Order removeItemInternal(Long orderId, Long orderItemId, boolean priceOrder) {
            Order order = findOrderByIdOrByOrderItemId(orderId, orderItemId);
            resolvedOrders.add(order);
            return order;
        }
    }
}
