/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.controller.cart;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.domain.ProductImpl;
import org.broadleafcommerce.core.catalog.service.CatalogService;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderImpl;
import org.broadleafcommerce.core.order.service.OrderItemService;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.ConfigurableOrderItemRequest;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.core.web.service.UpdateCartService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies that cart operations taking a request supplied order item id only act on items in the current cart.
 */
public class BroadleafCartControllerTest {

    private static final Long CART_ID = 1L;
    private static final Long OTHER_ORDER_ID = 2L;
    private static final Long CART_ITEM_ID = 10L;
    private static final Long OTHER_ORDER_ITEM_ID = 20L;
    private static final Long UNKNOWN_ITEM_ID = 99L;
    private static final Long PRODUCT_ID = 100L;

    private BroadleafCartController controller;
    private OrderService orderService;
    private CatalogService catalogService;
    private Order cart;

    @Before
    public void setUp() {
        cart = createOrder(CART_ID);
        Order otherOrder = createOrder(OTHER_ORDER_ID);

        Product product = new ProductImpl();
        product.setId(PRODUCT_ID);

        OrderItemService orderItemService = EasyMock.createNiceMock(OrderItemService.class);
        EasyMock.expect(orderItemService.readOrderItemById(CART_ITEM_ID))
                .andReturn(createOrderItem(CART_ITEM_ID, cart, product, 3)).anyTimes();
        EasyMock.expect(orderItemService.readOrderItemById(OTHER_ORDER_ITEM_ID))
                .andReturn(createOrderItem(OTHER_ORDER_ITEM_ID, otherOrder, product, 7)).anyTimes();
        EasyMock.expect(orderItemService.createConfigurableOrderItemRequestFromProduct(product))
                .andReturn(new ConfigurableOrderItemRequest()).anyTimes();
        EasyMock.expect(orderItemService.findAllProductsInRequest(EasyMock.anyObject()))
                .andReturn(new HashSet<>()).anyTimes();
        EasyMock.replay(orderItemService);

        catalogService = EasyMock.createNiceMock(CatalogService.class);
        EasyMock.expect(catalogService.findProductById(PRODUCT_ID)).andReturn(product).anyTimes();
        EasyMock.replay(catalogService);

        orderService = EasyMock.createStrictMock(OrderService.class);
        EasyMock.replay(orderService);

        UpdateCartService updateCartService = EasyMock.createNiceMock(UpdateCartService.class);
        EasyMock.replay(updateCartService);

        controller = new BroadleafCartController();
        controller.orderItemService = orderItemService;
        controller.catalogService = catalogService;
        controller.orderService = orderService;
        controller.updateCartService = updateCartService;
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void reconfigureRendersItemInCurrentCart() throws Exception {
        setCurrentCart(cart);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.reconfigure(createRequest(null, null), null, model, CART_ITEM_ID);

        Assert.assertEquals(controller.getConfigurePageRedirect(), view);
        Assert.assertEquals(CART_ITEM_ID, model.get("originalOrderItem"));
        ConfigurableOrderItemRequest baseItem = (ConfigurableOrderItemRequest) model.get("baseItem");
        Assert.assertEquals(Integer.valueOf(3), baseItem.getQuantity());
    }

    @Test
    public void reconfigureRejectsItemFromAnotherOrder() throws Exception {
        setCurrentCart(cart);
        assertReconfigureNotFound(OTHER_ORDER_ITEM_ID);
    }

    @Test
    public void reconfigureRejectsUnknownItem() throws Exception {
        setCurrentCart(cart);
        assertReconfigureNotFound(UNKNOWN_ITEM_ID);
    }

    @Test
    public void reconfigureRejectsAnyItemWithoutCart() throws Exception {
        setCurrentCart(null);
        assertReconfigureNotFound(CART_ITEM_ID);
    }

    @Test
    public void updateRequestRejectsOriginalItemFromAnotherOrder() throws Exception {
        setCurrentCart(cart);
        HttpServletRequest request = createRequest("true", OTHER_ORDER_ITEM_ID.toString());
        try {
            controller.add(request, null, new ExtendedModelMap(), new OrderItemRequestDTO());
            Assert.fail("Expected a 404 for an order item outside the current cart");
        } catch (ResponseStatusException e) {
            Assert.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
        EasyMock.verify(orderService);
    }

    private void assertReconfigureNotFound(Long orderItemId) throws Exception {
        ExtendedModelMap model = new ExtendedModelMap();
        try {
            controller.reconfigure(createRequest(null, null), null, model, orderItemId);
            Assert.fail("Expected a 404 for an order item outside the current cart");
        } catch (ResponseStatusException e) {
            Assert.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
        Assert.assertTrue(model.isEmpty());
    }

    private void setCurrentCart(Order currentCart) {
        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute(
                CartStateRequestProcessor.getCartRequestAttributeName(), WebRequest.SCOPE_REQUEST
        )).andReturn(currentCart).anyTimes();
        EasyMock.replay(webRequest);

        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    private HttpServletRequest createRequest(String isUpdateRequest, String originalOrderItem) {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getParameter("isUpdateRequest")).andReturn(isUpdateRequest).anyTimes();
        EasyMock.expect(request.getParameter("originalOrderItem")).andReturn(originalOrderItem).anyTimes();
        EasyMock.replay(request);
        return request;
    }

    private Order createOrder(Long orderId) {
        Order order = new OrderImpl();
        order.setId(orderId);
        return order;
    }

    private DiscreteOrderItem createOrderItem(Long orderItemId, Order order, Product product, int quantity) {
        DiscreteOrderItem orderItem = new DiscreteOrderItemImpl();
        orderItem.setId(orderItemId);
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(quantity);
        order.getOrderItems().add(orderItem);
        return orderItem;
    }

}
