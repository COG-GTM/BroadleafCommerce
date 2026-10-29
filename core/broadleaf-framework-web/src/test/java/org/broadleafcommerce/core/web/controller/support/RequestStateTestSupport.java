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
package org.broadleafcommerce.core.web.controller.support;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.web.order.security.CartStateRequestProcessor;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.easymock.EasyMock;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Field;

/**
 * Binds a customer and cart to the current thread so that {@code CustomerState} and {@code CartState}
 * resolve them the same way they do during a storefront request.
 */
public final class RequestStateTestSupport {

    private static final Field RESOLVER_CONTEXT_FIELD = ReflectionUtils.findField(
            BroadleafRequestCustomerResolverImpl.class, "applicationContext", ApplicationContext.class);

    private static boolean bound;
    private static ApplicationContext previousResolverContext;

    static {
        ReflectionUtils.makeAccessible(RESOLVER_CONTEXT_FIELD);
    }

    private RequestStateTestSupport() {
    }

    public static void bind(Customer customer, Order cart) {
        if (!bound) {
            previousResolverContext = (ApplicationContext) ReflectionUtils.getField(RESOLVER_CONTEXT_FIELD, null);
            bound = true;
        }
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton("blRequestCustomerResolver", BroadleafRequestCustomerResolverImpl.class);
        applicationContext.refresh();
        String customerAttribute = BroadleafRequestCustomerResolverImpl.getRequestCustomerResolver()
                .getCustomerRequestAttributeName();

        WebRequest webRequest = EasyMock.createNiceMock(WebRequest.class);
        EasyMock.expect(webRequest.getAttribute(customerAttribute, WebRequest.SCOPE_REQUEST))
                .andStubReturn(customer);
        EasyMock.expect(webRequest.getAttribute(CartStateRequestProcessor.getCartRequestAttributeName(), WebRequest.SCOPE_REQUEST))
                .andStubReturn(cart);
        EasyMock.replay(webRequest);

        BroadleafRequestContext context = new BroadleafRequestContext();
        context.setWebRequest(webRequest);
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    public static void clear() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
        if (bound) {
            ReflectionUtils.setField(RESOLVER_CONTEXT_FIELD, null, previousResolverContext);
            previousResolverContext = null;
            bound = false;
        }
    }

}
