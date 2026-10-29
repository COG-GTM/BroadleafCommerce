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
package org.broadleafcommerce.core.registration;

import org.broadleafcommerce.common.extension.ExtensionResultStatusType;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.extension.PostUpdateOrderExtensionHandler;
import org.broadleafcommerce.core.order.extension.PostUpdateOrderExtensionManager;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MergeOrdersByEmailPostRegistrationObserverTest {

    private static final String EMAIL = "alice@example.com";

    private MergeOrdersByEmailPostRegistrationObserver observer;
    private OrderService orderService;
    private PostUpdateOrderExtensionHandler handler;
    private Capture<List<Order>> mergedOrders;
    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        observer = new MergeOrdersByEmailPostRegistrationObserver();
        orderService = EasyMock.createMock(OrderService.class);
        handler = EasyMock.createMock(PostUpdateOrderExtensionHandler.class);
        mergedOrders = Capture.newInstance();
        EasyMock.expect(handler.postUpdateAll(EasyMock.capture(mergedOrders)))
                .andReturn(ExtensionResultStatusType.NOT_HANDLED);

        PostUpdateOrderExtensionManager extensionManager = new PostUpdateOrderExtensionManager();
        extensionManager.setHandlers(new ArrayList<>(Collections.singletonList(handler)));
        ReflectionTestUtils.setField(observer, "orderService", orderService);
        ReflectionTestUtils.setField(observer, "extensionManager", extensionManager);

        request = new MockHttpServletRequest();
        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(new ServletWebRequest(request));
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void doesNotClaimAnotherShoppersGuestOrdersByEmail() {
        Customer registering = customer(100L, true);
        Customer victimGuest = customer(7L, false);
        Order victimOrder = order(victimGuest);
        expectOrders(victimOrder);

        observer.processRegistrationEvent(registering);

        EasyMock.verify(orderService, handler, victimOrder);
        Assert.assertTrue(mergedOrders.getValue().isEmpty());
    }

    @Test
    public void mergesGuestOrdersOfSessionAnonymousCustomerById() {
        request.getSession().setAttribute(
                MergeOrdersByEmailPostRegistrationObserver.ANONYMOUS_CUSTOMER_ID_SESSION_ATTRIBUTE_NAME, 7L);
        Customer registering = customer(100L, true);
        Order sessionOrder = order(customer(7L, false));
        sessionOrder.setCustomer(registering);
        Order otherOrder = order(customer(8L, false));
        expectOrders(sessionOrder, otherOrder);

        observer.processRegistrationEvent(registering);

        EasyMock.verify(orderService, handler, sessionOrder, otherOrder);
        Assert.assertEquals(Collections.singletonList(sessionOrder), mergedOrders.getValue());
    }

    @Test
    public void mergesGuestOrdersOfSessionAnonymousCustomerObject() {
        Customer anonymous = customer(7L, false);
        request.getSession().setAttribute(
                MergeOrdersByEmailPostRegistrationObserver.ANONYMOUS_CUSTOMER_SESSION_ATTRIBUTE_NAME, anonymous);
        Customer registering = customer(100L, true);
        Order sessionOrder = order(customer(7L, false));
        sessionOrder.setCustomer(registering);
        expectOrders(sessionOrder);

        observer.processRegistrationEvent(registering);

        EasyMock.verify(orderService, handler, sessionOrder);
        Assert.assertEquals(Collections.singletonList(sessionOrder), mergedOrders.getValue());
    }

    @Test
    public void neverReassignsRegisteredCustomersOrders() {
        request.getSession().setAttribute(
                MergeOrdersByEmailPostRegistrationObserver.ANONYMOUS_CUSTOMER_ID_SESSION_ATTRIBUTE_NAME, 7L);
        Customer registering = customer(100L, true);
        Order registeredOrder = order(customer(7L, true));
        expectOrders(registeredOrder);

        observer.processRegistrationEvent(registering);

        EasyMock.verify(orderService, handler, registeredOrder);
        Assert.assertTrue(mergedOrders.getValue().isEmpty());
    }

    @Test
    public void mergesNothingWithoutRequestContext() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
        Customer registering = customer(100L, true);
        Order victimOrder = order(customer(7L, false));
        expectOrders(victimOrder);

        observer.processRegistrationEvent(registering);

        EasyMock.verify(orderService, handler, victimOrder);
        Assert.assertTrue(mergedOrders.getValue().isEmpty());
    }

    private void expectOrders(Order... orders) {
        EasyMock.expect(orderService.findOrdersByEmail(EMAIL)).andReturn(Arrays.asList(orders));
        EasyMock.replay(orderService, handler);
        EasyMock.replay((Object[]) orders);
    }

    private Customer customer(Long id, boolean registered) {
        Customer customer = EasyMock.createNiceMock(Customer.class);
        EasyMock.expect(customer.getId()).andReturn(id).anyTimes();
        EasyMock.expect(customer.isRegistered()).andReturn(registered).anyTimes();
        EasyMock.expect(customer.getEmailAddress()).andReturn(EMAIL).anyTimes();
        EasyMock.replay(customer);
        return customer;
    }

    private Order order(Customer owner) {
        Order order = EasyMock.createMock(Order.class);
        EasyMock.expect(order.getCustomer()).andReturn(owner).anyTimes();
        return order;
    }

}
