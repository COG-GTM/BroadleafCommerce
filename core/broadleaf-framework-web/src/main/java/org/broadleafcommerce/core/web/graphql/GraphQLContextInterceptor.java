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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link WebGraphQlInterceptor} that populates Broadleaf customer context for incoming
 * GraphQL requests. It mirrors the identity resolution pattern used by
 * {@link org.broadleafcommerce.profile.web.core.security.RestApiCustomerStateFilter}:
 * look for a {@code customerId} header or query parameter, load the customer when numeric,
 * and populate {@link CustomerState}. When no customer can be resolved, an anonymous
 * customer is created so downstream resolvers always see a valid customer context.
 */
@Component("blGraphQLContextInterceptor")
public class GraphQLContextInterceptor implements WebGraphQlInterceptor {

    public static final String CUSTOMER_ID_ATTRIBUTE = "customerId";
    public static final String BLC_RULE_MAP_PARAM = "blRuleMap";

    protected static final Log LOG = LogFactory.getLog(GraphQLContextInterceptor.class);

    @Autowired
    @Qualifier("blCustomerService")
    protected CustomerService customerService;

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        try {
            populateCustomerContext(request);
        } catch (Exception ex) {
            LOG.warn("Failed to populate customer context for GraphQL request", ex);
        }
        ensureCustomerContext();
        return chain.next(request);
    }

    protected void populateCustomerContext(WebGraphQlRequest request) {
        ensureBroadleafRequestContext();

        if (CustomerState.getCustomer() != null) {
            return;
        }

        String customerId = resolveCustomerId(request);
        if (customerId == null || customerId.trim().isEmpty()) {
            return;
        }
        if (!isValidLong(customerId)) {
            LOG.warn(String.format("The customer id passed in '%s' was not a number",
                    StringUtil.sanitize(customerId)));
            return;
        }
        Customer customer = customerService.readCustomerById(Long.valueOf(customerId));
        if (customer != null) {
            CustomerState.setCustomer(customer);
            setupCustomerForRuleProcessing(customer);
        }
    }

    protected void ensureCustomerContext() {
        if (CustomerState.getCustomer() != null) {
            return;
        }
        try {
            ensureBroadleafRequestContext();
            Customer anonymousCustomer = customerService.createCustomer();
            CustomerState.setCustomer(anonymousCustomer);
            setupCustomerForRuleProcessing(anonymousCustomer);
        } catch (Exception ex) {
            LOG.warn("Failed to create anonymous customer for GraphQL request", ex);
        }
    }

    protected boolean isValidLong(String value) {
        if (value == null) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    protected String resolveCustomerId(WebGraphQlRequest request) {
        String customerId = request.getHeaders().getFirst(CUSTOMER_ID_ATTRIBUTE);
        if (customerId != null) {
            return customerId;
        }
        String query = request.getUri() != null ? request.getUri().getQuery() : null;
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && CUSTOMER_ID_ATTRIBUTE.equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    protected void ensureBroadleafRequestContext() {
        BroadleafRequestContext brc = BroadleafRequestContext.getBroadleafRequestContext();
        if (brc == null) {
            brc = new BroadleafRequestContext();
            BroadleafRequestContext.setBroadleafRequestContext(brc);
        }
        if (brc.getWebRequest() == null) {
            WebRequest webRequest = resolveWebRequest();
            if (webRequest != null) {
                brc.setWebRequest(webRequest);
            }
        }
    }

    protected WebRequest resolveWebRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return new ServletWebRequest(attrs.getRequest(), attrs.getResponse());
            }
        } catch (IllegalStateException ignored) {
            // No servlet request bound to this thread; fall through
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected void setupCustomerForRuleProcessing(Customer customer) {
        BroadleafRequestContext brc = BroadleafRequestContext.getBroadleafRequestContext();
        if (brc == null) {
            return;
        }
        if (brc.getRequest() != null) {
            Map<String, Object> ruleMap = (Map<String, Object>) brc.getRequest()
                    .getAttribute(BLC_RULE_MAP_PARAM);
            if (ruleMap == null) {
                ruleMap = new HashMap<>();
            }
            ruleMap.put("customer", customer);
            brc.getRequest().setAttribute(BLC_RULE_MAP_PARAM, ruleMap);
            return;
        }
        Map<String, Object> additional = brc.getAdditionalProperties();
        if (additional == null) {
            additional = new HashMap<>();
            brc.setAdditionalProperties(additional);
        }
        Map<String, Object> ruleMap = (Map<String, Object>) additional.get(BLC_RULE_MAP_PARAM);
        if (ruleMap == null) {
            ruleMap = new HashMap<>();
            additional.put(BLC_RULE_MAP_PARAM, ruleMap);
        }
        ruleMap.put("customer", customer);
    }
}
