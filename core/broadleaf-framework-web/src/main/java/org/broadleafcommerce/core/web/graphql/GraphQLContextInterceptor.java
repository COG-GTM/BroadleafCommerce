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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.util.StringUtil;
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

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

/**
 * A {@link WebGraphQlInterceptor} that establishes the Broadleaf customer and cart
 * context for GraphQL requests. The customer identity is resolved following the same
 * precedence used by {@code RestApiCustomerStateFilter}: request attribute first, then
 * request parameter, then request header. If no customer can be resolved, an anonymous
 * customer is created. Once the customer is established, the active cart is populated
 * into {@link CartState} for downstream resolvers.
 */
@Component
public class GraphQLContextInterceptor implements WebGraphQlInterceptor {

    public static final String CUSTOMER_ID_ATTRIBUTE = "customerId";
    public static final String BLC_RULE_MAP_PARAM = "blRuleMap";

    protected static final Log LOG = LogFactory.getLog(GraphQLContextInterceptor.class);

    protected final CustomerService customerService;
    protected final OrderService orderService;

    @Autowired
    public GraphQLContextInterceptor(
            @Qualifier("blCustomerService") CustomerService customerService,
            @Qualifier("blOrderService") OrderService orderService
    ) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        HttpServletRequest httpRequest = extractHttpRequest(request);
        if (httpRequest != null) {
            populateCustomerAndCart(httpRequest);
        }
        return chain.next(request);
    }

    protected HttpServletRequest extractHttpRequest(WebGraphQlRequest request) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    protected void populateCustomerAndCart(HttpServletRequest request) {
        Customer customer = resolveCustomer(request);
        if (customer == null) {
            customer = customerService.createCustomer();
            CustomerState.setCustomer(customer);
            setupCustomerForRuleProcessing(customer, request);
        }
        populateCart(customer);
    }

    protected Customer resolveCustomer(HttpServletRequest request) {
        String customerId = readCustomerId(request);

        if (customerId != null && customerId.trim().length() > 0) {
            Long parsedId = parseCustomerId(customerId);
            if (parsedId != null) {
                Customer customer = customerService.readCustomerById(parsedId);
                if (customer != null) {
                    ensureWebRequest(request);
                    CustomerState.setCustomer(customer);
                    setupCustomerForRuleProcessing(customer, request);
                    return customer;
                }
            } else {
                LOG.warn(String.format("The customer id passed in '%s' was not a valid long", StringUtil.sanitize(customerId)));
            }
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("No customer ID was found for the GraphQL request. In order to look up a customer for the request"
                    + " send a request parameter or request header for the '" + CUSTOMER_ID_ATTRIBUTE + "' attribute");
        }

        Customer existingCustomer = CustomerState.getCustomer();
        if (existingCustomer != null) {
            return existingCustomer;
        }

        ensureWebRequest(request);
        return null;
    }

    protected Long parseCustomerId(String customerId) {
        if (!NumberUtils.isDigits(customerId)) {
            return null;
        }
        try {
            return Long.valueOf(customerId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected String readCustomerId(HttpServletRequest request) {
        String customerId = null;
        if (request.getAttribute(CUSTOMER_ID_ATTRIBUTE) != null) {
            customerId = String.valueOf(request.getAttribute(CUSTOMER_ID_ATTRIBUTE));
        }
        if (customerId == null) {
            customerId = request.getParameter(CUSTOMER_ID_ATTRIBUTE);
        }
        if (customerId == null) {
            customerId = request.getHeader(CUSTOMER_ID_ATTRIBUTE);
        }
        return customerId;
    }

    protected void ensureWebRequest(HttpServletRequest request) {
        BroadleafRequestContext ctx = BroadleafRequestContext.getBroadleafRequestContext();
        if (ctx != null && ctx.getWebRequest() == null) {
            ctx.setWebRequest(new ServletWebRequest(request));
        }
    }

    protected void populateCart(Customer customer) {
        if (customer == null) {
            return;
        }
        Order cart = orderService.findCartForCustomer(customer);
        if (cart != null) {
            CartState.setCart(cart);
        }
    }

    protected void setupCustomerForRuleProcessing(Customer customer, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleMap = (Map<String, Object>) request.getAttribute(BLC_RULE_MAP_PARAM);
        if (ruleMap == null) {
            ruleMap = new HashMap<>();
        }
        ruleMap.put("customer", customer);
        request.setAttribute(BLC_RULE_MAP_PARAM, ruleMap);
    }

}
