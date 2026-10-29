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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.broadleafcommerce.common.crossapp.service.CrossAppAuthService;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.AddToCartItem;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.core.web.service.UpdateCartService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.ServletWebRequest;

import java.math.BigDecimal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Maps {@link BroadleafCartController#addWithPriceOverride} in a subclass, the way a site would,
 * and verifies that only CSRs can add items with client-supplied override prices.
 */
public class BroadleafCartControllerPriceOverrideTest {

    private static final String URL = "/cart/addWithPriceOverride";

    private SitePriceOverrideCartController controller;
    private OrderService orderService;
    private UpdateCartService updateCartService;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        orderService = EasyMock.createStrictMock(OrderService.class);
        updateCartService = EasyMock.createStrictMock(UpdateCartService.class);
        controller = new SitePriceOverrideCartController();
        controller.orderService = orderService;
        controller.updateCartService = updateCartService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void shopperIsForbiddenWhenNoCrossAppAuthServiceIsConfigured() throws Exception {
        replay(orderService, updateCartService);

        mockMvc.perform(overridePost()).andExpect(status().isForbidden());

        verify(orderService, updateCartService);
    }

    @Test
    public void shopperWithoutCsrPermissionIsForbidden() throws Exception {
        controller.crossAppAuthService = crossAppAuthService(false);
        replay(orderService, updateCartService);

        mockMvc.perform(overridePost()).andExpect(status().isForbidden());

        verify(orderService, updateCartService, controller.crossAppAuthService);
    }

    @Test
    public void csrCanAddWithOverridePrices() throws Exception {
        controller.crossAppAuthService = crossAppAuthService(true);

        Order cart = EasyMock.createNiceMock(Order.class);
        expect(cart.getId()).andReturn(42L).anyTimes();
        replay(cart);
        bindCart(cart);

        Capture<OrderItemRequestDTO> itemRequest = Capture.newInstance();
        updateCartService.validateAddToCartRequest(anyObject(OrderItemRequestDTO.class), eq(cart));
        expectLastCall();
        expect(orderService.addItemWithPriceOverrides(eq(42L), capture(itemRequest), eq(false))).andReturn(cart);
        expect(orderService.save(cart, true)).andReturn(cart);
        replay(orderService, updateCartService);

        mockMvc.perform(overridePost())
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/cart"));

        verify(orderService, updateCartService, controller.crossAppAuthService);
        assertEquals(new BigDecimal("0.01"), itemRequest.getValue().getOverrideSalePrice().getAmount());
        assertEquals(new BigDecimal("0.01"), itemRequest.getValue().getOverrideRetailPrice().getAmount());
    }

    @Test(expected = PriceOverrideNotAllowedException.class)
    public void deprecatedAddToCartItemOverloadIsAlsoGuarded() throws Exception {
        replay(orderService, updateCartService);

        controller.addWithPriceOverride(new MockHttpServletRequest(), null, null, new AddToCartItem());
    }

    private CrossAppAuthService crossAppAuthService(boolean hasCsrPermission) {
        CrossAppAuthService service = EasyMock.createMock(CrossAppAuthService.class);
        expect(service.hasCsrPermission()).andReturn(hasCsrPermission);
        replay(service);
        return service;
    }

    private void bindCart(Order cart) {
        MockHttpServletRequest cartRequest = new MockHttpServletRequest();
        cartRequest.setAttribute(CartStateRequestProcessor.getCartRequestAttributeName(), cart);
        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(new ServletWebRequest(cartRequest));
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    private static MockHttpServletRequestBuilder overridePost() {
        return post(URL)
                .param("skuId", "1")
                .param("quantity", "1")
                .param("overrideSalePrice", "0.01")
                .param("overrideRetailPrice", "0.01");
    }

    @Controller
    static class SitePriceOverrideCartController extends BroadleafCartController {

        @PostMapping(URL)
        public String addWithOverrides(HttpServletRequest request, HttpServletResponse response, Model model,
                @ModelAttribute("addToCartItem") OrderItemRequestDTO itemRequest) throws Exception {
            return addWithPriceOverride(request, response, model, itemRequest);
        }
    }

}
