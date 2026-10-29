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

import org.broadleafcommerce.common.crossapp.service.CrossAppAuthService;
import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderImpl;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.AddToCartItem;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.core.web.service.UpdateCartService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies that {@link BroadleafCartController#addWithPriceOverride} only honors override prices for users
 * permitted to override them.
 */
public class BroadleafCartControllerPriceOverrideTest {

    private static final Long CART_ID = 1L;

    private BroadleafCartController controller;
    private OrderService orderService;
    private UpdateCartService updateCartService;
    private Order cart;

    @Before
    public void setUp() {
        cart = new OrderImpl();
        cart.setId(CART_ID);

        orderService = EasyMock.createStrictMock(OrderService.class);
        updateCartService = EasyMock.createStrictMock(UpdateCartService.class);

        controller = new BroadleafCartController();
        controller.orderService = orderService;
        controller.updateCartService = updateCartService;

        setCurrentCart(cart);
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void rejectsOverridesWhenNoCrossAppAuthServiceIsConfigured() throws Exception {
        assertRejected(controller, overrideRequest());
    }

    @Test
    public void rejectsOverridesForUsersWithoutCsrPermission() throws Exception {
        controller.crossAppAuthService = crossAppAuthService(false);

        assertRejected(controller, overrideRequest());
    }

    @Test
    public void deprecatedOverloadRejectsOverridesForUsersWithoutCsrPermission() throws Exception {
        controller.crossAppAuthService = crossAppAuthService(false);
        AddToCartItem itemRequest = new AddToCartItem();
        itemRequest.setOverrideSalePrice(new Money("0.01"));

        EasyMock.replay(orderService, updateCartService);
        try {
            controller.addWithPriceOverride(createRequest(), null, null, itemRequest);
            Assert.fail("Expected PriceOverrideNotAllowedException");
        } catch (PriceOverrideNotAllowedException e) {
            // expected
        }
        EasyMock.verify(orderService, updateCartService);
    }

    @Test
    public void honorsOverridesForCsrUsers() throws Exception {
        controller.crossAppAuthService = crossAppAuthService(true);
        OrderItemRequestDTO itemRequest = overrideRequest();

        updateCartService.validateAddToCartRequest(itemRequest, cart);
        EasyMock.expectLastCall();
        EasyMock.expect(orderService.addItemWithPriceOverrides(CART_ID, itemRequest, false)).andReturn(cart);
        EasyMock.expect(orderService.save(cart, true)).andReturn(cart);
        EasyMock.replay(orderService, updateCartService);

        String view = controller.addWithPriceOverride(createRequest(), null, null, itemRequest);

        Assert.assertEquals(controller.getCartPageRedirect(), view);
        EasyMock.verify(orderService, updateCartService);
    }

    @Test
    public void subclassesCanGrantOverridesWithTheirOwnPolicy() throws Exception {
        BroadleafCartController customController = new BroadleafCartController() {
            @Override
            protected boolean isPriceOverrideAllowed(HttpServletRequest request) {
                return true;
            }
        };
        customController.orderService = orderService;
        customController.updateCartService = updateCartService;
        OrderItemRequestDTO itemRequest = overrideRequest();

        updateCartService.validateAddToCartRequest(itemRequest, cart);
        EasyMock.expectLastCall();
        EasyMock.expect(orderService.addItemWithPriceOverrides(CART_ID, itemRequest, false)).andReturn(cart);
        EasyMock.expect(orderService.save(cart, true)).andReturn(cart);
        EasyMock.replay(orderService, updateCartService);

        customController.addWithPriceOverride(createRequest(), null, null, itemRequest);

        EasyMock.verify(orderService, updateCartService);
    }

    private void assertRejected(BroadleafCartController target, OrderItemRequestDTO itemRequest) throws Exception {
        EasyMock.replay(orderService, updateCartService);
        try {
            target.addWithPriceOverride(createRequest(), null, null, itemRequest);
            Assert.fail("Expected PriceOverrideNotAllowedException");
        } catch (PriceOverrideNotAllowedException e) {
            // expected
        }
        EasyMock.verify(orderService, updateCartService);
    }

    private OrderItemRequestDTO overrideRequest() {
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO();
        itemRequest.setSkuId(10L);
        itemRequest.setQuantity(1);
        itemRequest.setOverrideSalePrice(new Money("0.01"));
        itemRequest.setOverrideRetailPrice(new Money("0.01"));
        return itemRequest;
    }

    private CrossAppAuthService crossAppAuthService(boolean hasCsrPermission) {
        CrossAppAuthService service = EasyMock.createNiceMock(CrossAppAuthService.class);
        EasyMock.expect(service.hasCsrPermission()).andReturn(hasCsrPermission).anyTimes();
        EasyMock.replay(service);
        return service;
    }

    private HttpServletRequest createRequest() {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.replay(request);
        return request;
    }

    private void setCurrentCart(Order order) {
        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute(CartStateRequestProcessor.getCartRequestAttributeName(),
                WebRequest.SCOPE_REQUEST)).andReturn(order).anyTimes();
        EasyMock.replay(webRequest);

        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

}
