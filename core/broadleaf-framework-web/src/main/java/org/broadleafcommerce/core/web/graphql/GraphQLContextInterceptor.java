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
package org.broadleafcommerce.core.web.graphql;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.web.order.CartState;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Mono;

/**
 * Populates the Broadleaf customer and cart context for GraphQL requests. This mirrors the identity
 * resolution pattern used by {@code RestApiCustomerStateFilter} so that query/mutation resolvers
 * can rely on {@link CustomerState} and {@link CartState}.
 */
@Component
public class GraphQLContextInterceptor implements WebGraphQlInterceptor {

    @Autowired
    @Qualifier("blCustomerService")
    protected CustomerService customerService;

    @Autowired
    @Qualifier("blOrderService")
    protected OrderService orderService;

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest httpRequest = servletAttributes.getRequest();
            HttpServletResponse httpResponse = servletAttributes.getResponse();
            populateContext(httpRequest, httpResponse);
        }
        return chain.next(request);
    }

    protected void populateContext(HttpServletRequest request, HttpServletResponse response) {
        BroadleafRequestContext brc = BroadleafRequestContext.getBroadleafRequestContext();
        if (brc != null && brc.getWebRequest() == null) {
            brc.setWebRequest(new ServletWebRequest(request, response));
        }

        Customer customer = CustomerState.getCustomer();
        if (customer == null) {
            customer = customerService.createCustomer();
            CustomerState.setCustomer(customer);
        }

        if (CartState.getCart() == null && customer != null && customer.getId() != null) {
            Order cart = orderService.findCartForCustomer(customer);
            if (cart != null) {
                CartState.setCart(cart);
            }
        }
    }

}
